package ge.jar.springaiworkshop.voyagermate.model;

import java.util.Objects;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive container for streaming chat responses.
 */
public record ChatStreamPayload(Flux<String> chunks, Mono<ChatResponsePayload> completion) {

    public ChatStreamPayload {
        Objects.requireNonNull(chunks, "chunks must not be null");
        Objects.requireNonNull(completion, "completion must not be null");
    }
}
