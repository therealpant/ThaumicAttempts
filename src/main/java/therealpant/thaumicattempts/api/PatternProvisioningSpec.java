package therealpant.thaumicattempts.api;

import net.minecraft.util.EnumFacing;

/**
 * Describes how a pattern-driven tile issues delivery tasks for golems when
 * working off its own pattern list.
 */
public final class PatternProvisioningSpec {
    private final EnumFacing requestFacing;
    private final boolean supportsMirrorManager;
    private final boolean selfProvisionWhenUnmanaged;
    private final int minRequestInterval;

    public PatternProvisioningSpec(EnumFacing requestFacing,
                                   boolean supportsMirrorManager,
                                   boolean selfProvisionWhenUnmanaged,
                                   int minRequestInterval) {
        this.requestFacing = requestFacing == null ? EnumFacing.UP : requestFacing;
        this.supportsMirrorManager = supportsMirrorManager;
        this.selfProvisionWhenUnmanaged = selfProvisionWhenUnmanaged;
        this.minRequestInterval = Math.max(0, minRequestInterval);
    }

    /** Face used when emitting {@code GolemHelper.requestProvisioning} requests. */
    public EnumFacing getRequestFacing() { return requestFacing; }

    /**
     * Whether the tile can operate in a passive mode when a PatternRequester is
     * linked to a MirrorManager. When true, self-provision should be suppressed
     * and the manager is expected to issue the deliveries.
     */
    public boolean supportsMirrorManager() { return supportsMirrorManager; }

    /** Whether the tile queues its own provisioning tasks when not manager-backed. */
    public boolean selfProvisionWhenUnmanaged() { return selfProvisionWhenUnmanaged; }

    /**
     * Minimum ticks between self-issued provisioning orders. This mirrors the
     * internal throttling used by the concrete tile.
     */
    public int getMinRequestInterval() { return minRequestInterval; }

    /** Convenience spec for crafter tiles (upward requests, manager-aware). */
    public static PatternProvisioningSpec crafterSpec(int throttleTicks) {
        return new PatternProvisioningSpec(EnumFacing.UP, true, true, throttleTicks);
    }

    /** Convenience spec for the resource requester (upward requests, manager-aware). */
    public static PatternProvisioningSpec requesterSpec(int throttleTicks) {
        return new PatternProvisioningSpec(EnumFacing.UP, true, true, throttleTicks);
    }
}