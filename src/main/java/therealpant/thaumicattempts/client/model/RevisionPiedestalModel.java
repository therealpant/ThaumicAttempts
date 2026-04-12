package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileRevisionPiedestal;

public class RevisionPiedestalModel extends AnimatedGeoModel<TileRevisionPiedestal> {

    @Override
    public ResourceLocation getModelLocation(TileRevisionPiedestal object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/revision_piedestal.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRevisionPiedestal object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/revision.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRevisionPiedestal animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/revision.animation.json");
    }
}