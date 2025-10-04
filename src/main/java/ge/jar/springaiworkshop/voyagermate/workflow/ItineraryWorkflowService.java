package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.TripWorkflowSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ItineraryWorkflowService {

    private final ChatClient chatClient;

    public ItineraryWorkflowService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public TripWorkflowSummary orchestrate(TripPlanRequest request) {
        var interests = request.interests() == null || request.interests().isEmpty()
                ? "unspecified"
                : String.join(", ", request.interests());

        var baseContext = "Traveller: %s\nFrom: %s\nTo: %s\nDates: %s to %s\nBudget focus: %s\nInterests: %s".formatted(
                request.travellerName(),
                request.originCity(),
                request.destinationCity(),
                request.departureDate(),
                request.returnDate(),
                request.budgetFocus(),
                interests
        );

        var discovery = runStep("Summarise the traveller's key goals, constraints, and any clarifications needed.", baseContext);
        var itineraryDraft = runStep("Propose a three-part travel storyline (arrival, middle, farewell) with bullet itineraries.", baseContext);
        var riskReview = runStep("List major risks (weather, visas, health, budget) and mitigations.", baseContext);
        var nextSteps = runStep("Provide a concise next-step checklist for the traveller and the agent.", baseContext);

        return new TripWorkflowSummary(discovery, itineraryDraft, riskReview, nextSteps);
    }

    private String runStep(String instruction, String context) {
        return chatClient.prompt()
                .system("You are orchestrating a travel-planning workflow. Complete the requested step succinctly.")
                .user(instruction + "\n---\n" + context)
                .call()
                .content();
    }
}
