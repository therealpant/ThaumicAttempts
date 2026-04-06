package therealpant.thaumicattempts.golemnet.logistics;

public final class DeliveryReservationMath {
    private DeliveryReservationMath() {}

    public static boolean needInbound(DeliveryReservation r) {
        if (r == null) return false;
        return r.requested > (r.staged + r.inFlightInbound + r.delivered);
    }

    public static int availableForOutbound(DeliveryReservation r) {
        if (r == null) return 0;
        return Math.max(0, r.staged - r.dispatched);
    }

    public static boolean isCompleted(DeliveryReservation r) {
        return r != null && r.delivered >= r.requested;
    }
}