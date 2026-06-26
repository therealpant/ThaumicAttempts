package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftPortalPlatform;

public class RiftPortalPlatformModel extends AnimatedGeoModel<TileRiftPortalPlatform> {
    @Override
    public ResourceLocation getModelLocation(TileRiftPortalPlatform object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/platform.modal.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftPortalPlatform object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/platform.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftPortalPlatform animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/platform.animation.json");
    }
}
