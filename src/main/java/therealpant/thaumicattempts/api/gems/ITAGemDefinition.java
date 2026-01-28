package therealpant.thaumicattempts.api.gems;

import net.minecraft.util.ResourceLocation;

/**
 * Public definition for a gem type.
 */
public interface ITAGemDefinition {
    /**
     * Unique gem id.
     *
     * @return gem id
     */
    ResourceLocation getId();

    /**
     * Maximum tier available for this gem.
     *
     * @return maximum tier
     */
    int getMaxTier();

    /**
     * Base durability for the requested tier.
     *
     * @param tier gem tier
     * @return durability value
     */
    int getBaseDurability(int tier);

    /**
     * Event trigger that causes durability loss.
     *
     * @return damage source
     */
    GemDamageSource getDamageSource();

    /**
     * Passive effect handler.
     *
     * @return passive effect
     */
    GemPassiveEffect getPassiveEffect();

    /**
     * Set bonus effect for two gems.
     *
     * @return set bonus effect
     */
    GemSetEffect getSetEffect2();

    /**
     * Set bonus effect for four gems.
     *
     * @return set bonus effect
     */
    GemSetEffect getSetEffect4();
}