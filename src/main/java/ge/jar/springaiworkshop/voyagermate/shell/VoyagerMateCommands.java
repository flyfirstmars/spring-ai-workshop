package ge.jar.springaiworkshop.voyagermate.shell;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ge.jar.springaiworkshop.voyagermate.core.VoyagerMateService;
import ge.jar.springaiworkshop.voyagermate.model.AudioChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ChatResponsePayload;
import ge.jar.springaiworkshop.voyagermate.model.ImageChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ItineraryPlan;
import ge.jar.springaiworkshop.voyagermate.model.TextChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripWorkflowSummary;
import ge.jar.springaiworkshop.voyagermate.workflow.ItineraryWorkflowService;
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
    private final ItineraryWorkflowService itineraryWorkflowService;
    private final ObjectWriter jsonWriter;

    public VoyagerMateCommands(VoyagerMateService voyagerMateService,
                               ItineraryWorkflowService itineraryWorkflowService,
                               ObjectMapper objectMapper) {
        this.voyagerMateService = voyagerMateService;
        this.itineraryWorkflowService = itineraryWorkflowService;
        this.jsonWriter = objectMapper.copy()
                .findAndRegisterModules()
                .writerWithDefaultPrettyPrinter();
    }

    @ShellMethod(key = "chat", value = "Chat with VoyagerMate using a text prompt.")
    public String chat(@ShellOption(help = "Prompt for VoyagerMate") String prompt) {
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
    }

    @ShellMethod(key = "describe-image", value = "Send an image and prompt to VoyagerMate.")
    public String describeImage(
            @ShellOption(value = {"-f", "--file"}, help = "Path to the image file") String imagePath,
            @ShellOption(value = {"-p", "--prompt"}, defaultValue = "Spot travel inspiration from this image", help = "Prompt to guide VoyagerMate") String prompt
    ) {
        var imagePathResolved = resolvePath(imagePath);
        var base64 = encodeFile(imagePathResolved);
        var mimeType = probeMimeType(imagePathResolved, "image/jpeg");
        var response = voyagerMateService.analyzeImage(new ImageChatRequest(prompt, base64, mimeType));
        return formatChatResponse(response);
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
        var request = new TripPlanRequest(
                traveller,
                origin,
                destination,
                parseDate(depart),
                parseDate(ret),
                budget,
                parseList(interests)
        );

        ItineraryPlan plan = voyagerMateService.planTrip(request);
        return toJson(plan);
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
        var request = new TripPlanRequest(
                traveller,
                origin,
                destination,
                parseDate(depart),
                parseDate(ret),
                budget,
                parseList(interests)
        );

        TripWorkflowSummary summary = itineraryWorkflowService.orchestrate(request);
        return toJson(summary);
    }

    private String formatChatResponse(ChatResponsePayload payload) {
        return formatChatResponse(payload, true);
    }

    private String formatChatResponse(ChatResponsePayload payload, boolean includeReply) {
        var builder = new StringBuilder()
                .append("Model: ")
                .append(payload.model())
                .append(System.lineSeparator())
                .append("Latency: ")
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
}
