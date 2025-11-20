package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.RoutingWorkflowResult;
import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.VoyagerIntent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoyagerRoutingWorkflowServiceTest {

    private final VoyagerRoutingWorkflowService.IntentClassifier classifier =
            prompt -> new VoyagerRoutingWorkflowService.IntentDecision(VoyagerIntent.TRAVEL_RISK, "Detected risk language");

    private final VoyagerRoutingWorkflowService.IntentResponder responder =
            (intent, prompt, context) -> "%s :: %s :: %s".formatted(intent, prompt, context.hashCode());

    private final VoyagerRoutingWorkflowService service =
            new VoyagerRoutingWorkflowService(classifier, responder);

    @Test
    void routesPromptAndReturnsResponderOutput() {
        var request = new TripPlanRequest(
                "Noah",
                "Toronto",
                "Mexico City",
                LocalDate.parse("2025-02-10"),
                LocalDate.parse("2025-02-20"),
                "premium",
                List.of("food")
        );

        RoutingWorkflowResult result = service.route("Is Hurricane season a concern?", request);

        assertThat(result.intent()).isEqualTo(VoyagerIntent.TRAVEL_RISK);
        assertThat(result.rationale()).isEqualTo("Detected risk language");
        assertThat(result.response()).contains("TRAVEL_RISK").contains("Hurricane season");
    }
}


