package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.items.ItemAnomalyCompass;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;

public class AnomalyCompassModel extends AnimatedGeoModel<ItemAnomalyCompass> {

    private final Vector3f pointerDirection = new Vector3f(-1.0F, 0.0F, 0.0F);
    private boolean hasTarget;

    @Override
    public ResourceLocation getModelLocation(ItemAnomalyCompass object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/anomali_compas.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(ItemAnomalyCompass object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/items/compas.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(ItemAnomalyCompass animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/anomali_compas.animation.json");
    }

    public void setPointerDirection(@Nullable Vector3f direction) {
        hasTarget = direction != null && direction.lengthSquared() > 1.0E-6F;
        if (hasTarget) {
            pointerDirection.set(direction);
            pointerDirection.normalize();
        }
    }

    @Override
    public void setLivingAnimations(ItemAnomalyCompass item, Integer uniqueID, AnimationEvent event) {
        super.setLivingAnimations(item, uniqueID, event);

        IBone pointer = this.getAnimationProcessor().getBone("bone15");
        if (pointer == null) return;

        pointer.setHidden(!hasTarget, false);
        if (!hasTarget) {
            pointer.setRotationX(0.0F);
            pointer.setRotationY(0.0F);
            pointer.setRotationZ(0.0F);
            return;
        }

        float z = Math.max(-1.0F, Math.min(1.0F, pointerDirection.z));
        pointer.setRotationX(0.0F);
        pointer.setRotationY((float) Math.asin(z));
        pointer.setRotationZ((float) Math.atan2(-pointerDirection.y, -pointerDirection.x));
    }
}
