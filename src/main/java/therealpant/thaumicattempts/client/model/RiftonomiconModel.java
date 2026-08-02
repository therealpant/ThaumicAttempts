package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

public class RiftonomiconModel extends AnimatedGeoModel<TileRiftonomicon> {
    private static final String[] SECOND_LAYER_ROOTS = {"bone44", "bone24", "bone28", "bone32"};

    @Override
    public ResourceLocation getModelLocation(TileRiftonomicon object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/riftonomicon.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftonomicon object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftonomicon.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftonomicon animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/riftonomicon.animation.json");
    }

    @Override
    public void setLivingAnimations(TileRiftonomicon tile, Integer uniqueId, AnimationEvent event) {
        super.setLivingAnimations(tile, uniqueId, event);
        int stage = tile == null ? 3 : tile.getFormationStage();
        setGroupHidden("bone19", stage < 1);
        for (String bone : SECOND_LAYER_ROOTS) {
            setGroupHidden(bone, stage < 2);
        }
    }

    private void setGroupHidden(String name, boolean hidden) {
        IBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) bone.setHidden(hidden);
    }
}
