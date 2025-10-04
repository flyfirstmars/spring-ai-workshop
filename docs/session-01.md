# Augmented LLM Foundations with Spring AI

Spring AI applies familiar Spring patterns to large language models (LLMs), making it practical to build
production-ready AI services in Java. Within the "AI Native Product Development using SpringAI" workshop, VoyagerMate—a
travel concierge example—demonstrates how these concepts translate into a domain-specific product.

---

## 1. Understanding Large Language Models

### 1.1 What an LLM Is

An LLM is a pre-trained pattern-matching model that generates text, audio, or structured data by predicting the next
token in a sequence.[^1] Think of it as a well-read travel expert: it synthesises personalised answers from patterns it
absorbed during training rather than looking up exact facts.

Key characteristics:

- **Not a database** – no built-in lookups for real-time or private data.
- **Probabilistic** – temperature and sampling parameters influence creativity versus determinism.
- **Context-aware** – the model only “remembers” the tokens you send in the current request.
- **Stateless** – nothing persists across calls unless you resend it.

### 1.2 Token-by-Token Generation

Every response is emitted token by token. Tokens are ~4 characters on average, and both prompt and response tokens count
towards usage quotas.[^2] Keep interactions efficient by:

- Chunking large documents instead of pasting them wholesale.
- Preferring retrieval strategies (RAG) over monolithic prompts.
- Monitoring prompt/response token counts for cost and latency control.

### 1.3 Stateless Conversations

Because LLM APIs are stateless, you must resend prior turns to maintain context:

```java
List<Message> history = List.of(
        new UserMessage("My name is Alex"),
        new AssistantMessage("Nice to meet you, Alex!"),
        new UserMessage("What's my name?")
);

ChatResponse response = chatModel.call(new Prompt(history));
// -> "Your name is Alex."
```

Spring AI’s `MessageChatMemoryAdvisor` automates this bookkeeping so your application stays conversational without
manual history management.[^1]

---

## 2. Messages and Prompt Engineering

### 2.1 Message Roles

Spring AI encodes prompts as structured messages with explicit roles:[^2]

- **System** – global guidance (“You are VoyagerMate, an accessible travel advisor.”)
- **User** – the traveller’s request.
- **Assistant** – previous model replies that maintain conversational flow.
- **Tool** – results returned by external functions.

### 2.2 Constructing a Prompt

```java
String systemText = """
        You are VoyagerMate, a helpful travel assistant.
        Focus on logistics, budgets, and local insight.
        Reply in the style of {voice}.
        """;

SystemPromptTemplate systemTemplate = new SystemPromptTemplate(systemText);
Message systemMessage = systemTemplate.createMessage(Map.of("voice", "enthusiastic local guide"));
Message userMessage = new UserMessage("Plan a 3-day spring trip to Rome with mobility support.");

Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
ChatResponse response = chatModel.call(prompt);
```

### 2.3 Four Building Blocks of Effective Prompts

1. **Instructions** – exactly what the model should produce.
2. **External context** – traveller preferences, budgets, constraints.
3. **User input** – the direct question or task.
4. **Output cues** – required structure (tables, JSON schema, markdown sections).

Practical tips:

- Be specific: “List 5 family-friendly activities in Tokyo for April” beats “Tell me about Tokyo.”
- Communicate constraints: budgets, accessibility needs, dietary choices.
- Provide structure: define sections such as `## Morning`, `## Afternoon`, or share JSON scaffolding.
- Iterate: capture edge cases, test tone, and refine temperature settings.

### 2.4 Context Engineering

When you add instructions, examples, tools, and retrieved documents, you are performing context engineering.[^3]

- **Right altitude** – avoid vague goals (“be helpful”) or hyper-specific micromanagement.
- **Minimal toolset** – only register the functions you expect to use.
- **Few-shot examples** – offer 2–3 high-quality samples instead of exhaustive lists.
- **Just-in-time data** – fetch snippets when needed instead of saturating the prompt window.

---

## 3. Prompt Templates and Reuse

Spring AI’s `PromptTemplate` decouples static prompt structure from dynamic values.[^2]

