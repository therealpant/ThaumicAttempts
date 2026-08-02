package therealpant.thaumicattempts.client.model;

import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAncientFire;

public class AncientFireModel extends AnimatedGeoModel<TileAncientFire> {

    @Override
    public ResourceLocation getModelLocation(TileAncientFire object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "geo/lenter.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(TileAncientFire object) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/lenter.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(TileAncientFire object) {
        return new ResourceLocation(
                ThaumicAttempts.MODID, "animations/lenter.animation.json");
    }

    @Override
    public void setLivingAnimations(TileAncientFire tile, Integer uniqueId,
                                    AnimationEvent event) {
        super.setLivingAnimations(tile, uniqueId, event);

        EnumFacing facing = EnumFacing.UP;
        if (tile != null && tile.getWorld() != null) {
            IBlockState state = tile.getWorld().getBlockState(tile.getPos());
            if (state.getPropertyKeys().contains(BlockDirectional.FACING)) {
                facing = state.getValue(BlockDirectional.FACING);
            }
        }

        setRootVisible("bone", facing == EnumFacing.UP);
        setRootVisible("bone5", facing == EnumFacing.DOWN);
        setRootVisible("bone6", facing.getAxis().isHorizontal());
    }

    private void setRootVisible(String name, boolean visible) {
        IBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) bone.setHidden(!visible, !visible);
    }
}
