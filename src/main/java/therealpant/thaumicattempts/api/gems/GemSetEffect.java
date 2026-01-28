package therealpant.thaumicattempts.api.gems;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Applies set bonus for 2 or 4 identical gems.
 */
@FunctionalInterface
public interface GemSetEffect {
    /**
     * Apply set effect for a player.
     *
     * @param player target player
     */
    void apply(EntityPlayer player);
}
