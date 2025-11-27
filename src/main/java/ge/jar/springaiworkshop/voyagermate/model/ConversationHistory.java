package ge.jar.springaiworkshop.voyagermate.model;

import java.util.List;

/**
 * Represents the complete conversation history for a session.
 *
 * @param sessionId unique identifier for the conversation session
 * @param messages  list of messages in chronological order
 */
public record ConversationHistory(String sessionId, List<ConversationMessage> messages) {

    /**
     * Returns the total number of messages in this conversation.
     */
    public int messageCount() {
        return messages != null ? messages.size() : 0;
    }

    /**
     * Checks if the conversation has any messages.
     */
    public boolean isEmpty() {
        return messages == null || messages.isEmpty();
    }
}

