package therealpant.thaumicattempts.golemnet.planner;

public enum PlannerFailureReason {
    NONE,
    NO_PROVIDER,
    CYCLE_DETECTED,
    INVALID_OUTPUT,
    ENQUEUE_REJECTED,
    INACTIVE
}