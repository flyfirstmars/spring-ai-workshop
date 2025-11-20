package ge.jar.springaiworkshop.config;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("${spring.ai.azure.openai.chat.options.deployment-name:gpt-4o}") String deploymentName) {
        return builder
                .defaultSystem("You are VoyagerMate, an upbeat travel planning copilot. "
                        + "Offer practical suggestions, consider budget and accessibility, and always clarify unknowns before acting.")
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(deploymentName)
                        .maxCompletionTokens(1024)
                        .temperature(1.0)
                        .build())
                .build();
    }
}
