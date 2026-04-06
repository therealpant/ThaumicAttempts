package therealpant.thaumicattempts.golemnet.logistics;

public enum DeliveryStage {
    NEW,
    WAITING_INBOUND,
    INBOUND_IN_FLIGHT,
    STAGED,
    WAITING_OUTBOUND,
    OUTBOUND_IN_FLIGHT,
    COMPLETED,
    STALLED
}