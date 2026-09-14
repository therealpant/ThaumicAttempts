package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileDrainageNode;

public class DrainageNodeModel extends AnimatedGeoModel<TileDrainageNode> {
    @Override public ResourceLocation getModelLocation(TileDrainageNode object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/drenage.geo.json");
    }
    @Override public ResourceLocation getTextureLocation(TileDrainageNode object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/drenage.png");
    }
    @Override public ResourceLocation getAnimationFileLocation(TileDrainageNode object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/drenage.animation.json");
    }
}
