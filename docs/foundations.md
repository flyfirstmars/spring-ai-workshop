# LLM Foundations

This guide distills the core mechanics behind working with Large Language Model APIs across OpenAI and Azure OpenAI
deployments. Use it as a reference for how to pass information into a model, what content can be supplied, and which
controls shape behavior, cost, and reliability. Getting these fundamentals right routinely cuts token spend by 80%+,
improves task accuracy by ~40%, and unlocks workflows that simple prompt-response bots cannot handle.

LLMs behave unlike deterministic APIs: each response is sampled token-by-token from probability distributions, the
services are stateless between calls, and every byte you send or receive is billed in tokens. As a result, poor prompt
hygiene, unchecked context growth, or sloppy memory strategies surface as failures: context overflows, incoherent
replies, and unexpected cost spikes. Treat tokens, prompts, and context as first-class resources from the outset;
modern "context engineering" is really continuous memory management for probabilistic systems.

## Core API Interfaces

The common interaction model is an HTTPS `POST` with JSON payload and bearer authentication. Two request styles
dominate:

Chat Completions (OpenAI `/v1/chat/completions`, Azure `/openai/deployments/{deployment}/chat/completions`): you
provide a `messages[]` array with alternating roles (`system`, `user`, `assistant`, `tool`). Works with GPT-4o, GPT-5
variants, multimodal deployments, and tool calling. For reasoning models, use `max_completion_tokens` instead of
`max_tokens`.

Responses API (Azure preview for GPT-5 series, OpenAI for unified reasoning workflows): separates `instructions`
from `input`, adds reasoning controls, and references prior turns by `previous_response_id` rather than resending full
history. Uses `max_output_tokens` for all models.

```jsonc
// Chat Completions pattern
{
  "model": "gpt-4o",
  "messages": [
    {"role": "system", "content": "You are a clear technical explainer."},
    {"role": "user", "content": "What is an API?"}
  ],
  "temperature": 0.2,
  "max_tokens": 500
}
```

```jsonc
// Responses API pattern (Azure GPT-5)
{
  "instructions": "You are a helpful assistant that explains technical concepts clearly.",
  "input": "What is an API?",
  "max_output_tokens": 500,
  "reasoning": {"effort": "medium"},
  "text": {"verbosity": "medium"}
}
```

You can ask the service to retain Responses API outputs by setting `"store": true`; the API returns a persistent
`response_id` that you can pass later via `previous_response_id` to achieve stateful follow-ups without resending the
original input. Pair this with `metadata` for your own bookkeeping when retrieving stored responses through the SDK.

Always include authentication headers (`Authorization: Bearer <key>` for OpenAI, `api-key: <key>` for Azure), set
`Content-Type: application/json`, and target the deployment-specific API version.

The Responses API adds reasoning controls unique to GPT-5-class deployments. `reasoning.effort` ranges from `minimal` to
`high` (higher values allocate more reasoning tokens, though gpt-5-codex does not support minimal). Models that expose
`reasoning.summary` can return concise or detailed traces when desired (available for o3 and o4-mini with limited
access; GPT-5 supports auto and detailed). `text.verbosity` lets you request terse, medium, or expansive answers without
rewriting prompts.

## Passing Information Through Prompts

Treat the prompt as layered instructions:

- System / Instructions: establish persona, constraints, or domain policies that persist across the exchange.
- User Content: carry the current task or question.
- Assistant History: supply the model's previous replies when relevant.
- Few-Shot Examples: inline demonstrations that clarify the output pattern before the actual request.

Some platforms surface the system prompt as a top-level field rather than a `messages` entry, but the mental model is
identical: persistent guidance first, task-specific turns next. For reasoning models (GPT-5, o-series), you can use
`"role": "developer"` which is functionally equivalent to `"role": "system"`. Do not use both developer and system
messages in the same request.

```jsonc
{
  "role": "system",
  "content": "You are a senior reviewer. Use bullet points and call out risks before positives."
}
```

```jsonc
// Multi-turn chat payload
{
  "model": "gpt-4o",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant that explains technical concepts clearly."},
    {"role": "user", "content": "What is an API?"},
    {"role": "assistant", "content": "An API is a set of rules that lets applications communicate."},
    {"role": "user", "content": "Can you give an example?"}
  ]
}
```

