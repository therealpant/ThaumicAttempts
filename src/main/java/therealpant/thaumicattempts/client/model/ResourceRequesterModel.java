package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;

public class ResourceRequesterModel extends AnimatedGeoModel<TileResourceRequester> {

    @Override
    public ResourceLocation getModelLocation(TileResourceRequester object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/provision.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileResourceRequester object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/provision.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileResourceRequester animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/provosion.animation.json");
    }
}
