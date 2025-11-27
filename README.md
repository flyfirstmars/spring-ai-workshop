# AI Native Product Development using Spring AI

Hands-on material for the "AI Native Product Development using SpringAI" workshop. The project demonstrates how to integrate Spring AI 1.1.0 with Azure OpenAI (GPT-4o family) using Java 21 and Spring Boot 3.5. VoyagerMate, a multimodal travel copilot delivered through Spring Shell commands, serves as the reference example throughout the material.

## Contents

- Spring Shell commands showcasing text and image interactions with Azure OpenAI via `ChatClient`
- Structured itinerary generation using Spring AI output converters and travel-focused tool calls
- Workflow orchestration patterns for agent-like trip planning (`workflow`, `parallel-insights`, `route-intent`, `refine-itinerary`, `orchestrator-workers`, `multi-agent-plan`)
- Session 3: Chat Memory and Data Integration
    - Part 1: Persistent conversation history with PostgreSQL (`chat-session`, `show-history`, `clear-history`, `list-sessions`)
    - Part 2: ETL Pipeline for document processing (`load-guides`, `show-documents`, `search-docs`, `clear-docs`)
- Interactive Notebooks - Learn Spring AI with executable examples:
    - [Foundations](notebooks/chat-client-to-tool-calling.ipynb): LLM fundamentals, ChatClient, multimodal, structured outputs, tools
    - [Chat Memory](notebooks/chat-memory.ipynb): Session-based conversations, memory management, stateful interactions
- Documentation overview:
    - [Session 01 Guide](docs/session-01.md): LLM fundamentals, prompt engineering, ChatClient usage, multimodal flow, structured outputs, and tooling patterns.
    - [Session 02 Guide](docs/session-02.md): Workflow vs agent design, orchestration patterns, context engineering, memory, and optimisation strategies.
    - [Session 03 Guide](docs/session-03.md): Chat memory architecture, JDBC persistence, conversation management, and memory-aware interactions.
    - [Agentic Workflow Reference](docs/agentic-workflows.md): Mapping Anthropic patterns to VoyagerMate commands.
    - [Setup Guide](docs/setup.md): Environment prerequisites, deployments, and prep checklist.
    - [Technical Reference](docs/technical-reference.md): Exception handling, tool visibility, modern Java techniques.
    - [Exploration Exercises](docs/exercises.md): Codebase map and open-ended prompts for extending VoyagerMate.

## Prerequisites

