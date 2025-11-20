package ge.jar.springaiworkshop.voyagermate.workflow;

import ge.jar.springaiworkshop.voyagermate.model.TripPlanRequest;
import ge.jar.springaiworkshop.voyagermate.model.WorkerFinding;
import ge.jar.springaiworkshop.voyagermate.model.WorkerWorkflowSummary;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorWorkersWorkflowServiceTest {

    private final OrchestratorWorkersWorkflowService.TaskPlanner planner =
            (brief, context) -> new OrchestratorWorkersWorkflowService.Plan(
                    "Plan analysis for " + brief,
                    List.of(
                            new OrchestratorWorkersWorkflowService.WorkerTask("Flights", "air routing", "Compare carriers"),
                            new OrchestratorWorkersWorkflowService.WorkerTask("Experiences", "local culture", "Curate neighbourhood walks")
                    )
            );

    private final OrchestratorWorkersWorkflowService.WorkerExecutor workerExecutor =
            (task, context) -> new WorkerFinding(task.role(), task.focus(), "Result for " + task.role());

    private final OrchestratorWorkersWorkflowService.SynthesisAgent synthesisAgent =
            (analysis, findings, context) -> "Action plan using %d findings".formatted(findings.size());

    private final OrchestratorWorkersWorkflowService service =
            new OrchestratorWorkersWorkflowService(planner, workerExecutor, synthesisAgent);

    @Test
    void composesPlannerWorkersAndSynthesis() {
        WorkerWorkflowSummary summary = service.orchestrate("Plan a food retreat", sampleRequest());

        assertThat(summary.analysis()).contains("Plan analysis");
        assertThat(summary.workerFindings()).hasSize(2);
        assertThat(summary.workerFindings().getFirst().role()).isEqualTo("Flights");
        assertThat(summary.actionPlan()).contains("2 findings");
    }

    private TripPlanRequest sampleRequest() {
        return new TripPlanRequest(
                "Lena",
                "Vienna",
                "Lima",
                LocalDate.parse("2025-09-05"),
                LocalDate.parse("2025-09-20"),
                "premium",
                List.of("food", "culture")
        );
    }
}