Key prompting patterns:

- Ask for explicit formats (`"Return a JSON object with keys..."` or `response_format` for strict JSON).
- Request step-by-step or chain-of-thought reasoning when tasks are complex.
- Gate creativity with low `temperature` for deterministic tasks; raise it for brainstorming.
- Supply 3-5 exemplar inputs/outputs for transformation or translation tasks when you need the model to follow a precise
  pattern.
- Calibrate system instructions to the "right altitude": clear principles and constraints, without brittle if/else
  enumerations or vague directives like "be helpful."

The model will not infer hidden expectations: spell out tone, length, and structure so it can comply.

Prompt design benefits from a test-driven loop: define what "good" looks like, craft a baseline system prompt plus user
instructions, assemble diverse test cases, and iterate until the model passes. Few-shot blocks work best when you
provide 3-5 representative examples that anchor tricky formatting or domain-specific tone. Encourage chain-of-thought
sparingly; step-by-step reasoning boosts accuracy for analytical tasks but increases latency and token spend, so reserve
it for problems that truly need deeper reasoning.

Aim for a "right altitude" system prompt: specific enough to give reliable heuristics, flexible enough to let the model
generalise. Avoid giant if/else lists that hardcode every scenario, but also avoid vague exhortations like "be helpful."
Instead, structure the system message into sections such as `<instructions>`, `<background_information>`, and
`<examples>`, and show diverse canonical cases so the model learns from demonstration rather than brittle rulebooks.

## Context Windows and Memory Strategy

Models have finite context windows. GPT-4o supports 128,000 tokens. GPT-5 series supports 128,000 tokens standard with
expandable capacity up to 272,000 tokens (and 1M tokens in preview for some deployments). Every request must contain the
full state the model needs; nothing is retained server-side, and the model cannot fetch external links unless you
provide the content explicitly. Context engineering is the craft of curating the optimal set of tokens at inference
time, treating this window as a scarce "attention budget" with diminishing returns: each additional token increases n^2
attention work yet yields less incremental value if it's noise.

Organize context by priority:

1. System or instructions block.
2. Tool and function definitions (if any).
3. Few-shot examples and reference snippets.
4. Most recent conversation turns or tool results.

Best practices:

- Curate aggressively so each token "earns its place"; trim outdated or redundant text to combat context rot (attention
  cost grows roughly with n^2).
- Use structure (Markdown headers, XML-like tags such as `<instructions>` or `<background>`) to delineate sections.
- Implement auto-compaction near 95% of the window: drop stale tool traces, summarize old turns, or restart with a
  concise recap.
- Maintain persistent notes outside the prompt (e.g., `NOTES.md`, vector store entries) and re-inject them just-in-time
  via tool calls; progressive disclosure keeps the active context scoped to what matters now.
- For large workflows, break problems into sub-agents: a manager delegates to specialized workers operating in fresh
  contexts and aggregates concise reports.

Treat the context window as RAM in a resource-constrained service: load the minimum viable working set, cache what
you'll reuse, and evict aggressively before you thrash the budget.

Context capacity covers both prompt and completion, yet output limits stay smaller than the maximum
window. GPT-4o caps replies at approximately 16,000 tokens. GPT-5 series models cap output at 32,768 tokens. Plan for
the API to stop well before hitting the theoretical maximum.

Quality drops when you approach the ceiling. "Lost-in-the-middle" effects appear once you exceed approximately
50-55% of the context limit, so keep high-signal content near the top and avoid stuffing unless the task truly requires
it.

Think in tiers when assembling the prompt:

- High priority: current task directives, recent tool outputs, safety or compliance guardrails.
- Medium priority: few-shot examples, historical decisions, shared glossary terms.
- Low priority: encyclopedic references or large raw datasets; fetch them only when needed via retrieval tools.

Context hygiene pays measurable dividends: summarisation-based compaction alone can lift complex-task success by ~29%;
pairing compaction with external memory tools boosts performance to ~39% while cutting token consumption by up to 84%
across long workflows.

