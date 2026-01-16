package therealpant.thaumicattempts.golemcraft.block;

import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.common.blocks.IBlockEnabled;
import thaumcraft.common.blocks.IBlockFacing;
import thaumcraft.common.lib.utils.BlockStateUtils;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.tile.TileArcaneEarBand;

import javax.annotation.Nullable;
import java.util.List;

public class BlockArcaneEarBand extends Block implements IBlockFacing, IBlockEnabled {

    // На всякий: если интерфейсы вдруг не содержат полей (в некоторых форках/версиях)
    // можно раскомментить и использовать эти, но у тебя уже компилилось с IBlockFacing.FACING / IBlockEnabled.ENABLED.
    // public static final PropertyDirection FACING = PropertyDirection.create("facing");
    // public static final PropertyBool ENABLED = PropertyBool.create("enabled");

    private static final List<SoundEvent> INSTRUMENTS =
            Lists.newArrayList(
                    SoundEvents.BLOCK_NOTE_HARP,
                    SoundEvents.BLOCK_NOTE_BASEDRUM,
                    SoundEvents.BLOCK_NOTE_SNARE,
                    SoundEvents.BLOCK_NOTE_HAT,
                    SoundEvents.BLOCK_NOTE_BASS,
                    SoundEvents.BLOCK_NOTE_FLUTE,
                    SoundEvents.BLOCK_NOTE_BELL,
                    SoundEvents.BLOCK_NOTE_GUITAR,
                    SoundEvents.BLOCK_NOTE_CHIME,
                    SoundEvents.BLOCK_NOTE_XYLOPHONE
            );

    public BlockArcaneEarBand() {
        super(Material.WOOD);

        setRegistryName(ThaumicAttempts.MODID, "arcane_ear_band");
        setTranslationKey(ThaumicAttempts.MODID + ".arcane_ear_band");

        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setSoundType(SoundType.WOOD);
        setHardness(1.0F);
        setResistance(1.0F);

        IBlockState bs = this.blockState.getBaseState()
                .withProperty(IBlockFacing.FACING, EnumFacing.UP)
                .withProperty(IBlockEnabled.ENABLED, false);
        setDefaultState(bs);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, IBlockFacing.FACING, IBlockEnabled.ENABLED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta & 7);
        boolean enabled = (meta & 8) != 0;
        return getDefaultState()
                .withProperty(IBlockFacing.FACING, facing)
                .withProperty(IBlockEnabled.ENABLED, enabled);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(IBlockFacing.FACING).getIndex();
        if (state.getValue(IBlockEnabled.ENABLED)) meta |= 8;
        return meta;
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState()
                .withProperty(IBlockFacing.FACING, facing)
                .withProperty(IBlockEnabled.ENABLED, false);
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public int damageDropped(IBlockState state) { return 0; }

    // --- TileEntity ---
    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileArcaneEarBand();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileArcaneEarBand) {
            ((TileArcaneEarBand) te).updateTone();
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileArcaneEarBand) {
            ((TileArcaneEarBand) te).updateTone();
        }

        EnumFacing f = BlockStateUtils.getFacing(state);
        BlockPos support = pos.offset(f.getOpposite());
        if (!world.getBlockState(support).isSideSolid(world, support, f)) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileArcaneEarBand) {
            TileArcaneEarBand tile = (TileArcaneEarBand) te;
            tile.changePitch();
            triggerNote(world, pos, true, state);
        }
        return true;
    }

    @Override public boolean canProvidePower(IBlockState state) { return true; }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return (te instanceof TileArcaneEarBand) ? ((TileArcaneEarBand) te).getCurrentPower() : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return getWeakPower(state, world, pos, side);
    }

    @Override
    public boolean canPlaceBlockOnSide(World worldIn, BlockPos pos, EnumFacing side) {
        BlockPos support = pos.offset(side.getOpposite());
        return worldIn.getBlockState(support).isSideSolid(worldIn, support, side);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        EnumFacing facing = BlockStateUtils.getFacing(getMetaFromState(state));
        switch (facing.ordinal()) {
            case 0:  return new AxisAlignedBB(0.125F, 0.625F, 0.125F, 0.875F, 1.000F, 0.875F); // DOWN
            case 1:  return new AxisAlignedBB(0.125F, 0.000F, 0.125F, 0.875F, 0.375F, 0.875F); // UP
            case 2:  return new AxisAlignedBB(0.125F, 0.125F, 0.625F, 0.875F, 0.875F, 1.000F); // NORTH
            case 3:  return new AxisAlignedBB(0.125F, 0.125F, 0.000F, 0.875F, 0.875F, 0.375F); // SOUTH
            case 4:  return new AxisAlignedBB(0.625F, 0.125F, 0.125F, 1.000F, 0.875F, 0.875F); // WEST
            default: return new AxisAlignedBB(0.000F, 0.125F, 0.125F, 0.375F, 0.875F, 0.875F); // EAST
        }
    }

    private void triggerNote(World world, BlockPos pos, boolean sound, IBlockState state) {
        byte instrumentType = -1;
        if (sound) {
            EnumFacing facing = BlockStateUtils.getFacing(state).getOpposite();
            IBlockState iblockstate = world.getBlockState(pos.offset(facing));
            Material material = iblockstate.getMaterial();
            instrumentType = 0;
            if (material == Material.ROCK)  instrumentType = 1;
            if (material == Material.SAND)  instrumentType = 2;
            if (material == Material.GLASS) instrumentType = 3;
            if (material == Material.WOOD)  instrumentType = 4;
            Block block = iblockstate.getBlock();
            if (block == net.minecraft.init.Blocks.CLAY)       instrumentType = 5;
            if (block == net.minecraft.init.Blocks.GOLD_BLOCK) instrumentType = 6;
            if (block == net.minecraft.init.Blocks.WOOL)       instrumentType = 7;
            if (block == net.minecraft.init.Blocks.PACKED_ICE) instrumentType = 8;
            if (block == net.minecraft.init.Blocks.BONE_BLOCK) instrumentType = 9;
        }

        TileEntity te = world.getTileEntity(pos);
        int note = (te instanceof TileArcaneEarBand) ? (((TileArcaneEarBand) te).baseNote & 0xFF) : 0;
        world.addBlockEvent(pos, this, instrumentType, note);
    }

    @Override
    public boolean eventReceived(IBlockState state, World worldIn, BlockPos pos, int par5, int par6) {
        super.eventReceived(state, worldIn, pos, par5, par6);

        if (worldIn.isRemote) {
            TileEntity te = worldIn.getTileEntity(pos);
            if (te instanceof TileArcaneEarBand) {
                ((TileArcaneEarBand) te).clientSetBaseNote(par6);
            }
        }

        float pitch = (float) Math.pow(2.0F, (par6 - 12) / 12.0F);
        worldIn.playSound(null, pos, getInstrument(par5), SoundCategory.BLOCKS, 3.0F, pitch);
        worldIn.spawnParticle(EnumParticleTypes.NOTE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                par6 / 24.0, 0.0, 0.0);
        return true;
    }

    protected SoundEvent getInstrument(int type) {
        if (type < 0 || type >= INSTRUMENTS.size()) type = 0;
        return INSTRUMENTS.get(type);
    }

    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
