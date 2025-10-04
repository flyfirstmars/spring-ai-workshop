# Designing Agentic Travel Experiences with Spring AI

Anthropic’s engineering research stresses a key insight for agent builders: effective systems emerge from deliberate information architecture, not from single clever prompts.[^3] Context is a finite resource; each token consumes attention budget and eventually causes “context rot” if left unmanaged. This guide captures the theoretical foundations and practical patterns you can apply in VoyagerMate to evolve from deterministic workflows to adaptive agents using Spring AI.

---

## 1. Agents and Workflows: Distinct Architectures

### 1.1 Deterministic Workflows

Workflows are code-driven sequences that call LLMs at specific points but keep control in your application. They mirror Spring service orchestration: you define the order, perform validation, and integrate external APIs.

```java
TripWorkflowSummary summary = itineraryWorkflowService.orchestrate(request);
```

Use workflows when:
- Steps are predictable (parse → validate → call APIs → format response).
- Determinism, auditability, and latency are more important than creativity.
- Failures should be handled in conventional code paths.

### 1.2 Autonomous Agents

Agents let the model decide which tools to call, in what order, and when work is complete. They operate inside a loop, incorporating environmental feedback (API responses, code execution results, user clarifications) before choosing the next action.

```java
while (!context.complete() && context.iterations() < MAX_ITERS) {
    AgentAction action = agentClient.prompt()
        .system(systemPrompt)
        .messages(context.toMessages())
        .tools(availableTools)
        .call()
        .entity(AgentAction.class);

    ActionResult result = execute(action);
    context.record(result);

    if (result.requiresUserDecision()) {
        break; // surface back to the user for guidance
    }
}
```

Reserve true agents for open-ended goals (e.g., “plan a relaxing European vacation that fits a €4,000 budget”) where you cannot predict the necessary steps ahead of time.[^3]

### 1.3 Start Simple, Escalate Deliberately

Anthropic emphasises a staged approach:[^3]
1. Optimise single LLM calls with retrieval and examples.
2. Introduce workflows when deterministic multi-step logic adds value.
3. Promote to agent loops only when required by problem complexity.

---

## 2. Five Foundational Workflow Patterns

These patterns compose like software design patterns—master each in isolation before combining.

### 2.1 Prompt Chaining

Break a task into sequential calls where each step validates the previous output.

```java
Outline outline = chatClient.prompt().user(request).call().entity(Outline.class);
ValidationResult validation = validator.validate(outline);
if (!validation.valid()) { outline = retryWithFeedback(outline, validation); }
DetailedItinerary itinerary = writer.create(outline);
```

Use when tasks naturally decompose and intermediate checks improve reliability.

### 2.2 Routing

Classify input, then hand off to specialised handlers or model configurations.

```java
QueryType type = classifier.prompt().user(rawQuestion).call().entity(QueryType.class);
return switch (type) {
    case SIMPLE -> fastClient.handle(rawQuestion);
    case COMPLEX -> premiumClient.handle(rawQuestion);
    case REFUND -> refundService.handle(rawQuestion);
};
```

Ideal when different categories benefit from tailored prompts, tools, or cost profiles.

### 2.3 Parallelisation

Run independent prompts concurrently and aggregate results programmatically.

```java
CompletableFuture<HotelOptions> hotels = supplyAsync(() -> searchHotels(city));
CompletableFuture<ActivityIdeas> activities = supplyAsync(() -> planActivities(city));
return combine(hotels.join(), activities.join());
```

Sectioning focuses each prompt on a single concern; voting repeats the same task multiple times to build consensus or surface disagreements.

### 2.4 Orchestrator–Workers

An orchestrator LLM decides which subtasks are needed, delegates to worker prompts, and synthesises their outputs. Unlike parallelisation, subtasks are not predetermined—they emerge from analysis of the specific request.

### 2.5 Evaluator–Optimizer Loop

One LLM generates output; another critiques it. Repeat until quality thresholds are met or maximum iterations are reached.

```java
for (int i = 0; i < MAX_REVIEWS; i++) {
    Draft draft = generator.generate(spec);
    Feedback feedback = evaluator.review(draft);
    if (feedback.accepted()) return draft;
    spec = spec.withReviewerNotes(feedback);
}
```

Apply when iterative refinement measurably improves quality (e.g., nuanced itinerary descriptions).

---

## 3. Anatomy of a Production Agent

A robust agent loop contains:
1. **Task intake** – the user request and any system context.
2. **Planning** – optional scratchpad where the agent outlines steps (extended thinking).
3. **Action selection** – choose a tool or respond directly.
4. **Execution** – call the selected tool or produce text.
5. **Result evaluation** – assess tool output, update state, decide next step.
6. **Human checkpoint** – pause for clarification when needed.
7. **Termination** – succeed, fail gracefully, or reach safety limits.

Guard rails include iteration caps, execution sandboxes, exception handling, and clearly defined “ask the human” triggers.[^3]

---

## 4. Context Engineering for Agents

### 4.1 System Prompts at the Right Altitude

