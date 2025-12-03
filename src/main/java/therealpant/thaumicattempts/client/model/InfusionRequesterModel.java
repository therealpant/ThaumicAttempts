package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;

public class InfusionRequesterModel extends AnimatedGeoModel<TileInfusionRequester> {

    @Override
    public ResourceLocation getModelLocation(TileInfusionRequester object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/infusion_requester.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileInfusionRequester object) {
        // подставь свой путь к текстуре, если другой
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/infusion_requester.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileInfusionRequester animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/infusion_requester.animation.json");
    }
}

