package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.RefinementResult;
import ge.jar.springaiworkshop.voyagermate.model.RefinementRound;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ItineraryRefinementWorkflowService {

    private static final int MAX_ITERATIONS = 3;

    private final DraftGenerator generator;
    private final DraftEvaluator evaluator;

    @Autowired
    public ItineraryRefinementWorkflowService(ChatClient chatClient) {
        this(new ChatClientDraftGenerator(chatClient), new ChatClientDraftEvaluator(chatClient));
    }

    ItineraryRefinementWorkflowService(DraftGenerator generator, DraftEvaluator evaluator) {
        this.generator = Objects.requireNonNull(generator, "DraftGenerator must not be null");
        this.evaluator = Objects.requireNonNull(evaluator, "DraftEvaluator must not be null");
    }

    public RefinementResult refine(String travellerBrief, TripPlanRequest request) {
        Objects.requireNonNull(travellerBrief, "Traveller brief must not be null");
        var context = baseContext(request);
        var rounds = new ArrayList<RefinementRound>();

        String reviewerNotes = null;
        String latestDraft = null;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            latestDraft = generator.generate(travellerBrief, context, reviewerNotes);
            var feedback = evaluator.evaluate(latestDraft, context, iteration);

            rounds.add(new RefinementRound(iteration, latestDraft, feedback.feedback(), feedback.accepted()));
            if (feedback.accepted()) {
                break;
            }
            reviewerNotes = feedback.feedback();
        }

        if (latestDraft == null) {
            latestDraft = "";
        }

        return new RefinementResult(latestDraft, List.copyOf(rounds));
    }

    private String baseContext(TripPlanRequest request) {
        if (request == null) {
            return "Traveller context: unspecified.";
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

    interface DraftGenerator {
        String generate(String travellerBrief, String context, String reviewerNotes);
    }

    interface DraftEvaluator {
        EvaluationFeedback evaluate(String draft, String context, int iteration);
    }

    record EvaluationFeedback(boolean accepted, String feedback) {
    }

    static final class ChatClientDraftGenerator implements DraftGenerator {

        private final ChatClient chatClient;

        ChatClientDraftGenerator(ChatClient chatClient) {
            this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
        }

        @Override
        public String generate(String travellerBrief, String context, String reviewerNotes) {
            var userPayload = new StringBuilder()
                    .append("Brief:\n")
                    .append(travellerBrief)
                    .append("\n\nContext:\n")
                    .append(context);

            if (StringUtils.hasText(reviewerNotes)) {
                userPayload.append("\n\nReviewer notes to address:\n").append(reviewerNotes);
            }

            return chatClient.prompt()
                    .system("""
                            You are VoyagerMate's senior itinerary copywriter.
                            Deliver two vivid paragraphs followed by a concise bullet list of booking moves.
                            If reviewer notes are provided, address them before expanding.
                            """)
                    .user(userPayload.toString())
                    .call()
                    .content();
        }
    }

    static final class ChatClientDraftEvaluator implements DraftEvaluator {

        private final ChatClient chatClient;
        private final BeanOutputConverter<EvaluationFeedback> converter;
        private final String systemPrompt;

        ChatClientDraftEvaluator(ChatClient chatClient) {
            this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
            this.converter = new BeanOutputConverter<>(EvaluationFeedback.class);
            this.systemPrompt = """
                    You are VoyagerMate's editorial reviewer.
                    Rate drafts for clarity, safety, and actionable guidance.
                    Respond using this JSON schema:
                    %s
                    """.formatted(converter.getFormat());
        }

        @Override
        public EvaluationFeedback evaluate(String draft, String context, int iteration) {
            var response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("""
                            Iteration: %d
                            Draft:
                            %s
                            
                            Traveller context:
                            %s
                            """.formatted(iteration, draft, context))
                    .call()
                    .content();
            assert response != null;
            return converter.convert(response);
        }
    }
}


