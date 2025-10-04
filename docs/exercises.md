# Exploration Exercises

These prompts help you inspect the VoyagerMate codebase and reshape it to match the experience you want to deliver.
Treat each idea as a jumping-off point: read the implementation, understand the current behaviour, then adapt or rebuild
it using the concepts from `docs/session-01.md` and `docs/session-02.md`.

## Project Map: Where Things Live

| Area                     | Key Classes / Files                                                                                    | Typical Modifications                                                                                                   |
|--------------------------|--------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| **Shell commands**       | `src/main/java/.../voyagermate/shell/VoyagerMateCommands.java`<br>`VoyagerMateShellConfiguration.java` | Wiring prompts, adjusting command options, formatting console output, adding new commands.                              |
| **Core services**        | `voyagermate/core/VoyagerMateService.java`<br>`voyagermate/workflow/ItineraryWorkflowService.java`     | Implementing workflows, adding validation, orchestrating tool calls, integrating advisors.                              |
| **Tools & integrations** | `voyagermate/tools/VoyagerTools.java`<br>`voyagermate/tools/...`                                       | Defining `@Tool` methods, refining descriptions/parameters, handling external API failures, adding return-direct flows. |
| **Configuration**        | `config/ChatClientConfig.java`<br>`application.yml`                                                    | Setting default prompts, model options, advisors, logging levels, environment-specific overrides.                       |
| **Models & outputs**     | `voyagermate/model/*`<br>`ItineraryPlan`, `TripWorkflowSummary`                                        | Adjusting structured output schemas, adding new fields, updating converters/tests.                                      |
| **Docs & prompts**       | `docs/session-01.md`, `docs/session-02.md`, `src/main/resources/prompts/*`                             | Capturing theory, maintaining shared prompt templates, documenting new patterns.                                        |

Use this map as a reference when exploring each exercise—trace the flow from shell command → service/workflow →
tools/models so you know exactly which layer you are modifying.

## Conversational Foundations

- Study `chat` command wiring in `VoyagerMateCommands`. How does the default system prompt steer tone and what metadata
  is exposed? Experiment with alternative prompt templates or advisor combinations, then tailor logging/metrics to
  highlight the signals you care about most.
- Inspect the multimodal flow (`describe-image`, `transcribe-audio`). Identify assumptions about files, MIME types, and
  prompts. Rework the pipeline to support the media strategy you prefer—perhaps comparing multiple images, summarising
  audio for different personas, or streaming responses to the console.
- Review `ItineraryPlan` + `BeanOutputConverter`. Decide whether the schema and validation match your travel scenarios.
  Adjust fields, introduce new records, or change temperature/response format settings to achieve the reliability
  targets you set.

## Tooling and Retrieval Experiments

- Audit `VoyagerTools` descriptions and parameters. Where could better wording, stricter typing, or poka‑yoke techniques
  reduce misuse? Extend, replace, or remove tools until the set feels minimal and high-signal.
- Trace how `plan-itinerary` and related services combine tool results with structured outputs. Modify the flow to
  incorporate just-in-time retrieval, additional advisors, or external memory. Consider new evaluation checkpoints so
  you can observe tool usage over time.
- Explore the vector-store integration (if configured). Prototype your own RAG strategy: change chunking logic, add
  semantic filters, or plug in a different store implementation to see how it affects itinerary grounding.

## Workflow and Agent Patterns

- Walk through `ItineraryWorkflowService` to identify where deterministic sequencing helps or hurts. Try alternative
  workflow patterns: prompt chaining with additional validations, routing to specialised models, or parallel branches
  that feed into richer summaries.
- Design an orchestrator/worker experiment inspired by the agent blueprint in `docs/session-02.md`. Decide which
  subtasks deserve dedicated workers, how they should exchange information, and when to hand control back to a human.
- Prototype evaluator–optimizer loops for content you want to polish (e.g., narrated itineraries). Capture metrics so
  you can judge whether the quality gain outweighs additional latency/cost.

## Observability, Cost, and Safety

- Instrument token usage, tool call frequency, and latency. Build dashboards or simple console summaries that surface
  the trade-offs you find interesting.
- Implement context compaction or external memory for long-running conversations. Measure how these strategies affect
  both cost and accuracy in your test scenarios.
- Stress-test guardrails: iteration limits, tool argument validation, and human-in-the-loop checkpoints. Adjust system
  prompts and safety logic until they reflect the trust boundaries you need.

## Make It Yours

Pick any flow—chat concierge, itinerary builder, workflow orchestrator—and reimagine it. Swap models, rewire prompts, or
integrate third-party APIs. Document what you changed, why it matters, and how you validated the behaviour. The goal is
to practice treating Spring AI components as building blocks rather than fixed scripts.