Craft system messages that communicate principles without brittle rule lists. Structure with sections like `<role>`, `<guidelines>`, `<examples>`. Provide 3–5 canonical examples covering diverse trip types so the model learns heuristically.[^3]

### 4.2 Tool Design as Interface Design

Treat tools like public APIs:
- Keep responsibilities narrow—avoid overlapping functions.
- Use explicit parameter names (`departure_airport_code`, `budget_per_day`).
- Return paginated, filtered results instead of dumping raw datasets.
- Provide actionable error messages (“No availability July 14–16; consider shifting dates ±2 days”).
- Poka‑yoke the interface: design inputs so misuse becomes difficult (e.g., require absolute paths, enforce enum values for cabin class).

Example tool definition:

```java
@Tool(description = "Search flights with optional filters")
FlightSearchResult searchFlights(
    @ToolParam("IATA origin code") String origin,
    @ToolParam("IATA destination code") String destination,
    @ToolParam("Departure date (YYYY-MM-DD)") LocalDate departure,
    @ToolParam(value = "Return date", required = false) LocalDate returns,
    @ToolParam(value = "Maximum stops", required = false) Integer maxStops) {
    return flightService.search(origin, destination, departure, returns, maxStops);
}
```

### 4.3 Just-in-Time Retrieval

Favor lightweight identifiers in context and fetch detailed data via tools when needed. Store hotel IDs, not full descriptions. Provide `getHotelDetails(hotelId)` for deeper inspection. This mirrors human workflows: bookmark first, investigate when relevant.[^3]

### 4.4 Extended Thinking

Allow the agent to write a scratchpad (“thinking” blocks) before acting. Anthropic strips previous thinking from the active context window, letting the model plan without consuming tokens unnecessarily.[^3]

---

## 5. Managing Long-Horizon Tasks

### 5.1 Compaction and Summaries

Monitor token usage. When approaching limits, summarise prior dialogue and clear stale tool outputs. Simple optimisation—discarding old tool results—delivered a 29 % performance gain in Anthropic’s internal tests.[^3]

### 5.2 External Memory

Persist critical facts outside the prompt (e.g., NOTES.md, JSON state). On resume, read memory back in. Spring implementations can store memory via Spring Data repositories or file services exposed as tools.

```java
@Tool(description = "Persist trip decision")
void saveDecision(@ToolParam("Session ID") UUID sessionId,
                  @ToolParam("Decision type") String type,
                  @ToolParam("Payload") Map<String, Object> payload) {
    decisionStore.save(sessionId, type, payload);
}
```

Anthropic reports a 39 % lift combining memory tools with context editing.[^3]

### 5.3 Multi-Agent Collaboration

Deploy an orchestrator agent that spawns specialised sub-agents (hotels, flights, activities). Each sub-agent maintains focused context and returns concise summaries. Anthropic observed >90 % improvement on complex research tasks using Claude Opus as lead and Claude Sonnet as specialists, albeit with ~15× token cost.[^3]

For information hand-off, let sub-agents persist large artefacts (documents, JSON payloads) via tools, returning only lightweight references to the orchestrator. This keeps cross-agent communication cheap while preserving fidelity.

Use Spring’s `TaskExecutor` or virtual threads to run sub-agents concurrently, aggregating results when all complete.

---

## 6. Optimising Context Windows and Cost

- **Prompt caching**: Cache static system prompts to reduce latency and token charges.[^1]
- **Context editing**: Automate removal of old tool results, maintaining relevance throughout long sessions and reducing token consumption by up to 84 % in Anthropic’s long-horizon evaluations.[^3]
- **Token-aware tools**: Enforce pagination and size limits to prevent output floods.[^6]
- **Cache-aware rate limiting**: Remember that cached prompt reads often bypass rate limits on supported providers.[^1]

---

## 7. Implementation in Spring AI

### 7.1 Mapping Patterns to Spring Components

| Pattern | Spring AI / Java Support |
|---------|-------------------------|
| Prompt chaining | Sequential service methods invoking `ChatClient`; validate outputs with Jakarta Bean Validation. |
| Routing | LLM or rules-based classifier feeding a `switch` that selects dedicated `ChatClient` instances. |
| Parallelisation | `CompletableFuture`, `@Async`, or virtual threads for concurrent prompts. |
| Orchestrator-workers | Primary `ChatClient` for planning, worker beans for delegated tasks, coordinated via `TaskExecutor`. |
| Evaluator-optimizer | Loop managed in code with two `ChatClient` configurations (generator vs evaluator). |
| Memory tools | Spring Data repositories exposed through `@Tool` methods. |
| Monitoring | Spring Boot Actuator metrics, Micrometer timers, structured logging. |

### 7.2 Advisors and Beyond

Combine advisors to inject retrieval, memory, and logging:[^1]

```java
ChatClient agentClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),
        QuestionAnswerAdvisor.builder(vectorStore).build(),
        new SimpleLoggerAdvisor())
    .defaultTools(new VoyagerTools())
    .build();
```

Use AOP or filters to monitor token budgets and trigger compaction routines when thresholds are crossed.

---

## 8. Decision Framework for VoyagerMate

