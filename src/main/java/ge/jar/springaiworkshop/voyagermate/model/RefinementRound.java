package ge.jar.springaiworkshop.voyagermate.model;

public record RefinementRound(
        int iteration,
        String draft,
        String feedback,
        boolean accepted
) {
}


