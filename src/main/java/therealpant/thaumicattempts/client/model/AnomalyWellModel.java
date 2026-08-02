package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAnomalyWell;

public class AnomalyWellModel extends AnimatedGeoModel<TileAnomalyWell> {
    @Override
    public ResourceLocation getModelLocation(TileAnomalyWell object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/well.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileAnomalyWell object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/well.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileAnomalyWell object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/well.animation.json");
    }
}
