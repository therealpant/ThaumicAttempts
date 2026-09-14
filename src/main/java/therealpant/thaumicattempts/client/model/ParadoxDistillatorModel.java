package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileParadoxDistillator;

public class ParadoxDistillatorModel extends AnimatedGeoModel<TileParadoxDistillator> {
    @Override public ResourceLocation getModelLocation(TileParadoxDistillator object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/distilator.geo.json");
    }
    @Override public ResourceLocation getTextureLocation(TileParadoxDistillator object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/destilator.png");
    }
    @Override public ResourceLocation getAnimationFileLocation(TileParadoxDistillator object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/distilator.animation.json");
    }
}
