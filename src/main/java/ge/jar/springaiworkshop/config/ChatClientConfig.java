package ge.jar.springaiworkshop.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("gpt-4o-mini") String deploymentName) {
        return builder
                .defaultSystem("You are VoyagerMate, an upbeat travel planning copilot. "
                        + "Offer practical suggestions, consider budget and accessibility, and always clarify unknowns before acting.")
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(deploymentName)
                        .build())
                .build();
    }
}
