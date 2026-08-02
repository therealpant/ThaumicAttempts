package therealpant.thaumicattempts.client.model;

import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAncientColumn;

public class AncientColumnModel extends AnimatedGeoModel<TileAncientColumn> {

    @Override
    public ResourceLocation getModelLocation(TileAncientColumn object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/columm.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileAncientColumn object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/columm.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileAncientColumn object) {
        return new ResourceLocation(
                ThaumicAttempts.MODID, "animations/columm.animation.json");
    }

    @Override
    public void setLivingAnimations(TileAncientColumn tile, Integer uniqueId,
                                    AnimationEvent event) {
        super.setLivingAnimations(tile, uniqueId, event);
        boolean ceiling = false;
        if (tile != null && tile.getWorld() != null) {
            IBlockState state = tile.getWorld().getBlockState(tile.getPos());
            if (state.getPropertyKeys().contains(BlockDirectional.FACING)) {
                ceiling = state.getValue(BlockDirectional.FACING) == EnumFacing.DOWN;
            }
        }

        IBone floorBone = getAnimationProcessor().getBone("bone3");
        IBone ceilingBone = getAnimationProcessor().getBone("bone4");
        if (floorBone != null) floorBone.setHidden(ceiling, ceiling);
        if (ceilingBone != null) ceilingBone.setHidden(!ceiling, !ceiling);
    }
}
