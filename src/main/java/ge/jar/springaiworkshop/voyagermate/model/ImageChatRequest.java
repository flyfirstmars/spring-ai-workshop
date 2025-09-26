package ge.jar.springaiworkshop.voyagermate.model;

public record ImageChatRequest(String prompt, String imageBase64, String mimeType) {
}
