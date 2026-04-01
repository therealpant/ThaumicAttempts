package therealpant.thaumicattempts.golemnet.logistics;

public enum TaskStatus {
    NEW,
    WAITING_DEPENDENCY,
    READY,
    DISPATCHED,
    ACCEPTED,
    IN_PROGRESS,
    DONE,
    FAILED,
    BLOCKED,
    CANCELED
}