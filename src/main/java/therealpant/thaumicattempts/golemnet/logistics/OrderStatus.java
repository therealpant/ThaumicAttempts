package therealpant.thaumicattempts.golemnet.logistics;

public enum OrderStatus {
    NEW,
    PLANNING,
    RUNNING,
    WAITING_BUFFER_SLOT,
    WAITING_INPUTS,
    DONE,
    FAILED,
    CANCELED
}