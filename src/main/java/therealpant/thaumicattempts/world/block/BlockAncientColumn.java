package therealpant.thaumicattempts.world.block;

import net.minecraft.block.BlockDirectional;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAncientColumn;

import javax.annotation.Nullable;

public class BlockAncientColumn extends BlockDirectional {

    public BlockAncientColumn() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "ancient_column");
        setTranslationKey(ThaumicAttempts.MODID + ".ancient_column");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(5.0F);
        setResistance(20.0F);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAncientColumn();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos,
                                            EnumFacing facing,
                                            float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer,
                                            EnumHand hand) {
        return getDefaultState().withProperty(
                FACING, facing == EnumFacing.DOWN ? EnumFacing.DOWN : EnumFacing.UP);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return (side == EnumFacing.UP || side == EnumFacing.DOWN)
                && super.canPlaceBlockOnSide(world, pos, side);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(
                FACING, (meta & 1) == 1 ? EnumFacing.DOWN : EnumFacing.UP);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING) == EnumFacing.DOWN ? 1 : 0;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
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

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }
}
