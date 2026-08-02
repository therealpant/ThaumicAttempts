package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftGeod;

public class RiftGeodModel extends AnimatedGeoModel<TileRiftGeod> {

    @Override
    public ResourceLocation getModelLocation(TileRiftGeod object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/rift_geod.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftGeod object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/rift_geod.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftGeod animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/rift_geod.animation.json");
    }

    @Override
    public void setLivingAnimations(TileRiftGeod tile, Integer uniqueID, AnimationEvent event) {
        super.setLivingAnimations(tile, uniqueID, event);

        boolean collected = tile != null && tile.isCollected();

        IBone crystals = this.getAnimationProcessor().getBone("bone2");
        if (crystals != null) {
            crystals.setHidden(collected, false);
        }
    }
}
