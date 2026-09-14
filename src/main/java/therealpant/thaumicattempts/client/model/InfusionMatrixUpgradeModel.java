package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileInfusionMatrixUpgrade;

public class InfusionMatrixUpgradeModel extends AnimatedGeoModel<TileInfusionMatrixUpgrade> {
    @Override
    public ResourceLocation getModelLocation(TileInfusionMatrixUpgrade object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/upgrade.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileInfusionMatrixUpgrade object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/upgrade.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileInfusionMatrixUpgrade animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/upgrade.animation.json");
    }
}
