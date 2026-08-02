package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAnomalousMonolith;

public class AnomalousMonolithModel extends AnimatedGeoModel<TileAnomalousMonolith> {
    @Override
    public ResourceLocation getModelLocation(TileAnomalousMonolith object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/condensator.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileAnomalousMonolith object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/well.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileAnomalousMonolith object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/condensator.animation.json");
    }
}
