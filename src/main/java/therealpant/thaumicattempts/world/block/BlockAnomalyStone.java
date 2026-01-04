package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
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
import therealpant.thaumicattempts.world.tile.TileAnomalyStone;

import javax.annotation.Nullable;

public class BlockAnomalyStone extends Block implements ITaintBlock {
    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

    public BlockAnomalyStone() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_stone");
        setRegistryName(ThaumicAttempts.MODID, "anomaly_stone");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileAnomalyStone createTileEntity(World world, IBlockState state) {
        return new TileAnomalyStone();
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
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
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
        }
    }

    @Override
    public void die(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;

        world.setBlockState(pos, BlocksTC.stonePorous.getDefaultState(), 2);
    }
}