package ge.jar.springaiworkshop.voyagermate.model;

import java.util.List;

public record ChatResponsePayload(String reply, String model, long latencyMs, List<String> toolCalls) {

    public ChatResponsePayload(String reply, String model, long latencyMs) {
        this(reply, model, latencyMs, List.of());
    }
}
