package ge.jar.springaiworkshop.voyagermate.model;

public record AudioChatRequest(String prompt, String audioBase64, String mimeType) {
}
