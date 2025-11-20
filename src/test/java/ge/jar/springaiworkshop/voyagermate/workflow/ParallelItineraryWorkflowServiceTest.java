package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.ParallelWorkflowSummary;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelItineraryWorkflowServiceTest {

    private final ParallelItineraryWorkflowService service = new ParallelItineraryWorkflowService(null);

    @Test
    void aggregatesParallelTracksDeterministically() {
        var request = new TripPlanRequest(
                "Aisha",
                "Berlin",
                "Lisbon",
                LocalDate.parse("2025-05-10"),
                LocalDate.parse("2025-05-18"),
                "balanced",
                List.of("architecture", "food")
        );

        ParallelWorkflowSummary summary = service.buildSummary(request,
                (instruction, ctx) -> "[STEP] " + instruction + " :: " + ctx.hashCode());

        assertThat(summary.lodgingInsights()).contains("[STEP] Produce 3 lodging strategies");
        assertThat(summary.diningHighlights()).contains("[STEP] Highlight dining hits");
        assertThat(summary.logisticsAdvisory()).contains("[STEP] Summarise transport");
        assertThat(summary.culturalMoments()).contains("[STEP] Suggest cultural immersion moves");
        assertThat(summary.totalLatencyMs()).isGreaterThanOrEqualTo(0L);
    }
}