Structured note-taking gives agents durable memory. Writing to `NOTES.md` or a task log lets them track tallies or
decisions across thousands of tool calls without overloading the live context.

Just-in-time retrieval keeps prompts lean: store lightweight identifiers (file paths, record IDs, URL stubs) and let the
agent pull full content via tool calls only when the task actually needs it. Surface summaries or metadata up front,
then expose targeted tools like `getDetails(id)` so the model loads heavy data in-context only after it has narrowed
candidates. Add heuristics in the system prompt ("prefer many small, filtered queries over one broad sweep") so the
agent navigates the information landscape efficiently.

## Token Mechanics and Cost Awareness

Tokens are subword units; rough rule of thumb is 1 token ~ 4 characters of English. You pay for:

- Input tokens: everything you send (system prompts, history, tool schemas).
- Output tokens: generated completion, priced at roughly 2-3x the input rate.
- Cached tokens: reused context, billed at reduced rates when supported.
- Reasoning tokens: internal thinking budget for GPT-5 and o-series when you raise `reasoning.effort`.

Tokenization varies with spacing and casing: `" red"`, `" Red"`, and `"Red"` are distinct tokens. The sentence
`"You miss 100% of the shots you don't take"` becomes 11 tokens. Non-English and technical jargon consume more
tokens per character than conversational English.

Every vendor (and even individual models within a vendor family) use their own tokenizer definitions, so counts are not
portable. Rely on the official tooling (OpenAI `tiktoken`, Azure/OpenAI client SDK helpers, Anthropic `count_tokens`
endpoints, etc.) to estimate prompts ahead of time, and verify the server's authoritative numbers by reading the `usage`
object in each API response (`prompt_tokens`, `completion_tokens`, `reasoning_tokens`, cached token counts, and so on)
as documented by the provider.

Usage responses expose the breakdown so you can audit costs and reasoning budgets:

```jsonc
"usage": {
  "prompt_tokens": 245,
  "completion_tokens": 892,
  "total_tokens": 1137,
  "prompt_tokens_details": {"cached_tokens": 0},
  "completion_tokens_details": {"reasoning_tokens": 156}
}
```

Azure sample pricing (per 1M tokens, 2025 preview): GPT-5 input $1.25 / output $10.00, GPT-5-mini input $0.25 / output $
2.00, GPT-5-nano input $0.05 / output $0.40, GPT-4o input $2.50 / output $10.00, GPT-4o-mini input $0.15 / output $0.60.

Monitor `usage` blocks in responses and add alerting for spikes. Pre-flight prompts with tokenizer libraries (
`tiktoken`, `num_tokens_from_string`, etc.) so you know the budget before sending. Optimise by removing filler text,
choosing the cheapest viable model, batching non-urgent jobs (Azure/OpenAI batch APIs offer ~50% discounts for 24-hour
processing), caching common system prompts or tool definitions, and reusing conversation buffers when safe.

Cost optimisation checklist:

- Choose the lowest-cost model tier that still meets quality goals.
- Tighten prompts and examples to remove redundant wording.
- Implement conversation caching or short-term storage to avoid retransmitting unchanged context.
- Track token usage in dashboards, set alerts, and investigate anomalies quickly.
- Cap `max_tokens`/`max_output_tokens`/`max_completion_tokens` to prevent runaway completions without truncating
  essential answers.
- Validate that `prompt_tokens + max_output_tokens` remain under the model's context limit before every request; if
  documents overrun that budget, chunk them or summarise first.

## Managing Stateless Conversations

Because APIs are stateless, client applications track the conversation buffer.

- Chat Completions: append each turn to `messages[]` and resend the full array every call.
- Responses API: supply `previous_response_id` instead of resending, letting the platform link turns internally.

Strategies as conversations grow:

- Sliding window: keep the last N turns verbatim.
- Summarization: compress older turns using the model itself and replace them with short summaries.
- Hybrid: recent messages verbatim, older ones summarized.
- Remove large media attachments or obsolete tool outputs to save tokens.
- Remember that including the same image or large tool payload repeatedly charges tokens every time; strip or summarise
  them once they're no longer relevant.

