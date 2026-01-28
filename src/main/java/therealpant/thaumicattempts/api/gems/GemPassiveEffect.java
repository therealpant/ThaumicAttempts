package therealpant.thaumicattempts.api.gems;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Applies passive bonus based on inserted gem tier and count.
 */
@FunctionalInterface
public interface GemPassiveEffect {
    /**
     * Apply passive effect for a player.
     *
     * @param player        target player
     * @param tier          gem tier
     * @param sameGemCount  count of same gem on the player (1..4)
     */
    void apply(EntityPlayer player, int tier, int sameGemCount);
}
