package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.RefinementResult;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryRefinementWorkflowServiceTest {

    private final AtomicInteger drafts = new AtomicInteger();

    private final ItineraryRefinementWorkflowService.DraftGenerator generator =
            (brief, ctx, notes) -> "Draft-%d %s".formatted(
                    drafts.incrementAndGet(),
                    notes == null ? "[base]" : "[notes:" + notes + "]"
            );

    private final ItineraryRefinementWorkflowService.DraftEvaluator evaluator =
            (draft, context, iteration) -> {
                var accepted = iteration == 2;
                return new ItineraryRefinementWorkflowService.EvaluationFeedback(
                        accepted,
                        accepted ? "Ready to share" : "Add concrete budget reminders"
                );
            };

    private final ItineraryRefinementWorkflowService service =
            new ItineraryRefinementWorkflowService(generator, evaluator);

    @Test
    void haltsWhenEvaluatorAcceptsDraft() {
        RefinementResult result = service.refine("Make it playful", sampleRequest());

        assertThat(result.rounds()).hasSize(2);
        assertThat(result.finalProposal()).contains("Draft-2");
        assertThat(result.rounds().get(0).accepted()).isFalse();
        assertThat(result.rounds().get(1).accepted()).isTrue();
        assertThat(result.rounds().get(1).draft()).contains("notes:Add concrete budget reminders");
    }

    private TripPlanRequest sampleRequest() {
        return new TripPlanRequest(
                "Kai",
                "Seattle",
                "Osaka",
                LocalDate.parse("2025-04-03"),
                LocalDate.parse("2025-04-11"),
                "balanced",
                List.of("ramen", "design")
        );
    }
}


