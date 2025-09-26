# VoyagerMate Spring AI Workshop

Hands-on material for the "Building Intelligent Java Travel Assistants" workshop. The project demonstrates how to integrate Spring AI 1.0.2 with Azure OpenAI using Java 25 and Spring Boot 3.5 to craft **VoyagerMate**, a multimodal travel copilot delivered through Spring Shell commands.

## Contents
- Spring Shell commands showcasing text, image, and audio interactions with Azure OpenAI via `ChatClient`
- Structured itinerary generation using Spring AI output converters and travel-focused tool calls
- Workflow orchestration patterns for agent-like trip planning
- Workshop guides for the first two sessions (Augmented LLM fundamentals and Agents/Workflows)

## Prerequisites
1. Java 25 (Temurin recommended)
2. Maven 3.9+
3. Azure OpenAI resource with GPT-5 family deployment (`gpt-5`, `gpt-5-mini`, `gpt-5-nano`)
4. Postgres (Docker Compose via Rancher Desktop) for the future Session 3 preview
5. Environment variables configured before running the application:
   ```bash
   export AZURE_OPENAI_ENDPOINT="https://<resource>.openai.azure.com"
   export AZURE_OPENAI_KEY="<api-key>"
   export AZURE_OPENAI_DEPLOYMENT="gpt-5-mini"
   ```

## Running the demos
```bash
./mvnw spring-boot:run
```
The application starts an interactive Spring Shell session (`voyagermate>`) with no embedded web server. Use the commands below to explore VoyagerMate features:

- `chat --prompt "Help me plan a weekend escape from Paris in October."`
- `describe-image --file assets/barcelona.jpg --prompt "Suggest activities inspired by this photo"`
- `transcribe-audio --file assets/voice-note.mp3`
- `plan-itinerary --name Mira --origin Amsterdam --destination Tokyo --depart 2025-04-10 --return 2025-04-20 --budget balanced --interests food,design,nightlife`
- `workflow --name Mira --origin Amsterdam --destination Tokyo --depart 2025-04-10 --return 2025-04-20 --budget balanced --interests food,design,nightlife`

Refer to `docs/session-01-foundations.md` and `docs/session-02-agents-workflows.md` for detailed walkthroughs and additional scenarios.

## Source layout
- `ge.jar.springaiworkshop.voyagermate` — feature module containing shell commands, core services, workflow, tools, and models.
- Configuration and bootstrapping remain in `ge.jar.springaiworkshop.config` to emphasise cross-feature infrastructure.

## Next steps
Session 3 (data integration) and Session 4 (validation/evaluation) will build on this baseline. Update the `VoyagerTools` class with organisation-specific data sources and expand the workflow service to demonstrate advanced agent behaviours.