| Scenario | Recommended Pattern |
|----------|--------------------|
| Extract simple data (dates, destinations) | Single LLM call with examples |
| Validate booking request end-to-end | Prompt chaining workflow |
| Route booking vs refund vs concierge queries | Routing with specialised prompts |
| Research multiple cities simultaneously | Parallelisation |
| Update complex itinerary where required changes vary | Orchestrator–worker |
| Refine destination description with style checks | Evaluator–optimizer |
| Plan vague multi-week vacation | Agent loop with memory and human checkpoints |

Start with deterministic implementations; layer in autonomy as evaluation results justify the added complexity and cost.

---

## 9. Anti-Patterns to Avoid

- **Monolithic system prompts** packed with nested if/else logic or, conversely, vague “be helpful” guidance.[^3]
- **Bloated tool collections** with overlapping responsibilities or ambiguous naming.
- **Dumping entire datasets** into context instead of just-in-time retrieval.
- **Single-context mega agents** for tasks that could be partitioned into specialist sub-agents.
- **Opaque frameworks** that hide underlying prompts and responses, making debugging impossible—understand what runs under the hood before abstracting.[^3]

---

## 10. Applying the Principles in VoyagerMate

1. **Begin with workflows** for booking confirmation, budget estimation, and itinerary generation. Measure success before increasing scope.
2. **Design lean tools** (flight search, hotel search, visa requirements, budget calculation) with robust validation and token-efficient outputs.
3. **Adopt just-in-time retrieval** for user profiles, past bookings, and hotel metadata via dedicated tools rather than bloating prompts.
4. **Persist critical decisions** to external memory so itineraries survive context resets and cross-session handoffs.
5. **Introduce parallel research** for multi-city trips using `CompletableFuture` to gather hotels, transport, and activities concurrently.
6. **Promote to orchestrator–worker** for complex itinerary changes where required subtasks depend on user input.
7. **Pilot evaluator–optimizer loops** for high-touch content like concierge narratives where quality matters more than latency.
8. **Evaluate continuously** with scenario-based tests (budget solo travel, accessible family trip, last-minute business itinerary). Track token usage, completion time, and accuracy to guide further optimisation.

---

## 11. Performance Optimisation and Evaluation

- **Quantified gains**: Anthropic recorded 29 % improvement from aggressive context editing, 39 % from combining memory tools with context editing, and >90 % from multi-agent orchestration on complex research tasks—use these as benchmarks when introducing similar features in VoyagerMate.[^3]
- **Token governance**: Build a `TokenBudgetService` that records prompt and completion tokens per conversation, triggers compaction when thresholds are crossed, and reports usage to observability backends. Prompt caching can reduce per-call latency by up to 85 % on frequently reused context.[^1]
- **Evaluation-first mindset**: Create automated eval suites spanning core user journeys (refund request, multi-city luxury trip, budget backpacking, accessibility-focused itinerary). Measure accuracy, latency, tool usage counts, and fail-fast behaviours before and after each change.
- **Experiment instrumentation**: Persist agent traces (prompt, response, tool arguments/results, thinking blocks) for offline analysis. Use this data to refine system prompts, tool descriptions, and retrieval strategies.

---

## 12. Engineering Practices for Spring AI Agents

- **Concurrency**: Use `@Async`, `CompletableFuture`, or Java virtual threads to parallelise independent LLM calls and tool invocations while keeping threads lightweight.
- **Resilience**: Wrap external tool calls with Spring Cloud Circuit Breaker to protect against upstream outages; provide graceful fallbacks to the agent when data is unavailable.
- **Caching & state**: Employ Spring Cache or Redis for frequently accessed metadata (airport lists, popular hotels). Cache system prompts and tool definitions to minimise serialization overhead.
- **Observability**: Expose Micrometer metrics (latency, token counts, tool error rates) and structured logs capturing agent decisions. Trace multi-agent interactions with correlation IDs so you can reconstruct execution flows.
- **Testing**: Unit test individual tools, contract test integrations, and run integration tests on orchestration pipelines using Spring Boot Test. Maintain regression evals to ensure new prompts or tools do not degrade prior scenarios.
- **Framework awareness**: High-level libraries can accelerate development but may obscure raw prompts/responses. Inspect underlying calls regularly so debugging remains tractable.[^3]

---

## 13. Summary

Effective agent systems arise from disciplined context management, clean tool design, and systematic evaluation. Start with deterministic workflows, layer in retrieval and structured outputs, and graduate to autonomous agents only when the problem truly warrants it. Treat every token as a scarce resource, engineer tools with the same care as public APIs, and rely on measurements—not intuition—to decide when to introduce new patterns. With these practices, VoyagerMate can evolve from prompt-driven chat to a resilient, multi-modal travel concierge built entirely atop Spring AI.

---

## References

[^1]: Spring AI ChatClient and Advisors – https://docs.spring.io/spring-ai/reference/api/chatclient.html
[^3]: Anthropic, *Effective Context Engineering for AI Agents* – https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
[^6]: Spring AI Tools API – https://docs.spring.io/spring-ai/reference/api/tools.html
