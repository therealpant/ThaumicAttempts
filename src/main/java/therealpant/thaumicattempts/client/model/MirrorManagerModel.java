// src/main/java/therealpant/thaumicattempts/client/model/MirrorManagerModel.java
package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

public class MirrorManagerModel extends AnimatedGeoModel<TileMirrorManager> {

    @Override
    public ResourceLocation getModelLocation(TileMirrorManager object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/mirror_manager.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileMirrorManager object) {
        // подставь свой путь к текстуре, если другой
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/mirror_manager.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileMirrorManager animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/mirror_manager.animation.json");
    }
}
