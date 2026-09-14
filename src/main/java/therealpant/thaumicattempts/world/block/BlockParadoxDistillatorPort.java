package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.tile.TileParadoxDistillatorPort;

import javax.annotation.Nullable;

public class BlockParadoxDistillatorPort extends Block {
    private static final AxisAlignedBB PORT_BOUNDS =
            new AxisAlignedBB(0.125D, 0.0D, 0.125D, 0.875D, 0.875D, 0.875D);

    public BlockParadoxDistillatorPort() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "paradox_distillator_port");
        setTranslationKey(ThaumicAttempts.MODID + ".paradox_distillator_port");
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setLightOpacity(0);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileParadoxDistillatorPort();
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return PORT_BOUNDS;
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(
            IBlockState state, IBlockAccess world, BlockPos pos) { return PORT_BOUNDS; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        BlockPos controller = pos.down();
        Block block = world.getBlockState(controller).getBlock();
        return block instanceof BlockParadoxDistillator
                && ((BlockParadoxDistillator) block).onBlockActivated(
                world, controller, world.getBlockState(controller), player, hand,
                facing, hitX, hitY, hitZ);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block changedBlock, BlockPos changedPos) {
        if (!world.isRemote
                && world.getBlockState(pos.down()).getBlock() != TABlocks.PARADOX_DISTILLATOR) {
            world.setBlockToAir(pos);
        }
    }
}
