package ge.jar.springaiworkshop.voyagermate.core;

import ge.jar.springaiworkshop.voyagermate.model.ChatResponsePayload;
import ge.jar.springaiworkshop.voyagermate.model.ConversationHistory;
import ge.jar.springaiworkshop.voyagermate.model.ConversationMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static ge.jar.springaiworkshop.voyagermate.util.VirtualThreadExecutor.execute;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for managing conversational chat sessions with persistent memory.
 * <p>
 * Uses Spring AI's ChatMemory API backed by PostgreSQL to maintain
 * conversation context across multiple interactions within a session.
 */
@Service
public class ConversationService {

    private final ChatClient conversationalChatClient;
    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;
    private final JdbcTemplate jdbcTemplate;

    public ConversationService(
            @Qualifier("conversationalChatClient") ChatClient conversationalChatClient,
            ChatMemory chatMemory,
            ChatMemoryRepository chatMemoryRepository,
            JdbcTemplate jdbcTemplate) {
        this.conversationalChatClient = conversationalChatClient;
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Sends a message within a conversation session.
     * The session history is automatically included via the MessageChatMemoryAdvisor.
     *
     * @param sessionId unique identifier for the conversation session
     * @param prompt    user message to send
     * @return response payload with reply and metadata
     */
    public ChatResponsePayload chat(String sessionId, String prompt) {
        var started = Instant.now();

        var response = execute(
                () -> conversationalChatClient.prompt()
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, sessionId))
                        .user(prompt)
                        .call(),
                "Chat call failed"
        );

        var latency = Duration.between(started, Instant.now()).toMillis();
        var chatResponse = response.chatClientResponse().chatResponse();
        var reply = chatResponse != null
                ? chatResponse.getResult().getOutput().getText()
                : "";
        var model = chatResponse != null
                ? chatResponse.getMetadata().getModel()
                : "azure-openai";

        return new ChatResponsePayload(
                reply != null ? reply : "",
                model != null ? model : "azure-openai",
                latency,
                List.of()
        );
    }

    /**
     * Retrieves the conversation history for a session.
     *
     * @param sessionId unique identifier for the conversation session
     * @return conversation history with all messages
     */
    public ConversationHistory getHistory(String sessionId) {
        List<Message> messages = chatMemoryRepository.findByConversationId(sessionId);

        List<ConversationMessage> conversationMessages = messages.stream()
                .map(msg -> new ConversationMessage(
                        msg.getMessageType().name(),
                        msg.getText()
                ))
                .toList();

        return new ConversationHistory(sessionId, conversationMessages);
    }

    /**
     * Clears all messages from a conversation session.
     *
     * @param sessionId unique identifier for the conversation session
     */
    public void clearHistory(String sessionId) {
        chatMemory.clear(sessionId);
    }

    /**
     * Lists all active conversation sessions stored in the database.
     *
     * @return list of distinct session IDs
     */
    public List<String> listSessions() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY ORDER BY conversation_id",
                String.class
        );
    }

    /**
     * Returns the message count for a specific session.
     *
     * @param sessionId unique identifier for the conversation session
     * @return number of messages in the session
     */
    public int getMessageCount(String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
                Integer.class,
                sessionId
        );
        return count != null ? count : 0;
    }
}

