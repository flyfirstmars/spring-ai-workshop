# Session 1 — Augmented LLM Fundamentals

**Duration**: 4 hours  
**Focus**: Spring AI ChatClient with multimodal prompts, structured outputs, and Java-based tool calls powering VoyagerMate through Spring Shell.

---

## 1. Kick-off (15 min)
- Welcome travellers and introduce **VoyagerMate**.
- Set goals: text, image, and audio interactions + itinerary generation.
- Confirm Azure OpenAI deployments and environment variables (see `README.md`).

## 2. Spring AI ChatClient Overview (40 min)
- Present architecture: `ChatClientConfig` wiring a system prompt for VoyagerMate.
- Explain how `ChatClient` abstracts Azure OpenAI (options, deployment name, temperature).
- Show simple text chat with the `chat` command.

### Demo — Text Conversation
```bash
chat --prompt "Help me plan a weekend escape from Paris in October."
```
- Discuss latency, metadata, and how we surface them via `ChatResponsePayload` in the CLI output.

## 3. Multimodal Prompts (60 min)
- Walk through `ImageChatRequest` + Spring AI `Media` API used inside the shell command.
- Explain Base64 expectations, MIME type handling, and restrictions (handled automatically from files).

### Demo — Image Insights
```bash
describe-image --file assets/barcelona.jpg \
               --prompt "Suggest activities inspired by this photo"
```

### Demo — Audio Notes
```bash
transcribe-audio --file assets/voice-note.mp3 \
                 --prompt "What did the traveller mention in this recording?"
```
- Highlight how the service injects transcription instructions in the system prompt.

## 4. Structured Output (45 min)
- Introduce `BeanOutputConverter` for type-safe itineraries (`ItineraryPlan`).
- Break down the schema inserted into the system message.
- Discuss converting model output into `ItineraryDay` records.

### Demo — Plan Generation
```bash
plan-itinerary --name Mira --origin Amsterdam --destination Tokyo \
               --depart 2025-04-10 --return 2025-04-20 \
               --budget balanced --interests food,design,nightlife
```
- Inspect the JSON response and map to UI ideas (timeline, cost summary).

## 5. Java Tool Calls (40 min)
- Showcase `VoyagerTools` class — regular Java methods annotated with `@Tool`.
- Explore example tool outputs: attractions, budget estimates, travel gap checker.
- Experiment with prompts that encourage tool usage via the shell commands.

### Lab — Custom Travel Intelligence
1. Add a tool that returns visa requirements for selected countries.
2. Update `ItineraryPlan` to include `visaNotes` and adjust the converter usage.
3. Share prompts/commands that trigger or suppress the new tool call.

## 6. Wrap-Up & Session 2 Preview (10 min)
- Recap: ChatClient, multimodal messages, structured output, tool delegation.
- Preview Session 2: workflow orchestration and agent-like behaviours.

---

## Reference Links
- [Spring AI Reference Guide](https://docs.spring.io/spring-ai/reference/)
- [Azure OpenAI Documentation](https://learn.microsoft.com/azure/ai-services/openai/)
- `docs/setup.md` for environment checklists.
