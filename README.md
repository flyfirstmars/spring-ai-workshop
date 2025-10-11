# AI Native Product Development using Spring AI

Hands-on material for the "AI Native Product Development using SpringAI" workshop. The project demonstrates how to
integrate Spring AI 1.0.3 with Azure OpenAI (GPT-4o family) using Java 25 and Spring Boot 3.5. VoyagerMate, a
multimodal travel copilot delivered through Spring Shell commands, serves as the reference example throughout the
material.

## Contents

- Spring Shell commands showcasing text and image interactions with Azure OpenAI via `ChatClient`
- Structured itinerary generation using Spring AI output converters and travel-focused tool calls
- Workflow orchestration patterns for agent-like trip planning
- Documentation overview:
    - [Foundations Notebook](docs/foundations.ipynb): End-to-end LLM foundations walkthrough with runnable HTTP examples.
    - [Session 01 Guide](docs/session-01.md): LLM fundamentals, prompt engineering, ChatClient usage, multimodal flow,
      structured outputs, and tooling patterns.
    - [Session 02 Guide](docs/session-02.md): Workflow vs agent design, orchestration patterns, context engineering,
      memory, and optimisation strategies.
    - [Setup Guide](docs/setup.md): Environment prerequisites, deployments, and prep checklist.
    - [Technical Reference](docs/technical-reference.md): Exception handling, tool visibility, modern Java techniques.
    - [Exploration Exercises](docs/exercises.md): Codebase map and open-ended prompts for extending VoyagerMate.

## Prerequisites

1. Java 25 (Temurin recommended)
2. Maven 3.9+
3. Azure OpenAI resource with GPT-4o or GPT-4o mini deployment
4. Postgres (Docker Compose via Rancher Desktop) for the future Session 3 preview
5. Environment variables configured before running the application:
   ```bash
   export AZURE_OPENAI_ENDPOINT="https://<resource>.openai.azure.com"
   export AZURE_OPENAI_API_KEY="<api-key>"
   export AZURE_OPENAI_CHAT_DEPLOYMENT="gpt-4o-mini"
   # Optional: provide a dedicated transcription deployment if available
   export AZURE_OPENAI_TRANSCRIPTION_DEPLOYMENT="whisper-1"
   ```

## Running the demos

```bash
./mvnw spring-boot:run
```

The application starts an interactive Spring Shell session (`voyagermate>`). Try the following commands and adapt them
to your use case:

```shell
shell:>chat --prompt "Plan a weekend in Lisbon with accessibility tips."
shell:>describe-image \
  --file src/main/resources/images/background-vacation1.jpeg \
  --prompt "Describe this beach scene."
shell:>transcribe-audio \
  --file src/main/resources/audio/traveller-note.m4a \
  --prompt "Summarise this voice note."
shell:>plan-itinerary \
  --name Mira --origin Amsterdam --destination Tokyo \
  --depart 2025-04-10 --return 2025-04-20 \
  --budget balanced --interests food,design
shell:>workflow \
  --name Mira --origin Amsterdam --destination Tokyo \
  --depart 2025-04-10 --return 2025-04-20 \
  --budget balanced --interests food,design
```

*Requires a transcription deployment for audio commands.*

## Documentation

### Workshop Sessions

- [Session 01: Augmented LLM Foundations](docs/session-01.md): Theory primer covering LLM fundamentals, prompt
  engineering, ChatClient usage, multimodal flow, structured outputs, and tooling patterns for VoyagerMate.
- [Session 02: Agents & Workflows](docs/session-02.md): Agent-centric design guide mapping Anthropic principles to
  Spring AI, covering workflow patterns, agent loops, context engineering, memory, and optimisation strategies.

### Reference Materials

- Session 01 appendix: see `docs/session-01.md#appendix-quick-reference--configuration` for condensed API and
  configuration snippets
- [Technical Reference](docs/technical-reference.md): Exception handling, tool visibility, modern Java features
- [Setup Guide](docs/setup.md): Environment prerequisites and prep checklist
- [Exploration Exercises](docs/exercises.md): Codebase map and exploratory prompts for extending VoyagerMate

## Tool Call Visibility Feature

VoyagerMate provides transparency into AI tool usage through enhanced command output formatting. When the AI assistant
uses tools during processing, the tool names are displayed in the response metadata.

### Output Format

Commands that use tools display the information in this format:

```
Model: azure-openai
ToolCall: find_attractions, estimate_budget, travel_gap_checker
Latency: 2340 ms

[Response content...]
```

### Available Tools

- find_attractions: returns must-see experiences for destinations (Rome, Tokyo, Barcelona)
- estimate_budget: calculates base budget per traveller for trips
- travel_gap_checker: suggests buffer days between travel legs

### Examples

Chat without tools:

```bash
shell:>chat --prompt "Help me plan a weekend escape from Paris in October."
```

Output shows: `Model: azure-openai` and `Latency: 711 ms` (no ToolCall line)

Itinerary planning with tools (debug logs):

```bash
shell:>plan-itinerary -d "Rome" --depart "2024-12-01" --return "2024-12-07" -n "John" -b "balanced" -i "history,food"
```

Debug logs show tool execution:

```
DEBUG ... Executing tool call: estimate_budget
DEBUG ... Executing tool call: find_attractions
```

*Note: The `plan-itinerary` command returns structured JSON directly, so tool calls appear in debug logs rather than the
response metadata.*

## Source layout

- `ge.jar.springaiworkshop.voyagermate`: feature module containing shell commands, core services, workflow, tools, and
  models.
- Configuration and bootstrapping remain in `ge.jar.springaiworkshop.config` to emphasise cross-feature infrastructure.

## Next steps (Coming soon)

Session 3 (data integration) and Session 4 (validation/evaluation) will build on this baseline. Update the
`VoyagerTools` class with organisation-specific data sources and expand the workflow service to demonstrate advanced
agent behaviours. If your Azure subscription does not include an audio transcription deployment, skip the
`transcribe-audio` demo or provide a pre-generated transcript for attendees.
