package therealpant.thaumicattempts.golemnet.logistics;

import therealpant.thaumicattempts.util.ItemKey;

import java.util.UUID;

public class DeliveryReservation {
    public UUID reservationId;
    public UUID orderId;
    public ItemKey itemKey;

    public int requested;
    public int staged;
    public int dispatched;
    public int delivered;

    public int inFlightInbound;
    public int inFlightOutbound;

    public int failedOrLost;

    public EndpointRef source;
    public EndpointRef managerBuffer;
    public EndpointRef finalTarget;

    public int reservedSlot = -1;

    public long createdTick;
    public long lastProgressTick;

    public DeliveryStage stage = DeliveryStage.NEW;
    public boolean released;
}