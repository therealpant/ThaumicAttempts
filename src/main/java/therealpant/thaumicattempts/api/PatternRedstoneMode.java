package therealpant.thaumicattempts.api;

/**
 * Redstone contract exposed by pattern-driven tiles.
 */
public enum PatternRedstoneMode {
    /** Rising edge 0→N starts a single craft cycle for slot derived from the signal. */
    RISING_EDGE_SELECTS_SLOT,

    /** Signal level (1-15) enqueues the corresponding slot; multiple pulses stack. */
    LEVEL_QUEUES_SLOT
}