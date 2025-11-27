-- Spring AI Chat Memory table for JdbcChatMemoryRepository
-- This table stores conversation history with support for sliding window memory

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(255) NOT NULL,
    content         TEXT NOT NULL,
    type            VARCHAR(50) NOT NULL,
    "timestamp"     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, "timestamp")
);

-- Index for efficient conversation lookups
CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation 
    ON SPRING_AI_CHAT_MEMORY(conversation_id);

-- Index for ordering messages within a conversation
CREATE INDEX IF NOT EXISTS idx_chat_memory_timestamp 
    ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp" DESC);

COMMENT ON TABLE SPRING_AI_CHAT_MEMORY IS 'Stores chat conversation history for Spring AI MessageWindowChatMemory';
COMMENT ON COLUMN SPRING_AI_CHAT_MEMORY.conversation_id IS 'Unique identifier for a conversation session';
COMMENT ON COLUMN SPRING_AI_CHAT_MEMORY.content IS 'The actual message content';
COMMENT ON COLUMN SPRING_AI_CHAT_MEMORY.type IS 'Type of message: USER, ASSISTANT, or SYSTEM';
COMMENT ON COLUMN SPRING_AI_CHAT_MEMORY."timestamp" IS 'Timestamp when the message was created';
