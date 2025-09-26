package ge.jar.springaiworkshop.voyagermate.shell;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ge.jar.springaiworkshop.voyagermate.model.AudioChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ChatResponsePayload;
import ge.jar.springaiworkshop.voyagermate.model.ImageChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ItineraryPlan;
import ge.jar.springaiworkshop.voyagermate.model.TextChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripWorkflowSummary;
import ge.jar.springaiworkshop.voyagermate.core.VoyagerMateService;
import ge.jar.springaiworkshop.voyagermate.workflow.ItineraryWorkflowService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;

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
        var response = voyagerMateService.chat(new TextChatRequest(prompt));
        return formatChatResponse(response);
    }

    @ShellMethod(key = "describe-image", value = "Send an image and prompt to VoyagerMate.")
    public String describeImage(
        @ShellOption(value = {"-f", "--file"}, help = "Path to the image file") Path imagePath,
        @ShellOption(value = {"-p", "--prompt"}, defaultValue = "Spot travel inspiration from this image", help = "Prompt to guide VoyagerMate") String prompt
    ) {
        var base64 = encodeFile(imagePath);
        var mimeType = probeMimeType(imagePath, "image/jpeg");
        var response = voyagerMateService.analyzeImage(new ImageChatRequest(prompt, base64, mimeType));
        return formatChatResponse(response);
    }

    @ShellMethod(key = "transcribe-audio", value = "Send a recorded note to VoyagerMate for transcription and advice.")
    public String transcribeAudio(
        @ShellOption(value = {"-f", "--file"}, help = "Path to the audio file") Path audioPath,
        @ShellOption(value = {"-p", "--prompt"}, defaultValue = "What did the traveller mention in this recording?", help = "Prompt to guide VoyagerMate") String prompt
    ) {
        var base64 = encodeFile(audioPath);
        var mimeType = probeMimeType(audioPath, "audio/mpeg");
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
        return "Model: " + payload.model() + System.lineSeparator()
            + "Latency: " + payload.latencyMs() + " ms" + System.lineSeparator()
            + System.lineSeparator()
            + payload.reply();
    }

    private String encodeFile(Path path) {
        try {
            var bytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(bytes);
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read file: " + path, ex);
        }
    }

    private String probeMimeType(Path path, String fallback) {
        try {
            return Objects.requireNonNullElse(Files.probeContentType(path), fallback);
        }
        catch (IOException ex) {
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

    private String toJson(Object value) {
        try {
            return jsonWriter.writeValueAsString(value);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render JSON", ex);
        }
    }
}
