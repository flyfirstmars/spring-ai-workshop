package ge.jar.springaiworkshop.voyagermate.shell;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ge.jar.springaiworkshop.voyagermate.core.ConversationService;
import ge.jar.springaiworkshop.voyagermate.core.TravelDocumentService;
import ge.jar.springaiworkshop.voyagermate.core.VoyagerMateService;
import ge.jar.springaiworkshop.voyagermate.model.AudioChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ChatResponsePayload;
import ge.jar.springaiworkshop.voyagermate.model.ConversationHistory;
import ge.jar.springaiworkshop.voyagermate.model.ImageChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ItineraryPlan;
import ge.jar.springaiworkshop.voyagermate.model.ParallelWorkflowSummary;
import ge.jar.springaiworkshop.voyagermate.model.RefinementResult;
import ge.jar.springaiworkshop.voyagermate.model.RoutingWorkflowResult;
import ge.jar.springaiworkshop.voyagermate.model.TextChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripWorkflowSummary;
import ge.jar.springaiworkshop.voyagermate.model.WorkerWorkflowSummary;
import ge.jar.springaiworkshop.voyagermate.workflow.ItineraryRefinementWorkflowService;
import ge.jar.springaiworkshop.voyagermate.workflow.ItineraryWorkflowService;
import ge.jar.springaiworkshop.voyagermate.workflow.MultiAgentOrchestratorService;
import ge.jar.springaiworkshop.voyagermate.workflow.OrchestratorWorkersWorkflowService;
import ge.jar.springaiworkshop.voyagermate.workflow.ParallelItineraryWorkflowService;
import ge.jar.springaiworkshop.voyagermate.workflow.VoyagerRoutingWorkflowService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class VoyagerMateCommands {

    private final VoyagerMateService voyagerMateService;
    private final ConversationService conversationService;
    private final TravelDocumentService travelDocumentService;
    private final ItineraryWorkflowService itineraryWorkflowService;
    private final ParallelItineraryWorkflowService parallelItineraryWorkflowService;
    private final VoyagerRoutingWorkflowService voyagerRoutingWorkflowService;
    private final ItineraryRefinementWorkflowService itineraryRefinementWorkflowService;
    private final OrchestratorWorkersWorkflowService orchestratorWorkersWorkflowService;
    private final MultiAgentOrchestratorService multiAgentOrchestratorService;
    private final ObjectWriter jsonWriter;

    public VoyagerMateCommands(VoyagerMateService voyagerMateService,
                               ConversationService conversationService,
                               TravelDocumentService travelDocumentService,
                               ItineraryWorkflowService itineraryWorkflowService,
                               ParallelItineraryWorkflowService parallelItineraryWorkflowService,
                               VoyagerRoutingWorkflowService voyagerRoutingWorkflowService,
                               ItineraryRefinementWorkflowService itineraryRefinementWorkflowService,
                               OrchestratorWorkersWorkflowService orchestratorWorkersWorkflowService,
                               MultiAgentOrchestratorService multiAgentOrchestratorService,
                               ObjectMapper objectMapper) {
        this.voyagerMateService = voyagerMateService;
        this.conversationService = conversationService;
        this.travelDocumentService = travelDocumentService;
        this.itineraryWorkflowService = itineraryWorkflowService;
        this.parallelItineraryWorkflowService = parallelItineraryWorkflowService;
        this.voyagerRoutingWorkflowService = voyagerRoutingWorkflowService;
        this.itineraryRefinementWorkflowService = itineraryRefinementWorkflowService;
        this.orchestratorWorkersWorkflowService = orchestratorWorkersWorkflowService;
        this.multiAgentOrchestratorService = multiAgentOrchestratorService;
        this.jsonWriter = objectMapper.copy()
                .findAndRegisterModules()
                .writerWithDefaultPrettyPrinter();
    }

    @ShellMethod(key = "chat", value = "Chat with VoyagerMate using a text prompt.")
    public String chat(@ShellOption(help = "Prompt for VoyagerMate") String prompt) {
        try {
            var stream = voyagerMateService.streamChat(new TextChatRequest(prompt));

            stream.chunks()
                    .doOnNext(chunk -> {
                        System.out.print(chunk);
                        System.out.flush();
                    })
                    .blockLast();

            System.out.println();

            var payload = stream.completion().block();
            if (payload == null) {
                return "Model: azure-openai" + System.lineSeparator()
                        + "Latency: 0 ms";
            }
            return formatChatResponse(payload, false);
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "describe-image", value = "Send an image and prompt to VoyagerMate.")
    public String describeImage(
            @ShellOption(value = {"-f", "--file"}, help = "Path to the image file") String imagePath,
            @ShellOption(value = {"-p", "--prompt"}, defaultValue = "Spot travel inspiration from this image", help = "Prompt to guide VoyagerMate") String prompt
    ) {
        try {
            var imagePathResolved = resolvePath(imagePath);
            var base64 = encodeFile(imagePathResolved);
            var mimeType = probeMimeType(imagePathResolved, "image/jpeg");
            var stream = voyagerMateService.streamAnalyzeImage(new ImageChatRequest(prompt, base64, mimeType));

            stream.chunks()
                    .doOnNext(chunk -> {
                        System.out.print(chunk);
                        System.out.flush();
                    })
                    .blockLast();

            System.out.println();

            var payload = stream.completion().block();
            if (payload == null) {
                return "Model: azure-openai" + System.lineSeparator()
                        + "Latency: 0 ms";
            }
            return formatChatResponse(payload, false);
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "transcribe-audio", value = "Send a recorded note to VoyagerMate for transcription and advice.")
    public String transcribeAudio(
            @ShellOption(value = {"-f", "--file"}, help = "Path to the audio file") String audioPath,
            @ShellOption(value = {"-p", "--prompt"}, defaultValue = "What did the traveller mention in this recording?", help = "Prompt to guide VoyagerMate") String prompt
    ) {
        var audioPathResolved = resolvePath(audioPath);
        var base64 = encodeFile(audioPathResolved);
        var mimeType = probeMimeType(audioPathResolved, "audio/mpeg");
        var response = voyagerMateService.interpretAudio(new AudioChatRequest(prompt, base64, mimeType));
        return formatChatResponse(response);
    }

    @ShellMethod(key = "plan-itinerary", value = "Generate a structured itinerary with VoyagerMate.")
    public String planItinerary(
            @ShellOption(value = {"-n", "--name"}, defaultValue = ShellOption.NULL, help = "Traveller name") String traveller,
            @ShellOption(value = {"-o", "--origin"}, defaultValue = ShellOption.NULL, help = "Origin city") String origin,
            @ShellOption(value = {"-d", "--destination"}, help = "Destination city") String destination,
            @ShellOption(value = {"--depart"}, defaultValue = ShellOption.NULL, help = "Departure date (YYYY-MM-DD)") String depart,
            @ShellOption(value = {"--return"}, defaultValue = ShellOption.NULL, help = "Return date (YYYY-MM-DD)") String ret,
            @ShellOption(value = {"-b", "--budget"}, defaultValue = ShellOption.NULL, help = "Budget focus (e.g. budget, balanced, premium)") String budget,
            @ShellOption(value = {"-i", "--interests"}, defaultValue = ShellOption.NULL, help = "Comma separated traveller interests") String interests
    ) {
        try {
            var request = buildTripPlanRequest(traveller, origin, destination, depart, ret, budget, interests);
            ItineraryPlan plan = voyagerMateService.planTrip(request);
            return toJson(plan);
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "workflow", value = "Run the VoyagerMate workflow orchestrator for a trip plan.")
    public String orchestrateWorkflow(
            @ShellOption(value = {"-n", "--name"}, defaultValue = ShellOption.NULL, help = "Traveller name") String traveller,
            @ShellOption(value = {"-o", "--origin"}, defaultValue = ShellOption.NULL, help = "Origin city") String origin,
            @ShellOption(value = {"-d", "--destination"}, help = "Destination city") String destination,
            @ShellOption(value = {"--depart"}, defaultValue = ShellOption.NULL, help = "Departure date (YYYY-MM-DD)") String depart,
            @ShellOption(value = {"--return"}, defaultValue = ShellOption.NULL, help = "Return date (YYYY-MM-DD)") String ret,
            @ShellOption(value = {"-b", "--budget"}, defaultValue = ShellOption.NULL, help = "Budget focus") String budget,
            @ShellOption(value = {"-i", "--interests"}, defaultValue = ShellOption.NULL, help = "Comma separated traveller interests") String interests
    ) {
        var request = buildTripPlanRequest(traveller, origin, destination, depart, ret, budget, interests);
        TripWorkflowSummary summary = itineraryWorkflowService.orchestrate(request);
        return toJson(summary);
    }

    @ShellMethod(key = "parallel-insights", value = "Run VoyagerMate's parallel research workflow.")
    public String runParallelWorkflow(
            @ShellOption(value = {"-n", "--name"}, defaultValue = ShellOption.NULL, help = "Traveller name") String traveller,
            @ShellOption(value = {"-o", "--origin"}, defaultValue = ShellOption.NULL, help = "Origin city") String origin,
            @ShellOption(value = {"-d", "--destination"}, help = "Destination city") String destination,
            @ShellOption(value = {"--depart"}, defaultValue = ShellOption.NULL, help = "Departure date (YYYY-MM-DD)") String depart,
            @ShellOption(value = {"--return"}, defaultValue = ShellOption.NULL, help = "Return date (YYYY-MM-DD)") String ret,
            @ShellOption(value = {"-b", "--budget"}, defaultValue = ShellOption.NULL, help = "Budget focus") String budget,
            @ShellOption(value = {"-i", "--interests"}, defaultValue = ShellOption.NULL, help = "Comma separated traveller interests") String interests
    ) {
        var request = buildTripPlanRequest(traveller, origin, destination, depart, ret, budget, interests);
        ParallelWorkflowSummary summary = parallelItineraryWorkflowService.orchestrate(request);
        return toJson(summary);
    }

    @ShellMethod(key = "route-intent", value = "Route a traveller prompt to the best VoyagerMate workflow.")
    public String routeIntent(
            @ShellOption(value = {"-p", "--prompt"}, help = "Traveller request to classify") String prompt,
            @ShellOption(value = {"-n", "--name"}, defaultValue = ShellOption.NULL, help = "Traveller name") String traveller,
            @ShellOption(value = {"-o", "--origin"}, defaultValue = ShellOption.NULL, help = "Origin city") String origin,
            @ShellOption(value = {"-d", "--destination"}, defaultValue = ShellOption.NULL, help = "Destination city") String destination,
            @ShellOption(value = {"--depart"}, defaultValue = ShellOption.NULL, help = "Departure date (YYYY-MM-DD)") String depart,
            @ShellOption(value = {"--return"}, defaultValue = ShellOption.NULL, help = "Return date (YYYY-MM-DD)") String ret,
            @ShellOption(value = {"-b", "--budget"}, defaultValue = ShellOption.NULL, help = "Budget focus") String budget,
            @ShellOption(value = {"-i", "--interests"}, defaultValue = ShellOption.NULL, help = "Comma separated traveller interests") String interests
    ) {
        var request = buildTripPlanRequest(traveller, origin, destination, depart, ret, budget, interests);
        RoutingWorkflowResult result = voyagerRoutingWorkflowService.route(prompt, request);
        return toJson(result);
    }

    @ShellMethod(key = "refine-itinerary", value = "Iteratively refine a draft itinerary with evaluator feedback.")
    public String refineItinerary(
            @ShellOption(value = {"-p", "--prompt"}, help = "Creative brief or traveller instructions") String prompt,
            @ShellOption(value = {"-n", "--name"}, defaultValue = ShellOption.NULL, help = "Traveller name") String traveller,
            @ShellOption(value = {"-o", "--origin"}, defaultValue = ShellOption.NULL, help = "Origin city") String origin,
            @ShellOption(value = {"-d", "--destination"}, defaultValue = ShellOption.NULL, help = "Destination city") String destination,
            @ShellOption(value = {"--depart"}, defaultValue = ShellOption.NULL, help = "Departure date (YYYY-MM-DD)") String depart,
            @ShellOption(value = {"--return"}, defaultValue = ShellOption.NULL, help = "Return date (YYYY-MM-DD)") String ret,
            @ShellOption(value = {"-b", "--budget"}, defaultValue = ShellOption.NULL, help = "Budget focus") String budget,
            @ShellOption(value = {"-i", "--interests"}, defaultValue = ShellOption.NULL, help = "Comma separated traveller interests") String interests
    ) {
        var request = buildTripPlanRequest(traveller, origin, destination, depart, ret, budget, interests);
        RefinementResult result = itineraryRefinementWorkflowService.refine(prompt, request);
        return toJson(result);
    }

    @ShellMethod(key = "orchestrator-workers", value = "Run the orchestrator-workers agentic workflow.")
    public String orchestrateWorkers(
            @ShellOption(value = {"-p", "--prompt"}, help = "High-level traveller brief") String prompt,
            @ShellOption(value = {"-n", "--name"}, defaultValue = ShellOption.NULL, help = "Traveller name") String traveller,
            @ShellOption(value = {"-o", "--origin"}, defaultValue = ShellOption.NULL, help = "Origin city") String origin,
            @ShellOption(value = {"-d", "--destination"}, defaultValue = ShellOption.NULL, help = "Destination city") String destination,
            @ShellOption(value = {"--depart"}, defaultValue = ShellOption.NULL, help = "Departure date (YYYY-MM-DD)") String depart,
            @ShellOption(value = {"--return"}, defaultValue = ShellOption.NULL, help = "Return date (YYYY-MM-DD)") String ret,
            @ShellOption(value = {"-b", "--budget"}, defaultValue = ShellOption.NULL, help = "Budget focus") String budget,
            @ShellOption(value = {"-i", "--interests"}, defaultValue = ShellOption.NULL, help = "Comma separated traveller interests") String interests
    ) {
        var request = buildTripPlanRequest(traveller, origin, destination, depart, ret, budget, interests);
        WorkerWorkflowSummary summary = orchestratorWorkersWorkflowService.orchestrate(prompt, request);
        return toJson(summary);
    }

    @ShellMethod(key = "multi-agent-plan", value = "Plan a trip using a team of AI agents.")
    public String multiAgentPlan(@ShellOption(help = "Describe your trip request") String request) {
        return multiAgentOrchestratorService.planTrip(request);
    }

    // ==================== Session 3: Chat Memory Commands ====================

    @ShellMethod(key = "chat-session", value = "Chat with VoyagerMate using persistent session memory.")
    public String chatSession(
            @ShellOption(value = {"-s", "--session"}, help = "Session ID for conversation tracking") String sessionId,
            @ShellOption(value = {"-p", "--prompt"}, help = "Message to send") String prompt
    ) {
        try {
            var response = conversationService.chat(sessionId, prompt);
            return formatSessionChatResponse(sessionId, response);
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "show-history", value = "Display conversation history for a session.")
    public String showHistory(
            @ShellOption(value = {"-s", "--session"}, help = "Session ID to retrieve history for") String sessionId
    ) {
        try {
            ConversationHistory history = conversationService.getHistory(sessionId);
            if (history.isEmpty()) {
                return "No conversation history found for session: " + sessionId;
            }
            return formatConversationHistory(history);
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "clear-history", value = "Clear conversation history for a session.")
    public String clearHistory(
            @ShellOption(value = {"-s", "--session"}, help = "Session ID to clear history for") String sessionId
    ) {
        try {
            int messageCount = conversationService.getMessageCount(sessionId);
            conversationService.clearHistory(sessionId);
            return "Cleared " + messageCount + " messages from session: " + sessionId;
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "list-sessions", value = "List all active conversation sessions.")
    public String listSessions() {
        try {
            var sessions = conversationService.listSessions();
            if (sessions.isEmpty()) {
                return "No active conversation sessions found.";
            }
            var builder = new StringBuilder("Active Sessions (" + sessions.size() + "):\n");
            for (var session : sessions) {
                int count = conversationService.getMessageCount(session);
                builder.append("  - ").append(session)
                        .append(" (").append(count).append(" messages)\n");
            }
            return builder.toString().trim();
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    private String formatSessionChatResponse(String sessionId, ChatResponsePayload payload) {
        return "Session: " + sessionId + System.lineSeparator() +
                "Model: " + payload.model() + System.lineSeparator() +
                "Latency: " + payload.latencyMs() + " ms" +
                System.lineSeparator() + System.lineSeparator() +
                payload.reply();
    }

    // ==================== Session 3: ETL Pipeline Commands ====================

    @ShellMethod(key = "load-guides", value = "Load and process travel guide documents.")
    public String loadGuides() {
        try {
            int guideCount = travelDocumentService.loadTravelGuides();
            int destCount = travelDocumentService.loadDestinationData();
            int total = travelDocumentService.getDocumentCount();

            return String.format(
                    "Documents loaded successfully!%n" +
                            "  Travel guides: %d chunks%n" +
                            "  Destination data: %d documents%n" +
                            "  Total documents: %d%n%n" +
                            "Use 'show-documents' to view loaded documents or 'search-docs' to search.",
                    guideCount, destCount, total
            );
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "show-documents", value = "Display loaded documents with metadata.")
    public String showDocuments(
            @ShellOption(value = {"-t", "--type"}, defaultValue = ShellOption.NULL,
                    help = "Filter by source type: markdown or json") String sourceType,
            @ShellOption(value = {"-l", "--limit"}, defaultValue = "10",
                    help = "Maximum documents to display") int limit
    ) {
        try {
            var docs = sourceType != null
                    ? travelDocumentService.getDocumentsByType(sourceType)
                    : travelDocumentService.getLoadedDocuments();

            if (docs.isEmpty()) {
                return "No documents loaded. Run 'load-guides' first.";
            }

            var builder = new StringBuilder();
            builder.append("Loaded Documents (").append(docs.size()).append(" total)")
                    .append(System.lineSeparator())
                    .append("─".repeat(60))
                    .append(System.lineSeparator());

            int shown = 0;
            for (var doc : docs) {
                if (shown >= limit) {
                    builder.append(System.lineSeparator())
                            .append("... and ").append(docs.size() - limit).append(" more documents");
                    break;
                }

                builder.append(System.lineSeparator())
                        .append("[Document ").append(shown + 1).append("]")
                        .append(System.lineSeparator());

                // Show metadata
                var metadata = doc.getMetadata();
                builder.append("  Source: ").append(metadata.getOrDefault("source_type", "unknown"))
                        .append(System.lineSeparator());
                if (metadata.containsKey("file_name")) {
                    builder.append("  File: ").append(metadata.get("file_name"))
                            .append(System.lineSeparator());
                }
                if (metadata.containsKey("destination_name")) {
                    builder.append("  Destination: ").append(metadata.get("destination_name"))
                            .append(System.lineSeparator());
                }
                if (metadata.containsKey("category")) {
                    builder.append("  Category: ").append(metadata.get("category"))
                            .append(System.lineSeparator());
                }

                // Show content preview (first 200 chars)
                String content = doc.getText();
                assert content != null;
                String preview = content.length() > 200
                        ? content.substring(0, 200) + "..."
                        : content;
                builder.append("  Content: ").append(preview.replace("\n", " "))
                        .append(System.lineSeparator());

                shown++;
            }

            return builder.toString();
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "search-docs", value = "Search loaded documents by keyword.")
    public String searchDocs(
            @ShellOption(value = {"-q", "--query"}, help = "Search query") String query
    ) {
        try {
            if (travelDocumentService.getDocumentCount() == 0) {
                return "No documents loaded. Run 'load-guides' first.";
            }

            var results = travelDocumentService.searchDocuments(query);

            if (results.isEmpty()) {
                return "No documents found matching: " + query;
            }

            var builder = new StringBuilder();
            builder.append("Found ").append(results.size()).append(" document(s) matching '")
                    .append(query).append("'")
                    .append(System.lineSeparator())
                    .append("─".repeat(60))
                    .append(System.lineSeparator());

            int shown = 0;
            for (var doc : results) {
                if (shown >= 5) {
                    builder.append(System.lineSeparator())
                            .append("... and ").append(results.size() - 5).append(" more results");
                    break;
                }

                builder.append(System.lineSeparator())
                        .append("[Result ").append(shown + 1).append("]")
                        .append(System.lineSeparator());

                var metadata = doc.getMetadata();
                builder.append("  Source: ").append(metadata.getOrDefault("source_type", "unknown"));
                if (metadata.containsKey("destination_name")) {
                    builder.append(" | ").append(metadata.get("destination_name"));
                }
                builder.append(System.lineSeparator());

                // Show content with query highlighted (simple approach)
                String content = doc.getText();
                assert content != null;
                String preview = content.length() > 300
                        ? content.substring(0, 300) + "..."
                        : content;
                builder.append("  ").append(preview.replace("\n", " "))
                        .append(System.lineSeparator());

                shown++;
            }

            builder.append(System.lineSeparator())
                    .append("Note: This is keyword search. Use RAG (Session 4) for semantic search.");

            return builder.toString();
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    @ShellMethod(key = "clear-docs", value = "Clear all loaded documents from memory.")
    public String clearDocs() {
        try {
            int count = travelDocumentService.getDocumentCount();
            travelDocumentService.clearDocuments();
            return "Cleared " + count + " documents from memory.";
        } catch (Exception ex) {
            return handleException(ex);
        }
    }

    private String formatConversationHistory(ConversationHistory history) {
        var builder = new StringBuilder()
                .append("Session: ").append(history.sessionId())
                .append(" (").append(history.messageCount()).append(" messages)")
                .append(System.lineSeparator())
                .append("─".repeat(50))
                .append(System.lineSeparator());

        for (var message : history.messages()) {
            String role = switch (message.type()) {
                case "USER" -> "You";
                case "ASSISTANT" -> "VoyagerMate";
                case "SYSTEM" -> "System";
                default -> message.type();
            };
            builder.append("[").append(role).append("]").append(System.lineSeparator())
                    .append(message.content()).append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        return builder.toString().trim();
    }

    private String formatChatResponse(ChatResponsePayload payload) {
        return formatChatResponse(payload, true);
    }

    private String formatChatResponse(ChatResponsePayload payload, boolean includeReply) {
        var builder = new StringBuilder()
                .append("Model: ")
                .append(payload.model())
                .append(System.lineSeparator());

        if (!payload.toolCalls().isEmpty()) {
            builder.append("ToolCall: ")
                    .append(String.join(", ", payload.toolCalls()))
                    .append(System.lineSeparator());
        }

        builder.append("Latency: ")
                .append(payload.latencyMs())
                .append(" ms");

        if (includeReply) {
            builder.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(payload.reply());
        }

        return builder.toString();
    }

    private String encodeFile(Path path) {
        try {
            var bytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read file: " + path, ex);
        }
    }

    private String probeMimeType(Path path, String fallback) {
        try {
            return Objects.requireNonNullElse(Files.probeContentType(path), fallback);
        } catch (IOException ex) {
            return fallback;
        }
    }

    private TripPlanRequest buildTripPlanRequest(String traveller,
                                                 String origin,
                                                 String destination,
                                                 String depart,
                                                 String ret,
                                                 String budget,
                                                 String interests) {
        return new TripPlanRequest(
                traveller,
                origin,
                destination,
                parseDate(depart),
                parseDate(ret),
                budget,
                parseList(interests)
        );
    }

    private LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
    }

    private List<String> parseList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split("\\s*,\\s*"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private Path resolvePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("File path must not be blank");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private String toJson(Object value) {
        try {
            return jsonWriter.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render JSON", ex);
        }
    }

    private String handleException(Exception ex) {
        return VoyagerMateExceptionHandler.handleGenericError(ex);
    }
}