1. Java 21+ (Temurin recommended)
2. Maven 3.9+
3. Container runtime for Session 3 (one of the following):
   - [Docker Desktop](https://www.docker.com/products/docker-desktop/)
   - [Rancher Desktop](https://rancherdesktop.io/)
   - [Podman](https://podman.io/)
4. Azure OpenAI resource with GPT-4o or GPT-4o mini deployment
5. Environment variables configured before running the application:
   ```bash
   export DIAL_API_ENDPOINT="https://<resource>.openai.azure.com"
   export DIAL_API_KEY="<api-key>"
   export DIAL_API_OPENAI_DEPLOYMENT="gpt-4o-mini"
   # Optional: provide a dedicated transcription deployment if available
   export DIAL_API_OPENAI_TRANSCRIPTION_DEPLOYMENT="whisper-1"
   ```

### Starting PostgreSQL (Session 3)

Session 3 requires PostgreSQL for persistent chat memory. Start the database using Docker Compose:

```bash
docker-compose up -d
```

This starts a PostgreSQL 16 container with:
- Database: `voyagermate`
- Username: `postgres`
- Password: `postgres`
- Port: `5435`

The database schema is automatically created via Flyway migrations on application startup.

## Running the demos

```bash
./mvnw spring-boot:run
```

The application starts an interactive Spring Shell session (`voyagermate>`). Try the following commands and adapt them to your use case:

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
shell:>parallel-insights --destination Lisbon --depart 2025-05-01 --return 2025-05-06 --interests food,cycling
shell:>route-intent --prompt "My flight was cancelled, what now?" --destination Toronto
shell:>refine-itinerary --prompt "Make the Rome plan more poetic" --destination Rome --depart 2025-06-01 --return 2025-06-05
shell:>orchestrator-workers --prompt "Take a family across Japan for two weeks" --destination Tokyo --depart 2025-07-10 --return 2025-07-24
shell:>multi-agent-plan --request "Plan a 10-day culinary tour of Italy starting in Rome."

# Session 3 Part 1: Chat Memory Commands (requires PostgreSQL)
shell:>chat-session --session travel-2025 --prompt "I want to plan a trip to Japan"
shell:>chat-session --session travel-2025 --prompt "What about visiting Kyoto for 3 days?"
shell:>show-history --session travel-2025
shell:>list-sessions
shell:>clear-history --session travel-2025

# Session 3 Part 2: ETL Pipeline Commands
shell:>load-guides
shell:>show-documents --limit 5
shell:>show-documents --type markdown --limit 3
shell:>search-docs --query "temples"
shell:>search-docs --query "food recommendations"
shell:>clear-docs
```

*Requires a transcription deployment for audio commands.*
*Session 3 Part 1 commands require PostgreSQL running via Docker Compose.*

## Documentation

### Interactive Learning (Notebooks)

Start here for hands-on learning with executable examples:

1. **[Setup Guide](notebooks/README.ipynb)** - Environment setup, testing, troubleshooting
2. **[Foundations](notebooks/chat-client-to-tool-calling.ipynb)** - LLM fundamentals, ChatClient, tools, structured outputs
3. **[Agentic Patterns](notebooks/agentic-patterns.ipynb)** - Workflows, agents, orchestration, optimization
4. **[Exercises](notebooks/exercises.ipynb)** - Hands-on exploration and feature building

**How to use:** Open notebooks in IntelliJ IDEA (Kotlin Notebook plugin bundled in 2023.3+)

### Workshop Sessions (Markdown)

- [Session 01: Augmented LLM Foundations](docs/session-01.md): Theory primer covering LLM fundamentals, prompt engineering, ChatClient usage, multimodal flow, structured outputs, and tooling patterns for VoyagerMate.
- [Session 02: Agents & Workflows](docs/session-02.md): Agent-centric design guide mapping Anthropic principles to Spring AI, covering workflow patterns, agent loops, context engineering, memory, and optimisation strategies.
- [Session 03: Chat Memory & Data Integration](docs/session-03.md): Part 1 covers persistent conversation memory with Spring AI's ChatMemory API and JDBC storage. Part 2 introduces the ETL pipeline for document loading and processing, preparing the knowledge base for RAG in Session 4.

### Reference Materials

- Session 01 appendix: see `docs/session-01.md#appendix-quick-reference--configuration` for condensed API and configuration snippets
- [Technical Reference](docs/technical-reference.md): Exception handling, tool visibility, modern Java features
- [Setup Guide](docs/setup.md): Environment prerequisites and prep checklist
- [Exploration Exercises](docs/exercises.md): Codebase map and exploratory prompts for extending VoyagerMate

## Tool Call Visibility Feature

VoyagerMate provides transparency into AI tool usage through enhanced command output formatting. When the AI assistant uses tools during processing, the tool names are displayed in the response metadata.

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

*Note: The `plan-itinerary` command returns structured JSON directly, so tool calls appear in debug logs rather than the response metadata.*

## Source layout

- `ge.jar.springaiworkshop.voyagermate`: feature module containing shell commands, core services, workflow, tools, and models.
- Configuration and bootstrapping remain in `ge.jar.springaiworkshop.config` to emphasise cross-feature infrastructure.

## Session 3: Chat Memory and Data Integration

VoyagerMate now supports persistent conversation memory and document processing for knowledge-enhanced interactions.

### Part 1: Chat Memory

#### Architecture

- **ChatMemory API**: Spring AI's `MessageWindowChatMemory` maintains a sliding window of recent messages
- **JDBC Persistence**: PostgreSQL storage via custom `JdbcChatMemoryRepository` with Flyway migrations
- **Advisor Pattern**: `MessageChatMemoryAdvisor` automatically injects conversation history into prompts

#### Chat Memory Commands

| Command | Description |
|---------|-------------|
| `chat-session -s <id> -p <prompt>` | Chat within a named session with memory |
| `show-history -s <id>` | Display conversation history for a session |
| `clear-history -s <id>` | Clear all messages from a session |
| `list-sessions` | List all active conversation sessions |

#### Example Multi-turn Conversation

```shell
shell:>chat-session -s japan-trip -p "I want to visit Japan for 2 weeks"
# VoyagerMate responds with suggestions...

shell:>chat-session -s japan-trip -p "Focus on traditional culture and temples"
# VoyagerMate builds on previous context...

shell:>chat-session -s japan-trip -p "What about food recommendations?"
# VoyagerMate remembers the Japan trip context...

shell:>show-history -s japan-trip
# Shows all messages in the conversation
```

### Part 2: ETL Pipeline for Data Integration

#### Architecture

- **DocumentReader**: Spring AI readers for markdown, JSON, and PDF files
- **Transformers**: `TokenTextSplitter` chunks large documents for optimal retrieval
- **Document Model**: Content with rich metadata for filtering and classification

#### ETL Commands

| Command | Description |
|---------|-------------|
| `load-guides` | Load and process all travel guide documents |
| `show-documents -l <limit>` | Display loaded documents with metadata |
| `search-docs -q <query>` | Keyword search across loaded documents |
| `clear-docs` | Clear all documents from memory |

#### Example ETL Workflow

```shell
shell:>load-guides
# Documents loaded successfully!
#   Travel guides: 12 chunks
#   Destination data: 8 documents
#   Total documents: 20

shell:>search-docs -q "ramen"
# Found 2 document(s) matching 'ramen'
# [Result 1] Source: markdown | tokyo-guide.md
#   ...Try Ichiran for tonkotsu or Fuunji for tsukemen...

shell:>show-documents -t json -l 3
# Shows destination data documents
```

#### Sample Travel Data

VoyagerMate includes sample travel knowledge in `src/main/resources/data/`:
- `travel-guides/tokyo-guide.md` - Tokyo travel information
- `travel-guides/lisbon-guide.md` - Lisbon travel information
- `destinations.json` - Structured destination data with 5 cities

## Next steps (Coming soon)

Session 4 (RAG and Vector Database) will build on Session 3's ETL pipeline, adding:
- **Embeddings**: Azure OpenAI embedding model integration
- **PGVector**: Vector storage extension for PostgreSQL
- **VectorStore**: Spring AI's abstraction for similarity search
- **QuestionAnswerAdvisor**: Automatic context retrieval for grounded responses
- **Semantic Search**: Finding relevant travel information by meaning, not keywords

If your Azure subscription does not include an audio transcription deployment, skip the `transcribe-audio` demo or provide a pre-generated transcript for attendees.
