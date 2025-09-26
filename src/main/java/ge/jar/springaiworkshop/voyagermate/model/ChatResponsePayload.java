package ge.jar.springaiworkshop.voyagermate.model;

public record ChatResponsePayload(String reply, String model, long latencyMs) {
}
