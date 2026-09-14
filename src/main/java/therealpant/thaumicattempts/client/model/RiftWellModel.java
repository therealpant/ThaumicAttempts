package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftWell;

public class RiftWellModel extends AnimatedGeoModel<TileRiftWell> {
    @Override public ResourceLocation getModelLocation(TileRiftWell object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/rift_well.geo.json");
    }
    @Override public ResourceLocation getTextureLocation(TileRiftWell object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftwell.png");
    }
    @Override public ResourceLocation getAnimationFileLocation(TileRiftWell object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/rift_well.animation.json");
    }
}
