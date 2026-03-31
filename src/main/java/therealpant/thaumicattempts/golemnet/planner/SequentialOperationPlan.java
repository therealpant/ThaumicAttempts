package therealpant.thaumicattempts.golemnet.planner;

public final class SequentialOperationPlan {
    private final PlannedOperationNode root;
    private final PlannerFailureReason failure;
    private final String failureDetails;

    private SequentialOperationPlan(PlannedOperationNode root, PlannerFailureReason failure, String failureDetails) {
        this.root = root;
        this.failure = failure;
        this.failureDetails = failureDetails == null ? "" : failureDetails;
    }

    public static SequentialOperationPlan success(PlannedOperationNode root) {
        return new SequentialOperationPlan(root, PlannerFailureReason.NONE, "");
    }

    public static SequentialOperationPlan fail(PlannerFailureReason reason, String details) {
        return new SequentialOperationPlan(null, reason, details);
    }

    public boolean isSuccess() {
        return failure == PlannerFailureReason.NONE;
    }

    public PlannedOperationNode getRoot() {
        return root;
    }

    public PlannerFailureReason getFailure() {
        return failure;
    }

    public String getFailureDetails() {
        return failureDetails;
    }
}