Implement exponential backoff retries for HTTP 429 (rate limits) with doubling delays (1s, 2s, 4s, ...), and watch the
response headers (`x-ratelimit-remaining-requests`, `x-ratelimit-remaining-tokens`) to understand remaining quota.
Re-sending long histories inflates both latency and cost, so prune early and frequently.

Because you choose exactly which turns to resend, you can prune, summarise, or augment history on every call: inject
fresh knowledge, omit stale chatter, or slot in externally retrieved documents just in time.

Implementation pattern: maintain an array (or list) of message objects locally, append the assistant's response after
every API call, and resend the full array (or sliding subset) with each new request so the stateless service receives
the intended context.

Rate limiting covers both requests-per-minute (RPM) and tokens-per-minute (TPM) caps; monitor the headers to
anticipate throttling and budget bursts.

At runtime the illusion of memory comes from your client: call one sends just the first user turn, call two resends that
message plus the assistant reply and the new user turn, and so on. Persist those transcripts (Redis for short-lived
chats, a database for long-lived sessions) so you can resume conversations without losing state.

## Parameter Tuning Cheat Sheet

| Parameter                                                    | Scope                                       | Effect                                                                                                                                                                                       |
|--------------------------------------------------------------|---------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `temperature` (0.0-2.0)                                      | Chat Completions (not reasoning models)     | Higher values boost randomness; 0.0 is maximally deterministic.                                                                                                                              |
| `top_p` (0.0-1.0)                                            | Chat Completions (not reasoning models)     | Nucleus sampling; restricts token pool to a probability mass. Tune either `temperature` or `top_p`, not both.                                                                                |
| `max_tokens` / `max_completion_tokens` / `max_output_tokens` | All models                                  | Caps completion length; protect against truncation (finish_reason `length`). Use `max_completion_tokens` with Chat Completions for reasoning models; `max_output_tokens` with Responses API. |
| `frequency_penalty`                                          | Chat Completions (not reasoning models)     | Penalizes repeated tokens proportional to count.                                                                                                                                             |
| `presence_penalty`                                           | Chat Completions (not reasoning models)     | Discourages reusing previously mentioned tokens (topic diversity).                                                                                                                           |
| `stop` (up to 4 sequences)                                   | All models                                  | Halt generation on markers like `"NEXT"` or `"\n\n"`; the stop text itself is omitted from the response.                                                                                     |
| `reasoning.effort` (minimal-high)                            | GPT-5, o-series (Responses API)             | Allocates more reasoning tokens/time for tougher tasks. Values: minimal, low, medium, high. gpt-5-codex does not support minimal.                                                            |
| `text.verbosity` (low-high)                                  | GPT-5 (Responses API)                       | Controls response length/detail independent of token limits. Values: low, medium, high.                                                                                                      |
| `reasoning.summary` (`auto`, `detailed`)                     | o3, o4-mini (Responses API, limited access) | Toggles whether the model exposes a detailed reasoning trace alongside the final answer.                                                                                                     |
| `seed` + `system_fingerprint`                                | Select models                               | Enable reproducibility.                                                                                                                                                                      |

Remember that max token parameters apply only to the generated completion; if prompt plus completion would exceed the
model's total context window, the API stops early and returns `finish_reason: "length"`.

Additional controls include `n` (number of completions), `logprobs` (token probabilities for analysis), and
`response_format`/`json_schema` for structured output guarantees.

GPT-5's Responses API and reasoning models intentionally omit legacy controls: you won't find `frequency_penalty`,
`presence_penalty`, `temperature`, or `top_p` for these models. In return, GPT-5 introduces richer reasoning controls (
`reasoning.effort`, optional `reasoning.summary`) and stylistic steering via `text.verbosity`.

Use stop sequences to enforce template boundaries, clamp rambling answers, or prevent filler phrases when the model runs
out of content.

Always inspect `finish_reason` in responses to detect truncation, refusals, or tool requests.

## Tool Calling and Structured Outputs

Tool (function) calling lets models emit structured intent that your application executes.

```jsonc
{
  "tools": [{
    "type": "function",
    "function": {
      "name": "get_weather",
      "description": "Get the current weather for a city",
      "parameters": {
        "type": "object",
        "properties": {
          "location": {
            "type": "string",
            "description": "City and country, e.g. Paris, France"
          },
          "units": {
            "type": "string",
            "enum": ["celsius", "fahrenheit"]
          }
        },
        "required": ["location", "units"],
        "additionalProperties": false
      }
    }
  }]
}
```

