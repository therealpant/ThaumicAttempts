package therealpant.thaumicattempts.gems;

import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.gems.GemDamageSource;
import therealpant.thaumicattempts.api.gems.GemPassiveEffect;
import therealpant.thaumicattempts.api.gems.GemSetEffect;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.effects.AmethystEffects;

public class AmethystGemDefinition implements ITAGemDefinition {
    public static final ResourceLocation ID = new ResourceLocation(ThaumicAttempts.MODID, "amethyst");
    public static final int TIER1_DURABILITY = 300;
    public static final int TIER2_DURABILITY = 350;
    public static final int TIER3_DURABILITY = 400;

    private static final GemPassiveEffect PASSIVE = (player, tier, sameGemCount) -> {
    };
    private static final GemSetEffect SET = player -> {
    };

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public int getMaxTier() {
        return AmethystEffects.MAX_TIER;
    }

    @Override
    public int getBaseDurability(int tier) {
        switch (tier) {
            case 1:
                return TIER1_DURABILITY;
            case 2:
                return TIER2_DURABILITY;
            case 3:
                return TIER3_DURABILITY;
            default:
                return 0;
        }
    }

    @Override
    public GemDamageSource getDamageSource() {
        return GemDamageSource.ON_PLAYER_HURT;
    }

    @Override
    public GemPassiveEffect getPassiveEffect() {
        return PASSIVE;
    }

    @Override
    public GemSetEffect getSetEffect2() {
        return SET;
    }

    @Override
    public GemSetEffect getSetEffect4() {
        return SET;
    }
}
