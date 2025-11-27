package ge.jar.springaiworkshop.config;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {

    /**
     * Primary stateless ChatClient for single-turn interactions.
     * Used by existing commands like 'chat', 'describe-image', etc.
     */
    @Bean
    @Primary
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

    /**
     * Memory-enabled ChatClient for multi-turn conversations.
     * Maintains conversation history via MessageChatMemoryAdvisor backed by JDBC storage.
     * Used by session-based commands like 'chat-session'.
     */
    @Bean
    @Qualifier("conversationalChatClient")
    public ChatClient conversationalChatClient(ChatClient.Builder builder,
                                               ChatMemory chatMemory,
                                               @Value("${spring.ai.azure.openai.chat.options.deployment-name:gpt-4o}") String deploymentName) {
        return builder
                .defaultSystem("You are VoyagerMate, an upbeat travel planning copilot. "
                        + "Offer practical suggestions, consider budget and accessibility, and always clarify unknowns before acting. "
                        + "Reference previous messages in this conversation when relevant.")
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(deploymentName)
                        .maxCompletionTokens(1024)
                        .temperature(1.0)
                        .build())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
