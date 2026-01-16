package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileRiftStoneBase;

public class RiftStoneBaseModel extends AnimatedGeoModel<TileRiftStoneBase> {

    @Override
    public ResourceLocation getModelLocation(TileRiftStoneBase object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/mirror_manager_base.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftStoneBase object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/mirror_manager.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftStoneBase animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/mirror_manager_base.animation.json");
    }
}