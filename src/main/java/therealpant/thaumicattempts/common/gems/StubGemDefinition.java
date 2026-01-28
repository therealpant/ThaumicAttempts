package therealpant.thaumicattempts.common.gems;

import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.gems.GemDamageSource;
import therealpant.thaumicattempts.api.gems.GemPassiveEffect;
import therealpant.thaumicattempts.api.gems.GemSetEffect;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;

/**
 * Stub gem definition used for testing.
 */
public class StubGemDefinition implements ITAGemDefinition {
    public static final ResourceLocation ID = new ResourceLocation(ThaumicAttempts.MODID, "stub_gem");

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
        return 3;
    }

    @Override
    public int getBaseDurability(int tier) {
        switch (tier) {
            case 1:
                return 100;
            case 2:
                return 200;
            case 3:
                return 400;
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