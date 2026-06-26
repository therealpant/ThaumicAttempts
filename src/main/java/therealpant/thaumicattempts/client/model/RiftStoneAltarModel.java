package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftStoneAltar;

public class RiftStoneAltarModel extends AnimatedGeoModel<TileRiftStoneAltar> {
    @Override
    public ResourceLocation getModelLocation(TileRiftStoneAltar object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/altar.modal.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftStoneAltar object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/transmutatio_altar.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftStoneAltar animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/altar.animation.json");
    }
}
