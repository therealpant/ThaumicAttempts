package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManagerCore;

public class MirrorManagerCoreModel extends AnimatedGeoModel<TileMirrorManagerCore> {

    @Override
    public ResourceLocation getModelLocation(TileMirrorManagerCore object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/mirror_manager_core.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileMirrorManagerCore object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/mirror_manager.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileMirrorManagerCore animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/mirror_manager_core.animation.json");
    }
}