```java
PromptTemplate template = new PromptTemplate(
        "Explain {season} travel in {destination}");
Prompt prompt = template.create(Map.of("season", "autumn", "destination", "Barcelona"));
ChatResponse response = chatModel.call(prompt);
```

Store templates as external resources to version them alongside code:

```java

@Value("classpath:/prompts/itinerary.st")
Resource travelPromptResource;

SystemPromptTemplate systemTemplate = new SystemPromptTemplate(travelPromptResource);
Message systemMessage = systemTemplate.createMessage(Map.of("specialty", "Mediterranean travel"));
```

Templates make it easier for product and content teams to collaborate without touching Java source.

---

## 4. Working with Spring AI ChatClient

### 4.1 Architectural Overview

`ChatClient` wraps a provider-specific `ChatModel` with fluent APIs, default options, advisors, and tool
orchestration.[^1]

```
┌─────────────────┐
│ Your Service    │  (ChatClient)
└────────┬────────┘
         │
┌────────▼────────┐
│ Spring AI Layer │  Prompt assembly, retries,
│                 │  observability, advisors
└────────┬────────┘
         │
┌────────▼────────┐
│ Provider API    │  Azure OpenAI, OpenAI, Anthropic...
└─────────────────┘
```

### 4.2 Fluent Usage Patterns

```java
// Simple response
String answer = chatClient.prompt()
                .user("Best time to visit Santorini?")
                .call()
                .content();

// Override options per call
String creative = chatClient.prompt()
        .user("Suggest quirky Barcelona activities")
        .options(ChatOptions.builder().temperature(1.1).build())
        .call()
        .content();

// Stream progressively
Flux<String> stream = chatClient.prompt()
        .user("Tell me about Iceland in winter")
        .stream()
        .content();

// Map to typed record
ItineraryPlan plan = chatClient.prompt()
        .user("Build a balanced 5-day Tokyo itinerary")
        .call()
        .entity(ItineraryPlan.class);
```

### 4.3 Metadata and Resilience

```java
ChatResponse response = chatClient.prompt()
        .user(prompt)
        .call()
        .chatResponse();

String model = response.getMetadata().getModel();
Usage usage = response.getMetadata().getUsage();

if(!response.

getResult().

getOutput().

getToolCalls().

isEmpty()){
        response.

getResult().

getOutput().

getToolCalls()
        .

forEach(call ->log.

info("Tool call: {}",call.getName()));
        }
```

Retry on throttling while surfacing errors for observability:

```java
try{
        return response.getResult().

getOutput().

getContent();
}catch(
OpenAiApiException ex){
        if(ex.statusCode ==429){
        Thread.

sleep(1000);
        return chatClient.

prompt().

user(prompt).

call().

content();
    }
            throw ex;
}
```

### 4.4 Default Configuration

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultSystem("""
                You are VoyagerMate, an expert travel assistant.
                Offer precise logistics, costs, and accessibility insights.
                """)
        .defaultTools(new WeatherTool(), new CurrencyConverterTool())
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new SimpleLoggerAdvisor())
        .build();
```

Defaults keep tone, tooling, and memory consistent across requests while still letting individual prompts override
parameters.

---

## 5. Multimodal Interactions

### 5.1 Image Analysis

GPT-4o-style models accept images alongside text, enabling visual travel insights.[^4]

```java
var media = new Media(MimeTypeUtils.IMAGE_JPEG,
        new ClassPathResource("/images/vacation.jpg"));

var userMessage = UserMessage.builder()
        .text("What destination is this and what should I do nearby?")
        .media(List.of(media))
        .build();

ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
        OpenAiChatOptions.builder().model("gpt-4o").build()));
```

Attach multiple `Media` objects to compare photos or enrich context.

### 5.2 Audio Input and Output

```java
var audioMessage = new UserMessage(
        "Process this travel note",
        List.of(new Media(MimeTypeUtils.parseMimeType("audio/mp3"), audioResource)));

ChatResponse transcription = chatModel.call(new Prompt(List.of(audioMessage),
        OpenAiChatOptions.builder()
                .model(OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW)
                .build()));
