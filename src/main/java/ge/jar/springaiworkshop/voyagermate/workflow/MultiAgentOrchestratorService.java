package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.tools.TravelAgentTools;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class MultiAgentOrchestratorService {

    private final ChatClient orchestratorClient;

    public MultiAgentOrchestratorService(ChatClient.Builder builder, TravelAgentTools travelAgentTools) {
        this.orchestratorClient = builder
                .defaultSystem("You are the Lead Travel Orchestrator. "
                        + "Your goal is to create a comprehensive travel plan by consulting your team of experts. "
                        + "Break down the user's request and call the appropriate expert tools (Logistics, Accommodation, Activity) to get details. "
                        + "Synthesize their responses into a final cohesive itinerary.")
                .defaultTools(travelAgentTools)
                .build();
    }

    public String planTrip(String userRequest) {
        var response = orchestratorClient.prompt()
                .user(userRequest)
                .call()
                .content();
        return Objects.requireNonNullElse(response, "I'm sorry, I couldn't generate a plan at this time.");
    }
}

