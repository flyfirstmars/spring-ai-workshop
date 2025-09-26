package ge.jar.springaiworkshop.voyagermate.core;

import ge.jar.springaiworkshop.voyagermate.model.AudioChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ChatResponsePayload;
import ge.jar.springaiworkshop.voyagermate.model.ImageChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.ItineraryPlan;
import ge.jar.springaiworkshop.voyagermate.model.TextChatRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.tools.VoyagerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class VoyagerMateService {

    private final ChatClient chatClient;
    private final VoyagerTools voyagerTools;

    public VoyagerMateService(ChatClient chatClient, VoyagerTools voyagerTools) {
        this.chatClient = chatClient;
        this.voyagerTools = voyagerTools;
    }

    public ChatResponsePayload chat(TextChatRequest request) {
        return executePrompt(() -> chatClient.prompt()
            .user(request.prompt())
            .call());
    }

    public ChatResponsePayload analyzeImage(ImageChatRequest request) {
        var media = Media.builder()
            .mimeType(resolveMime(request.mimeType(), MimeTypeUtils.IMAGE_JPEG))
            .data(decode(request.imageBase64()))
            .build();
        var userMessage = UserMessage.builder()
            .text(request.prompt())
            .media(media)
            .build();
        return executePrompt(() -> chatClient.prompt()
            .messages(userMessage)
            .system("Describe the travel-relevant details of the provided image before giving suggestions.")
            .call());
    }

    public ChatResponsePayload interpretAudio(AudioChatRequest request) {
        var media = Media.builder()
            .mimeType(resolveMime(request.mimeType(), MimeTypeUtils.parseMimeType("audio/mpeg")))
            .data(decode(request.audioBase64()))
            .build();
        var userMessage = UserMessage.builder()
            .text(request.prompt())
            .media(media)
            .build();
        return executePrompt(() -> chatClient.prompt()
            .messages(userMessage)
            .system("Transcribe the traveller's note, clarify uncertainties, then suggest next booking actions.")
            .call());
    }

    public ItineraryPlan planTrip(TripPlanRequest request) {
        var converter = new BeanOutputConverter<>(ItineraryPlan.class);
        var system = """
            You are VoyagerMate creating a personalised travel itinerary. Use the tool outputs when helpful.
            Return the plan using this JSON schema:
            %s
            """.formatted(converter.getFormat());

        var interests = request.interests() == null || request.interests().isEmpty()
            ? "unspecified"
            : String.join(", ", request.interests());

        var dateRange = formatDate(request.departureDate()) + " to " + formatDate(request.returnDate());

        var userPrompt = "Plan a journey based on these preferences:\n" +
            "Traveller: " + defaultValue(request.travellerName(), "Guest Traveller") + "\n" +
            "Origin: " + defaultValue(request.originCity(), "Unknown") + "\n" +
            "Destination: " + defaultValue(request.destinationCity(), "Unknown") + "\n" +
            "Dates: " + dateRange + "\n" +
            "Budget level: " + defaultValue(request.budgetFocus(), "flexible") + "\n" +
            "Interests: " + interests;

        var response = chatClient.prompt()
            .system(system)
            .tools(voyagerTools)
            .user(userPrompt)
            .call();

        return converter.convert(response.content());
    }

    private ChatResponsePayload executePrompt(ResponseSupplier supplier) {
        var started = Instant.now();
        var spec = supplier.get();
        var latency = Duration.between(started, Instant.now()).toMillis();
        var chatResponse = spec.chatResponse();
        var metadata = chatResponse.getMetadata();
        var model = metadata != null && metadata.getModel() != null ? metadata.getModel() : "azure-openai";
        return new ChatResponsePayload(spec.content(), model, latency);
    }

    private MimeType resolveMime(String value, MimeType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return MimeTypeUtils.parseMimeType(value);
    }

    private byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private String defaultValue(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "unscheduled" : date.toString();
    }

    @FunctionalInterface
    private interface ResponseSupplier {
        ChatClient.CallResponseSpec get();
    }
}
