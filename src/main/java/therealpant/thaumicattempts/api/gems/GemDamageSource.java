package therealpant.thaumicattempts.api.gems;

/**
 * Source trigger for gem durability loss.
 */
public enum GemDamageSource {
    /**
     * Durability loss on focus casts.
     */
    ON_FOCUS_CAST,
    /**
     * Durability loss when player is hurt.
     */
    ON_PLAYER_HURT,
    /**
     * Durability loss when player hits a target in melee.
     */
    ON_PLAYER_HIT,
    /**
     * Durability loss over time.
     */
    TICK
}
