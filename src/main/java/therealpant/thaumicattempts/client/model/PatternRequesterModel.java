// src/main/java/therealpant/thaumicattempts/client/model/PatternRequesterModel.java
package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

public class PatternRequesterModel extends AnimatedGeoModel<TilePatternRequester> {

    @Override
    public ResourceLocation getModelLocation(TilePatternRequester animatable) {
        // твой pattern_requester.json
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/pattern_requester.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TilePatternRequester animatable) {
        // допустим ты собрал всё на один атлас:
        // assets/thaumicattempts/textures/blocks/pattern_requester.png
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/pattern_requester.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TilePatternRequester animatable) {
        // твой pattern_requester.animation.json, анимация "animation.model.pattern_requester"
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/pattern_requester.animation.json");
    }
}
