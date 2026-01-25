package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAuraBooster;

public class AuraBoosterModel extends AnimatedGeoModel<TileAuraBooster> {

    @Override
    public ResourceLocation getModelLocation(TileAuraBooster object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/aura_booster.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileAuraBooster object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/aura_buffer.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileAuraBooster animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/aura_booster.animation.json");
    }
}