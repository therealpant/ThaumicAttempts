package therealpant.thaumicattempts.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.block.BlockAnomalyStone;
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

    @Override
    public void setLivingAnimations(TileAnomalyStone tile, Integer uniqueID, AnimationEvent event) {
        super.setLivingAnimations(tile, uniqueID, event);

        boolean collected = false;
        if (tile != null && tile.getWorld() != null) {
            IBlockState state = tile.getWorld().getBlockState(tile.getPos());
            collected = state.getBlock() instanceof BlockAnomalyStone
                    && state.getValue(BlockAnomalyStone.HARVEST_STATE) == BlockAnomalyStone.HarvestState.COLLECTED;
        }

        for (int i = 2; i <= 7; i++) {
            IBone protrusion = this.getAnimationProcessor().getBone("bone" + i);
            if (protrusion != null) {
                protrusion.setHidden(collected, false);
            }
        }
    }
}
