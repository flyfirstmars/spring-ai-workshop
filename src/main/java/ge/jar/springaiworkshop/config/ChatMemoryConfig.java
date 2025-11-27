package ge.jar.springaiworkshop.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Configuration for Spring AI Chat Memory with JDBC persistence.
 * <p>
 * This configuration sets up persistent conversation memory using PostgreSQL,
 * enabling VoyagerMate to maintain context across chat sessions.
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * Maximum number of messages to retain in the conversation window.
     * Older messages beyond this limit are evicted to manage context size.
     */
    @Value("${voyagermate.chat.memory.window-size:20}")
    private int windowSize;

    /**
     * Creates a JDBC-backed repository for persisting chat messages.
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcChatMemoryRepository(jdbcTemplate);
    }

    /**
     * Creates a windowed chat memory that maintains the most recent N messages.
     * System messages are always preserved regardless of the window size.
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(windowSize)
                .build();
    }

    /**
     * Custom JDBC-backed ChatMemoryRepository for PostgreSQL.
     */
    public static class JdbcChatMemoryRepository implements ChatMemoryRepository {

        private final JdbcTemplate jdbcTemplate;
        private final MessageRowMapper messageRowMapper = new MessageRowMapper();

        public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public List<Message> findByConversationId(String conversationId) {
            return jdbcTemplate.query(
                    "SELECT content, type FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? ORDER BY \"timestamp\" ASC",
                    messageRowMapper,
                    conversationId
            );
        }

        @Override
        public void saveAll(String conversationId, List<Message> messages) {
            for (Message message : messages) {
                jdbcTemplate.update(
                        "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type) VALUES (?, ?, ?)",
                        conversationId,
                        message.getText(),
                        message.getMessageType().name()
                );
            }
        }

        @Override
        public void deleteByConversationId(String conversationId) {
            jdbcTemplate.update(
                    "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
                    conversationId
            );
        }

        @Override
        public List<String> findConversationIds() {
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY ORDER BY conversation_id",
                    String.class
            );
        }

        private static class MessageRowMapper implements RowMapper<Message> {
            @Override
            public Message mapRow(ResultSet rs, int rowNum) throws SQLException {
                String content = rs.getString("content");
                String type = rs.getString("type");

                return switch (type) {
                    case "USER" -> new UserMessage(content);
                    case "ASSISTANT" -> new AssistantMessage(content);
                    case "SYSTEM" -> new SystemMessage(content);
                    default -> new UserMessage(content);
                };
            }
        }
    }
}