Think of the workflow in three phases: describe tools up front (JSON Schema definitions live alongside the prompt and
consume input tokens), let the model decide whether to call a tool or answer directly, then execute the requested
function(s) and feed results back for the final response.

```jsonc
// Response snippet when the model decides to call a tool
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "get_weather",
          "arguments": "{\"location\": \"San Francisco, CA\", \"units\": \"celsius\"}"
        }
      }]
    },
    "finish_reason": "tool_calls"
  }]
}
```

Workflow:

1. Include tool schemas in the request (counts toward input tokens).
2. Inspect the response for `tool_calls` and parse the function name plus JSON arguments.
3. Execute the real function, capture results.
4. Append a new message with `role: "tool"`, matching `tool_call_id`, and stringified output.
5. Resend conversation so the model can integrate results into a final answer.
6. Continue the loop while new tool calls appear, or conclude when you receive a normal text response.

Control usage via `tool_choice` (`"auto"`, `"none"`, `"required"`, or a specific function). GPT-5 custom tools
additionally allow raw text payloads when JSON is inconvenient. Custom tools support optional context-free grammar
constraints to enforce format without rigid JSON schemas.

```jsonc
// Append tool results back into the conversation
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "{\"temperature\": 18, \"conditions\": \"partly cloudy\"}"
}
```

For guaranteed schemas, enable Structured Outputs:

- Function strict mode: set `"strict": true` inside the function definition. The model can only emit arguments that
  satisfy your JSON Schema.
- Response format: `{"type": "json_schema", "json_schema": {"name": "...", "strict": true, "schema": {...}}}` forces the
  main completion into your schema.

Schema rules: root must be `type: "object"` (no `anyOf` at the root), list every property in `required`, set
`additionalProperties: false`, limit nesting to 5 levels and 100 properties. Optional behavior uses `anyOf` with `null`.
First-run caching can add up to ~10 seconds for simple schemas (and approach a minute for very complex ones); subsequent
calls are fast, and schema definitions themselves are not billed as prompt tokens. Structured outputs use constrained
decoding so the model can only emit tokens that keep the payload valid; it guarantees format, not factual correctness,
so still validate content. Schemas can reference themselves with `"$ref": "#"` to model recursive structures, and
reusing the same schema name improves cache hits. Only a subset of JSON Schema is supported, so avoid deep conditional
logic or root-level unions, and remember that truncation (`finish_reason: "length"`) can still yield invalid payloads.

Parallel function calling lets newer models emit multiple tool invocations in one turn: execute them all (ideally in
parallel), append each tool result with the matching `tool_call_id`, then continue. When using strict structured
outputs, set `parallel_tool_calls` to `false` because the modes are mutually exclusive (also note that parallel tool
calls are not supported when `reasoning_effort` is set to minimal). Keep sequential fallbacks for workflows where a
later call depends on earlier tool output, otherwise the model may guess placeholder values.

JSON mode remains handy when you only need syntactically valid JSON without strict schemas: set `response_format` to
`{"type": "json_object"}` and remind the model in the system prompt to respond with JSON. Validate outputs regardless,
because field names and shapes can drift. If you omit the instruction, the model may stream whitespace until timeout.

High-quality tool definitions drive reliable orchestration: spell out when each function should fire, describe
parameters with concrete formats (e.g., `YYYY-MM-DD`), constrain enums wherever possible, and use unambiguous names like
`user_id` instead of `user`.

Design tools the way you design clean APIs: minimal overlap, single responsibility, and strong affordances. If you can't
articulate when a tool should be chosen, neither can the model. Keep outputs token efficient: paginate, filter, and
truncate so you return just the data needed for the next reasoning step. Where mistakes are common, design the interface
so the model cannot make the error (e.g., require absolute file paths or validated IDs).

Common use cases: data extraction to structured stores, external API lookups, transactional actions (emailing,
scheduling, booking), chaining multi-step workflows, translating natural language to SQL, complex analytical
calculations, and safety or compliance moderation pipelines; the model supplies intent, your code performs the work.

