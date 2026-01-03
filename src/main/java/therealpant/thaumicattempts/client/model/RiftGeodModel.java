package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftGeod;

public class RiftGeodModel extends AnimatedGeoModel<TileRiftGeod> {

    @Override
    public ResourceLocation getModelLocation(TileRiftGeod object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/rift_geod.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftGeod object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/rift_geod.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftGeod animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/rift_geod.animation.json");
    }
}