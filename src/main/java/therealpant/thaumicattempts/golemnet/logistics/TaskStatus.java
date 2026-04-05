package therealpant.thaumicattempts.golemnet.logistics;

public enum TaskStatus {
    NEW,
    WAITING_DEPENDENCY,
    WAITING_SOURCE,
    READY,
    DISPATCHED,
    ACCEPTED,
    IN_PROGRESS,
    STALLED_OUTPUT,
    DONE,
    FAILED,
    BLOCKED,
    CANCELED
}