## Multimodal Inputs

Modern models (GPT-4o, GPT-5) handle text, images, and audio within the same API.

Images: embed in the `content` array with `type: "image_url"` (preferred) or `type: "input_image"` with base64 data.
`detail` controls processing cost (`"low"` = 85 tokens flat; `"high"` uses 512x512 tiles at 170 tokens each plus base
85; `"auto"` lets the model decide). The platform caps images at 20 MB and up to 50 per request.

```jsonc
{
  "messages": [{
    "role": "user",
    "content": [
      {"type": "text", "text": "Describe the chart"},
      {"type": "image_url", "image_url": {"url": "https://example.com/chart.png", "detail": "high"}}
    ]
  }],
  "max_tokens": 400
}
```

Audio: send files to transcription endpoints (MP3/MP4/WAV/WEBM, up to 25 MB) for speech-to-text (legacy `whisper-1`
remains async-only, while newer `gpt-4o-transcribe` models target tougher accents and noise); generate speech with TTS
models (`tts-1`, `tts-1-hd`, `gpt-4o-mini-tts`) specifying voice (`Alloy`, `Nova`, etc.) and optional natural language
style instructions like "Speak in a warm, empathetic tone with a French accent." Realtime audio APIs exchange
base64-encoded PCM16 frames (24 kHz, mono, little-endian) over WebSockets for low-latency conversations with built-in
voice activity detection.

Audio billing differs from text: plan on roughly $0.06 per input audio minute and $0.24 per output audio minute for
realtime models. High-quality recordings and clear speaker separation improve accuracy.

Multimodal best practices: resize images to needed detail, choose `low` detail when coarse recognition suffices, give
textual context to anchor analysis, and remove obsolete images from history to avoid repeated token charges.

Limitations to note: models still approximate fine-grained counts, struggle with low-contrast or stylised text, refuse
to identify specific individuals, and can misread rotated, panoramic, or fisheye imagery, so validate critical outputs
before acting.

## Streaming Responses

Set `stream: true` to receive Server-Sent Events as tokens are produced.

```jsonc
{
  "input": "Explain quantum computing to a high-schooler.",
  "stream": true
}
```

Each SSE frame carries incremental deltas (`choices[0].delta.content`). Accumulate them into a buffer for display, stop
when you receive `data: [DONE]`, and record the final `finish_reason`. Streaming improves perceived latency for long
answers and allows progressive UI updates. Skip it for batch processing or when you must validate the entire payload
before acting.

Capture partial output as it streams so you can fall back gracefully if the connection drops; retry with exponential
backoff and re-send the conversation plus the portion you've already surfaced when necessary.

## Reliability and Operational Guardrails

- Inspect `finish_reason` each call (`stop`, `length`, `content_filter`, `tool_calls`).
- Validate schema-constrained outputs before using them downstream.
- Log token usage, reasoning tokens, and cost per request; set automated alerts for anomalies.
- Implement retry logic for transient 5xx/429 errors with exponential backoff.
- Handle safety refusals gracefully. Structured outputs include a `refusal` object when content violates policy.
- Version prompts, schemas, and tool definitions so you can reproduce model behavior.
- Parse and validate JSON responses before you call downstream systems, and build fallbacks for partial or truncated
  results.
- Log response/request identifiers (`id`, `x-request-id`, `system_fingerprint`) alongside application traces so you can
  debug regressions and share precise evidence with support.

## Architectural Checklist

- Choose the cheapest model tier that satisfies quality requirements; mix GPT-5 for complex reasoning with GPT-5-mini or
  GPT-5-nano for routine tasks.
- Prefer just-in-time retrieval over monolithic prompts; let the agent pull docs or database results via tools only when
  needed.
- Use hierarchical or modular agent flows to keep token budgets lean.
- Periodically audit conversation buffers for drift or irrelevant content.
- Start simple, measure against accuracy/cost/latency KPIs, then iterate with targeted optimizations (prompt edits,
  caching, batching).

Mastering these foundations gives you tight control over what the model sees, how it reasons, and the economics of every
call, whether you run on OpenAI's public endpoints or Azure-hosted deployments.
