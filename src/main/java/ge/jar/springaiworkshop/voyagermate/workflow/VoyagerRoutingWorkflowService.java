package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.RoutingWorkflowResult;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.VoyagerIntent;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VoyagerRoutingWorkflowService {

    private final IntentClassifier classifier;
    private final IntentResponder responder;

    @Autowired
    public VoyagerRoutingWorkflowService(ChatClient chatClient) {
        this(new ChatClientIntentClassifier(chatClient), new ChatClientIntentResponder(chatClient));
    }

    VoyagerRoutingWorkflowService(IntentClassifier classifier, IntentResponder responder) {
        this.classifier = classifier;
        this.responder = responder;
    }

    public RoutingWorkflowResult route(String travellerPrompt, TripPlanRequest request) {
        Objects.requireNonNull(travellerPrompt, "Traveller prompt must not be null");
        var context = baseContext(request);
        var decision = classifier.classify(travellerPrompt);
        var response = responder.respond(decision.intent(), travellerPrompt, context);
        return new RoutingWorkflowResult(decision.intent(), decision.rationale(), response);
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

    interface IntentClassifier {
        IntentDecision classify(String prompt);
    }

    interface IntentResponder {
        String respond(VoyagerIntent intent, String prompt, String context);
    }

    record IntentDecision(VoyagerIntent intent, String rationale) {
    }

    static final class ChatClientIntentClassifier implements IntentClassifier {

        private final ChatClient chatClient;
        private final BeanOutputConverter<IntentDecision> converter;
        private final String systemPrompt;

        ChatClientIntentClassifier(ChatClient chatClient) {
            this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
            this.converter = new BeanOutputConverter<>(IntentDecision.class);
            this.systemPrompt = """
                    You classify travel support requests into intents.
                    Allowed intents: CONCIERGE, BOOKING_CHANGE, TRAVEL_RISK.
                    Respond using this JSON schema:
                    %s
                    """.formatted(converter.getFormat());
        }

        @Override
        public IntentDecision classify(String prompt) {
            var response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();
            assert response != null;
            return converter.convert(response);
        }
    }

    static final class ChatClientIntentResponder implements IntentResponder {

        private final ChatClient chatClient;

        ChatClientIntentResponder(ChatClient chatClient) {
            this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
        }

        @Override
        public String respond(VoyagerIntent intent, String prompt, String context) {
            var userPayload = prompt + "\n---\n" + context;
            return chatClient.prompt()
                    .system(systemFor(intent))
                    .user(userPayload)
                    .call()
                    .content();
        }

        private String systemFor(VoyagerIntent intent) {
            return switch (intent) {
                case BOOKING_CHANGE -> "You are VoyagerMate handling booking changes. "
                        + "Return numbered remediation steps plus a short escalation note.";
                case TRAVEL_RISK -> "You are VoyagerMate acting as a travel risk analyst. "
                        + "Provide risk tiers, mitigation, and when to alert a human.";
                case CONCIERGE -> "You are VoyagerMate concierge. "
                        + "Offer creative ideas while confirming assumptions before acting.";
            };
        }
    }
}


