package ge.jar.springaiworkshop.voyagermate.model;

import java.util.List;

public record RefinementResult(
        String finalProposal,
        List<RefinementRound> rounds
) {
}


