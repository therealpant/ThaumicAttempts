package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileStrangePedestal;

public class StrangePedestalModel extends AnimatedGeoModel<TileStrangePedestal> {
    @Override
    public ResourceLocation getModelLocation(TileStrangePedestal object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/charger.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileStrangePedestal object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/well.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileStrangePedestal object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/charger.animation.json");
    }
}
