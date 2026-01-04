package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.world.taint.ITaintBlock;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftGeod;

import javax.annotation.Nullable;

public class BlockRiftGeod extends Block implements ITaintBlock {

    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class);
    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

    public BlockRiftGeod() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".rift_geod");
        setRegistryName(ThaumicAttempts.MODID, "rift_geod");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRiftGeod();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        // meta может прилететь мусорный, поэтому нормализуем в 0..5
        EnumFacing facing = EnumFacing.VALUES[meta % EnumFacing.VALUES.length];
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, facing); // или facing.getOpposite()
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return AABB;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean isTranslucent(IBlockState state) {
        return true;
    }

    @Override
    public boolean getUseNeighborBrightness(IBlockState state) {
        return true;
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public float getAmbientOcclusionLightValue(IBlockState state) {
        return 1.0F;
    }

    @Override
    public void randomTick(World world, BlockPos pos, IBlockState state, java.util.Random rand) {
        if (world.isRemote) return;

        if (!TaintHelper.isNearTaintSeed(world, pos)) {
            die(world, pos, state);
            return;
        }
        int count = FluxResourceHelper.countBlocks(world, pos, this, 4);
        if (!FluxResourceHelper.shouldReproduce(rand, count, 14, 1.0 / 16.0)) return;

        BlockPos target = FluxResourceHelper.randomOffset(pos, rand, 4, 2);
        if (target.getY() < 1 || target.getY() >= world.getHeight()) return;
        if (!world.isBlockLoaded(target)) return;

        IBlockState targetState = world.getBlockState(target);
        if (targetState.getMaterial().isLiquid()) return;
        if (!targetState.getMaterial().isReplaceable() && !world.isAirBlock(target)) return;

        EnumFacing facing = EnumFacing.UP;
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos neighbor = target.offset(face.getOpposite());
            IBlockState neighborState = world.getBlockState(neighbor);
            if (neighborState.isSideSolid(world, neighbor, face)) {
                facing = face;
                break;
            }
        }

        world.setBlockState(target, getDefaultState().withProperty(FACING, facing), 2);
        FluxResourceHelper.damageNearestSeed(world, target, 1.5f);
    }

    @Override
    public void die(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;

        world.setBlockState(pos, BlocksTC.stonePorous.getDefaultState(), 2);
    }
}