```

Generate speech by requesting audio output alongside text:

```java
ChatResponse response = chatModel.call(new Prompt(List.of(new UserMessage(
        "Describe the best walking route through Paris")),
        OpenAiChatOptions.builder()
                .model(OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW)
                .outputModalities(List.of("text", "audio"))
                .outputAudio(new AudioParameters(Voice.ALLOY, AudioResponseFormat.WAV))
                .build()));

String transcript = response.getResult().getOutput().getContent();
byte[] audioBytes = response.getResult().getOutput().getMedia().get(0).getDataAsByteArray();
```

The response contains both transcript and audio. Select from preset voices (Alloy, Echo, Fable, Onyx, Nova, Shimmer) to
align with your product experience.[^4]

---

## 6. Structured Outputs

### 6.1 Why Structured Outputs Matter

Prompting for JSON is unreliable—models often add prose or omit fields. OpenAI’s structured output mode constrains
generation using JSON Schema so every field matches your specification.[^5]

```java
record VoyagerItinerary(
        @JsonProperty(required = true) String destination,
        @JsonProperty(required = true) String startDate,
        @JsonProperty(required = true) String endDate,
        @JsonProperty(required = true) List<Day> days,
        @JsonProperty(required = true) Budget totalBudget) {

    record Day(
            @JsonProperty(required = true) String date,
            @JsonProperty(required = true) String theme,
            @JsonProperty(required = true) List<Activity> activities,
            @JsonProperty(required = true) String meals,
            @JsonProperty(required = true) double estimatedCost) {
    }

    record Activity(
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) String time,
            @JsonProperty(required = true) String location,
            @JsonProperty(required = true) String description,
            @JsonProperty(required = true) double cost,
            @JsonProperty(required = true) String transportationDetails) {
    }
}

var converter = new BeanOutputConverter<>(VoyagerItinerary.class);
Prompt prompt = new Prompt(userPrompt,
        OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .responseFormat(new ResponseFormat(
                        ResponseFormat.Type.JSON_SCHEMA,
                        converter.getJsonSchema()))
                .build());

ChatResponse response = chatModel.call(prompt);
VoyagerItinerary itinerary = converter.convert(
        response.getResult().getOutput().getContent());
```

Use lower temperatures (0.0–0.3) for deterministic JSON and mark fields `required=true` so the constrained decoder knows
they must be present.

---

## 7. Tool Calling with Spring AI

### 7.1 Why Tools Matter

LLMs cannot access live weather, reservations, or private customer data. Tool calling lets the model request application
functions whenever it needs extra information or actions.[^6]

### 7.2 Annotating Java Methods with `@Tool`

```java

@Component
class VoyagerTools {

    @Tool(description = "Return must-see attractions for a city")
    String findAttractions(
            @ToolParam(description = "Destination city") String destination,
            @ToolParam(description = "Traveller interests") List<String> interests) {
        return attractionService.topChoices(destination, interests);
    }

    @Tool(description = "Estimate per-day travel budget")
    String estimateBudget(
            @ToolParam(description = "Destination city") String destination,
            @ToolParam(description = "Number of travel days") int days,
            @ToolParam(description = "Budget level: budget|balanced|luxury") String level) {
        return budgetService.estimate(destination, days, level);
    }
}
```

Register tools once so every prompt can use them:

```java

