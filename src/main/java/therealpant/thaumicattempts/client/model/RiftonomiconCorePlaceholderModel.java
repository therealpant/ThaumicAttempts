package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconCorePlaceholder;

public class RiftonomiconCorePlaceholderModel extends AnimatedGeoModel<TileRiftonomiconCorePlaceholder> {
    @Override
    public ResourceLocation getModelLocation(TileRiftonomiconCorePlaceholder object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/riftonomicon_core.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileRiftonomiconCorePlaceholder object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftonomicon.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileRiftonomiconCorePlaceholder object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/riftonomicon_core.animation.json");
    }
}
