package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftExtractor;

public class RiftExtractorModel extends AnimatedGeoModel<TileRiftExtractor> {

    @Override
    public ResourceLocation getModelLocation(TileRiftExtractor object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/rift_extractor.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftExtractor object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/mirror_manager.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftExtractor animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/rift_extractor.animation.json");
    }
}