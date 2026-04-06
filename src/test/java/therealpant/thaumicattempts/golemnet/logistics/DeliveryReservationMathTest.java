package therealpant.thaumicattempts.golemnet.logistics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DeliveryReservationMathTest {

    private static DeliveryReservation reservation(int requested, int staged, int dispatched, int delivered, int inFlightInbound) {
        DeliveryReservation r = new DeliveryReservation();
        r.requested = requested;
        r.staged = staged;
        r.dispatched = dispatched;
        r.delivered = delivered;
        r.inFlightInbound = inFlightInbound;
        return r;
    }

    @Test
    public void inboundNeedUsesReservationLocalState() {
        DeliveryReservation a = reservation(64, 10, 0, 0, 20);
        DeliveryReservation b = reservation(64, 60, 0, 0, 0);
        assertTrue(DeliveryReservationMath.needInbound(a));
        assertTrue(DeliveryReservationMath.needInbound(b));
    }

    @Test
    public void outboundAvailabilityDoesNotMergeSameItemAcrossOrders() {
        DeliveryReservation orderA = reservation(64, 64, 0, 0, 0);
        DeliveryReservation orderB = reservation(64, 0, 0, 0, 0);
        assertEquals(64, DeliveryReservationMath.availableForOutbound(orderA));
        assertEquals(0, DeliveryReservationMath.availableForOutbound(orderB));
    }

    @Test
    public void completionDependsOnlyOnDelivered() {
        DeliveryReservation r = reservation(64, 64, 64, 63, 0);
        assertFalse(DeliveryReservationMath.isCompleted(r));
        r.delivered = 64;
        assertTrue(DeliveryReservationMath.isCompleted(r));
    }

    @Test
    public void outboundAvailabilityCannotGoNegative() {
        DeliveryReservation r = reservation(64, 5, 10, 0, 0);
        assertEquals(0, DeliveryReservationMath.availableForOutbound(r));
    }
}