@Bean
ChatClient chatClient(ChatClient.Builder builder, VoyagerTools tools) {
    return builder.defaultTools(tools).build();
}
```

Detailed descriptions and parameter metadata help the model decide when to call each function and how to supply
arguments. Spring AI logs tool invocations when `logging.level.org.springframework.ai=DEBUG`.

### 7.3 Tool Context and Direct Returns

Use `ToolContext` to pass hidden operational data (tenant IDs, request IDs) that should not live in the model prompt.
Set `returnDirect = true` when the tool output should bypass the model—for example, returning a generated PDF itinerary
or RAG search snippet directly to the caller.[^6]

---

## 8. Bringing Data to the Model

Spring AI supports three complementary strategies:

1. **Fine-tuning** – retrain the model with domain-specific data. Expensive and rarely necessary for itinerary planning
   but powerful for niche terminology.[^6]
2. **Retrieval-Augmented Generation (RAG)** – embed documents, store them in a vector database, and inject the top
   semantic matches into the prompt. This “prompt stuffing” approach keeps prompts concise while grounding answers in
   current knowledge.[^1]
    - Extract → split into coherent chunks → embed → store.
    - At query time, retrieve via vector similarity → compose augmented prompt → call the model.
3. **Tool calling** – access external APIs (weather, flights, loyalty data) and take actions (bookings, notifications)
   in real time.

VoyagerMate typically combines all three: model priors for general travel knowledge, RAG for curated guides, and tools
for live data.

---

## 9. Advisors: Cross-Cutting Behaviours

Advisors are Spring AI interceptors that modify prompts or responses before they reach the provider.[^1]

- `QuestionAnswerAdvisor` attaches RAG results from a `VectorStore`.
- `MessageChatMemoryAdvisor` stores conversational history.
- `SimpleLoggerAdvisor` prints final prompts, responses, tools, and token metrics.

Ordering matters: add memory first, retrieval second, logging last.

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                QuestionAnswerAdvisor.builder(vectorStore).build(),
                new SimpleLoggerAdvisor())
        .build();
```

---

## 10. Putting the Patterns Together

### 10.1 Conversational Concierge

```java

@RestController
class ChatController {

    @PostMapping("/api/chat")
    Map<String, String> chat(@RequestBody String userMessage,
                             @RequestHeader("Session-ID") String sessionId) {
        String reply = chatClient.prompt()
                .advisors(advisor -> advisor.param(
                        AdvisorConstants.CHAT_MEMORY_CONVERSATION_ID_KEY,
                        sessionId))
                .user(userMessage)
                .call()
                .content();

        return Map.of("response", reply);
    }
}
```

### 10.2 Image-to-Itinerary Insight

```java

@PostMapping("/api/analyze-photo")
DestinationAnalysis analyzePhoto(@RequestParam("file") MultipartFile photo) {

    var media = new Media(MimeTypeUtils.IMAGE_JPEG, photo.getResource());

    var message = UserMessage.builder()
            .text("""
                    Analyse this travel photo.
                    Identify the location and suggest similar destinations,
                    realistic costs, and best times to visit.
                    {format}
                    """)
            .media(List.of(media))
            .build();

    return chatClient.prompt()
            .user(message)
            .system(sp -> sp.param("format", converter.getFormat()))
            .options(OpenAiChatOptions.builder().model("gpt-4o").build())
            .call()
            .entity(DestinationAnalysis.class);
}
```

### 10.3 Orchestrated Itinerary Generation

```java

@Bean
ChatClient itineraryGenerator(ChatClient.Builder builder,
                              VectorStore travelGuideStore,
                              VoyagerTools tools) {
    return builder
            .defaultSystem("""
                    You are VoyagerMate's itinerary specialist.
                    Produce realistic plans with precise logistics,
                    budgets, accessibility insights, and cultural etiquette tips.
                    Use tools to fetch weather, convert currencies, and search flights.
                    """)
            .defaultAdvisors(QuestionAnswerAdvisor.builder(travelGuideStore).build())
            .defaultTools(tools)
            .build();
}
```

With these ingredients—well-structured prompts, typed outputs, tool integration, and advisors—you can deliver reliable,
multimodal travel experiences in a pure-Java stack.

---

## References

[^1]: Spring AI ChatClient Reference – https://docs.spring.io/spring-ai/reference/api/chatclient.html
[^2]: Spring AI Prompt & Message Reference – https://docs.spring.io/spring-ai/reference/api/prompt.html
[^3]: Anthropic, *Effective Context Engineering for AI
Agents* – https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
[^4]: Spring AI OpenAI Chat (Images & Audio) – https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
[^5]: OpenAI Structured Outputs Guide – https://platform.openai.com/docs/guides/structured-outputs
[^6]: Spring AI Tools API & Function Calling – https://docs.spring.io/spring-ai/reference/api/tools.html
