package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftStonePortal;

public class RiftStonePortalModel extends AnimatedGeoModel<TileRiftStonePortal> {
    @Override
    public ResourceLocation getModelLocation(TileRiftStonePortal object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/portal.modal.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftStonePortal object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/portal.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftStonePortal animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/portal.animation.json");
    }

    @Override
    public void setLivingAnimations(TileRiftStonePortal entity, Integer uniqueID, AnimationEvent customPredicate) {
        super.setLivingAnimations(entity, uniqueID, customPredicate);
        IBone portalPlane = this.getAnimationProcessor().getBone("bb_main");
        if (portalPlane != null) {
            portalPlane.setCubesHidden(true);
        }
    }
}
