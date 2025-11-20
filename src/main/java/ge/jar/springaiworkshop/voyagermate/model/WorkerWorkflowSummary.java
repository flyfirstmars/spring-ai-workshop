package ge.jar.springaiworkshop.voyagermate.model;

import java.util.List;

public record WorkerWorkflowSummary(
        String analysis,
        List<WorkerFinding> workerFindings,
        String actionPlan
) {
}


