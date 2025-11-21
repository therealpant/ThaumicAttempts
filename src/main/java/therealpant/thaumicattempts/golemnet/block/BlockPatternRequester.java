// therealpant.thaumicattempts.golemnet.block.BlockPatternRequester
package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

import javax.annotation.Nullable;

public class BlockPatternRequester extends Block {

    public static final PropertyEnum<EnumFacing> FACING =
            PropertyEnum.create("facing", EnumFacing.class);

    public BlockPatternRequester() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey("pattern_requester");
        setRegistryName(ThaumicAttempts.MODID, "pattern_requester");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, (meta == 1) ? EnumFacing.DOWN : EnumFacing.UP);
    }
    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }
    @Override
    public IBlockState getStateForPlacement(World w, BlockPos pos, EnumFacing face,
                                            float hx, float hy, float hz, int meta, EntityLivingBase placer) {
        // Просто берём сторону, по которой кликнули — туда и "смотрит" блок
        return getDefaultState().withProperty(FACING, face);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TilePatternRequester(); }

    // редстоун как было
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public int getWeakPower(IBlockState s, IBlockAccess w, BlockPos p, EnumFacing side) {
        TileEntity te = w.getTileEntity(p);
        return (te instanceof TilePatternRequester) ? ((TilePatternRequester) te).getOutSignal() : 0;
    }
    @Override public int getStrongPower(IBlockState s, IBlockAccess w, BlockPos p, EnumFacing side) {
        return getWeakPower(s, w, p, side);
    }
    @Override public boolean canConnectRedstone(IBlockState s, IBlockAccess w, BlockPos p, @Nullable EnumFacing side) { return true; }

    private static final AxisAlignedBB AABB_LOW = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // контур выбора
    }
    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB_LOW;                    // коллизия (во что упирается игрок)
    }
    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(net.minecraft.block.state.IBlockState state) {
        // пусть всё рисует Geckolib (TESR), сам блок — невидимый
        return net.minecraft.util.EnumBlockRenderType.INVISIBLE;
    }

    // визуал/свет — чтобы земля вокруг не темнела
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s)   { return false; }
    @Override public int  getLightOpacity(IBlockState s, IBlockAccess w, BlockPos p) { return 0; }
    @Override public boolean isTranslucent(IBlockState s) { return true; }
    @Override public boolean getUseNeighborBrightness(IBlockState s) { return true; }
    @Override public boolean doesSideBlockRendering(IBlockState s, IBlockAccess w, BlockPos p, EnumFacing f) { return false; }
    @SideOnly(Side.CLIENT) @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @SideOnly(Side.CLIENT) @Override public boolean canRenderInLayer(IBlockState s, BlockRenderLayer layer) { return layer == BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public float getAmbientOcclusionLightValue(IBlockState s) { return 1.0F; }
}
