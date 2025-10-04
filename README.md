# VoyagerMate Spring AI Workshop

Hands-on material for the "Building Intelligent Java Travel Assistants" workshop. The project demonstrates how to integrate Spring AI 1.0.2 with Azure OpenAI (GPT-4o family) using Java 25 and Spring Boot 3.5 to craft **VoyagerMate**, a multimodal travel copilot delivered through Spring Shell commands.

## Contents
- Spring Shell commands showcasing text and image interactions with Azure OpenAI via `ChatClient`
- Structured itinerary generation using Spring AI output converters and travel-focused tool calls
- Workflow orchestration patterns for agent-like trip planning
- Workshop guides for the first two sessions (Augmented LLM fundamentals and Agents/Workflows)

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
The application starts an interactive Spring Shell session (`voyagermate>`) with no embedded web server. Use the commands below to explore VoyagerMate features:

- `chat --prompt "Plan an accessible autumn weekend in Lisbon for two friends arriving from Paris."`
- `describe-image --file src/main/resources/images/background-vacation1.jpeg --prompt "Suggest a mid-range daytime plan inspired by this beach scene."`
- `describe-image --file src/main/resources/images/background-vacation2.jpeg --prompt "Design an evening food crawl that matches the atmosphere in this image."`
- `transcribe-audio --file src/main/resources/audio/traveller-note.m4a --prompt "Summarise the traveller note and suggest next booking steps."` *(requires an Azure Whisper/GPT-4o Transcribe deployment; otherwise skip this exercise)*
- `plan-itinerary --name Mira --origin Amsterdam --destination Tokyo --depart 2025-04-10 --return 2025-04-20 --budget balanced --interests food,design,nightlife`
- `workflow --name Mira --origin Amsterdam --destination Tokyo --depart 2025-04-10 --return 2025-04-20 --budget balanced --interests food,design,nightlife`

Refer to `docs/session-01-foundations.md` and `docs/session-02-agents-workflows.md` for detailed walkthroughs and additional scenarios.

## Source layout
- `ge.jar.springaiworkshop.voyagermate` — feature module containing shell commands, core services, workflow, tools, and models.
- Configuration and bootstrapping remain in `ge.jar.springaiworkshop.config` to emphasise cross-feature infrastructure.

## Next steps
Session 3 (data integration) and Session 4 (validation/evaluation) will build on this baseline. Update the `VoyagerTools` class with organisation-specific data sources and expand the workflow service to demonstrate advanced agent behaviours. If your Azure subscription does not include an audio transcription deployment, skip the `transcribe-audio` demo or provide a pre-generated transcript for attendees.
