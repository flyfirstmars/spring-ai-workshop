package ge.jar.springaiworkshop.voyagermate.model;

/**
 * Represents a single message in a conversation history.
 *
 * @param type    message type (USER, ASSISTANT, SYSTEM, TOOL)
 * @param content message content text
 */
public record ConversationMessage(String type, String content) {
}

