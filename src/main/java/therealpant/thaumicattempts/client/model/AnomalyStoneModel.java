package therealpant.thaumicattempts.client.model;

import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAnomalyStone;

public class AnomalyStoneModel extends AnimatedGeoModel<TileAnomalyStone> {

    @Override
    public ResourceLocation getModelLocation(TileAnomalyStone object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/rift_rock.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileAnomalyStone object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftstone_b.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileAnomalyStone animatable) {
        return new ResourceLocation(ThaumicAttempts.MODID, "animations/rift_rock.animation.json");
    }
}