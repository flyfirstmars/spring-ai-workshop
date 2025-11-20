package ge.jar.springaiworkshop.voyagermate.model;

public record RoutingWorkflowResult(
        VoyagerIntent intent,
        String rationale,
        String response
) {
}


