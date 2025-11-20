package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.ParallelWorkflowSummary;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ParallelItineraryWorkflowService {

    private static final ExecutorService VIRTUAL_EXECUTOR =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    private final ChatClient chatClient;

    public ParallelItineraryWorkflowService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ParallelWorkflowSummary orchestrate(TripPlanRequest request) {
        return buildSummary(request, this::runStep);
    }

    ParallelWorkflowSummary buildSummary(TripPlanRequest request, StepRunner runner) {
        Objects.requireNonNull(request, "Trip plan request must not be null");
        var context = baseContext(request);
        var started = Instant.now();

        var lodgingFuture = runInParallel(() -> runner.run(lodgingInstruction(), context));
        var diningFuture = runInParallel(() -> runner.run(diningInstruction(), context));
        var logisticsFuture = runInParallel(() -> runner.run(logisticsInstruction(), context));
        var cultureFuture = runInParallel(() -> runner.run(cultureInstruction(), context));

        CompletableFuture.allOf(lodgingFuture, diningFuture, logisticsFuture, cultureFuture).join();

        var latency = Duration.between(started, Instant.now()).toMillis();
        return new ParallelWorkflowSummary(
                lodgingFuture.join(),
                diningFuture.join(),
                logisticsFuture.join(),
                cultureFuture.join(),
                latency
        );
    }

    private CompletableFuture<String> runInParallel(Supplier<String> supplier) {
        return CompletableFuture.supplyAsync(supplier, VIRTUAL_EXECUTOR);
    }

    private String baseContext(TripPlanRequest request) {
        var interests = (request.interests() == null || request.interests().isEmpty())
                ? "unspecified"
                : String.join(", ", request.interests());

        return """
                Traveller: %s
                From: %s
                To: %s
                Dates: %s to %s
                Budget focus: %s
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

    private String runStep(String instruction, String context) {
        return chatClient.prompt()
                .system("You are VoyagerMate running concurrent research tracks. "
                        + "Respond with concise bullet lists focused on actionable travel guidance.")
                .user(instruction + "\n---\n" + context)
                .call()
                .content();
    }

    private String lodgingInstruction() {
        return "Produce 3 lodging strategies (boutique, mid-range, splurge) including neighbourhood pros/cons.";
    }

    private String diningInstruction() {
        return "Highlight dining hits: breakfast staples, lunch-on-the-go, evening tasting experiences.";
    }

    private String logisticsInstruction() {
        return "Summarise transport + budget watchpoints (local transit, day-trip transfers, passes).";
    }

    private String cultureInstruction() {
        return "Suggest cultural immersion moves (festivals, community tours, mindful etiquette reminders).";
    }

    private String defaultValue(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "unscheduled" : date.toString();
    }

    @FunctionalInterface
    interface StepRunner {
        String run(String instruction, String context);
    }
}


