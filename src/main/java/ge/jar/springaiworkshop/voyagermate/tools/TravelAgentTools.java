package ge.jar.springaiworkshop.voyagermate.tools;

import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TravelAgentTools {

    private final ChatClient.Builder chatClientBuilder;

    public TravelAgentTools(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @Tool(name = "ask_logistics_expert", description = "Consult the travel logistics expert for flights, trains, and transfers")
    public String askLogisticsExpert(@ToolParam(description = "The specific logistics question or request") String query) {
        var response = chatClientBuilder.build().prompt()
                .system("You are a Travel Logistics Expert. Focus on routes, times, transit modes, and practical transfer details. Be precise.")
                .user(query)
                .call()
                .content();
        return Objects.requireNonNullElse(response, "No response from logistics expert.");
    }

    @Tool(name = "ask_accommodation_expert", description = "Consult the accommodation expert for hotels, areas to stay, and lodging advice")
    public String askAccommodationExpert(@ToolParam(description = "The specific accommodation question or request") String query) {
        var response = chatClientBuilder.build().prompt()
                .system("You are a Hospitality & Accommodation Expert. Suggest specific neighborhoods and hotel types (boutique, luxury, budget) matching the traveller's style.")
                .user(query)
                .call()
                .content();
        return Objects.requireNonNullElse(response, "No response from accommodation expert.");
    }

    @Tool(name = "ask_activity_expert", description = "Consult the local activity expert for things to do, culture, and dining")
    public String askActivityExpert(@ToolParam(description = "The specific activity or cultural question") String query) {
        var response = chatClientBuilder.build().prompt()
                .system("You are a Local Experience Guide. Focus on cultural immersion, dining, hidden gems, and must-see attractions.")
                .user(query)
                .call()
                .content();
        return Objects.requireNonNullElse(response, "No response from activity expert.");
    }
}

