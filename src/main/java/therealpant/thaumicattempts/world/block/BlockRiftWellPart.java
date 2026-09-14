package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockRiftWellPart extends Block {
    public BlockRiftWellPart() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "rift_well_part");
        setTranslationKey(ThaumicAttempts.MODID + ".rift_well_part");
        setHardness(8.0F);
        setResistance(40.0F);
        setSoundType(SoundType.STONE);
        setCreativeTab(null);
    }

    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return true; }
    @Override public int quantityDropped(java.util.Random random) { return 0; }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        BlockPos controller = RiftWellStructure.findController(world,pos);
        return controller == null ? 0 : world.getBlockState(controller).getBlock()
                .getComparatorInputOverride(world.getBlockState(controller),world,controller);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        BlockPos controller = RiftWellStructure.findController(world, pos);
        if (controller == null) return false;
        return ((BlockRiftWell) world.getBlockState(controller).getBlock()).onBlockActivated(
                world, controller, world.getBlockState(controller), player, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !RiftWellStructure.isDismantling()) {
            BlockPos controller = RiftWellStructure.findController(world, pos);
            if (controller != null) RiftWellStructure.dismantle(world, controller, true);
        }
        super.breakBlock(world, pos, state);
    }
}
