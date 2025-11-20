package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.WorkerFinding;
import ge.jar.springaiworkshop.voyagermate.model.WorkerWorkflowSummary;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorWorkersWorkflowService {

    private static final ExecutorService WORKER_EXECUTOR =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    private final TaskPlanner planner;
    private final WorkerExecutor workerExecutor;
    private final SynthesisAgent synthesisAgent;

    @Autowired
    public OrchestratorWorkersWorkflowService(ChatClient chatClient) {
        this(new ChatClientTaskPlanner(chatClient),
                new ChatClientWorkerExecutor(chatClient),
                new ChatClientSynthesisAgent(chatClient));
    }

    OrchestratorWorkersWorkflowService(TaskPlanner planner,
                                       WorkerExecutor workerExecutor,
                                       SynthesisAgent synthesisAgent) {
        this.planner = Objects.requireNonNull(planner, "TaskPlanner must not be null");
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "WorkerExecutor must not be null");
        this.synthesisAgent = Objects.requireNonNull(synthesisAgent, "SynthesisAgent must not be null");
    }

    public WorkerWorkflowSummary orchestrate(String travellerBrief, TripPlanRequest request) {
        Objects.requireNonNull(travellerBrief, "Traveller brief must not be null");
        var context = baseContext(request);

        var plan = planner.plan(travellerBrief, context);
        var findings = executeWorkers(plan.tasks(), context);
        var actionPlan = synthesisAgent.synthesise(plan.analysis(), findings, context);

        return new WorkerWorkflowSummary(plan.analysis(), findings, actionPlan);
    }

    private List<WorkerFinding> executeWorkers(List<WorkerTask> tasks, String context) {
        var futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> workerExecutor.execute(task, context), WORKER_EXECUTOR))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private String baseContext(TripPlanRequest request) {
        if (request == null) {
            return "No itinerary metadata supplied.";
        }
        var interests = (request.interests() == null || request.interests().isEmpty())
                ? "unspecified"
                : String.join(", ", request.interests());
        return """
                Traveller: %s
                Route: %s to %s
                Dates: %s to %s
                Budget: %s
                Interests: %s
                """.formatted(
                defaultValue(request.travellerName(), "Guest Traveller"),
                defaultValue(request.originCity(), "Unknown"),
                defaultValue(request.destinationCity(), "Unknown"),
                formatDate(request.departureDate()),
                formatDate(request.returnDate()),
                defaultValue(request.budgetFocus(), "flexible"),
                interests
        );
    }

    private String defaultValue(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "unscheduled" : date.toString();
    }

    interface TaskPlanner {
        Plan plan(String travellerBrief, String context);
    }

    interface WorkerExecutor {
        WorkerFinding execute(WorkerTask task, String context);
    }

    interface SynthesisAgent {
        String synthesise(String analysis, List<WorkerFinding> findings, String context);
    }

    record Plan(String analysis, List<WorkerTask> tasks) {
    }

    record WorkerTask(String role, String focus, String instruction) {
    }

    static final class ChatClientTaskPlanner implements TaskPlanner {

        private final ChatClient chatClient;
        private final BeanOutputConverter<Plan> converter;
        private final String systemPrompt;

        ChatClientTaskPlanner(ChatClient chatClient) {
            this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
            this.converter = new BeanOutputConverter<>(Plan.class);
            this.systemPrompt = """
                    You are VoyagerMate's task orchestrator.
                    Analyse the traveller brief and propose 2-4 worker tasks.
                    Respond using this JSON schema:
                    %s
                    """.formatted(converter.getFormat());
        }

        @Override
        public Plan plan(String travellerBrief, String context) {
            var response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("""
                            Traveller brief:
                            %s
                            
                            Trip context:
                            %s
                            """.formatted(travellerBrief, context))
                    .call()
                    .content();
            assert response != null;
            return converter.convert(response);
        }
    }

    static final class ChatClientWorkerExecutor implements WorkerExecutor {

        private final ChatClient chatClient;

        ChatClientWorkerExecutor(ChatClient chatClient) {
            this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
        }

        @Override
        public WorkerFinding execute(WorkerTask task, String context) {
            var response = chatClient.prompt()
                    .system("""
                            You are a specialised VoyagerMate worker focused on %s.
                            Return concise bullet points with concrete tips.
                            """.formatted(task.focus()))
                    .user("""
                            Role: %s
                            Instruction: %s
                            
                            Trip context:
                            %s
                            """.formatted(task.role(), task.instruction(), context))
                    .call()
                    .content();
            return new WorkerFinding(task.role(), task.focus(), response);
        }
    }

    static final class ChatClientSynthesisAgent implements SynthesisAgent {

        private final ChatClient chatClient;

        ChatClientSynthesisAgent(ChatClient chatClient) {
            this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
        }

        @Override
        public String synthesise(String analysis, List<WorkerFinding> findings, String context) {
            var findingsText = findings.stream()
                    .map(finding -> "- %s (%s): %s".formatted(finding.role(), finding.focus(), finding.output()))
                    .collect(Collectors.joining("\n"));

            return chatClient.prompt()
                    .system("""
                            You are VoyagerMate's orchestrator summarising worker results.
                            Deliver a short action plan plus callouts for human follow-up.
                            """)
                    .user("""
                            Orchestrator analysis:
                            %s
                            
                            Worker findings:
                            %s
                            
                            Traveller context:
                            %s
                            """.formatted(analysis, findingsText, context))
                    .call()
                    .content();
        }
    }
}


