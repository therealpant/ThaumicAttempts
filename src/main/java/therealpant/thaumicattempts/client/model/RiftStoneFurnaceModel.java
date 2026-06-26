package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftStoneFurnace;

public class RiftStoneFurnaceModel extends AnimatedGeoModel<TileRiftStoneFurnace> {
    @Override
    public ResourceLocation getModelLocation(TileRiftStoneFurnace object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/furnace.modal.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftStoneFurnace object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/rift_stone_furnase.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftStoneFurnace animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/furnace.animation.json");
    }
}
