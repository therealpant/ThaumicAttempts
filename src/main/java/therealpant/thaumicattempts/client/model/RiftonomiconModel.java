package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

public class RiftonomiconModel extends AnimatedGeoModel<TileRiftonomicon> {
    @Override
    public ResourceLocation getModelLocation(TileRiftonomicon object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/riftonomicon.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftonomicon object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftonomicon.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftonomicon animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/riftonomicon.animation.json");
    }
}
