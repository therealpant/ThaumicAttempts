package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAncientFire;

import javax.annotation.Nullable;

public class BlockAncientFire extends BlockDirectional {

    public BlockAncientFire() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "ancient_fire");
        setTranslationKey(ThaumicAttempts.MODID + ".ancient_fire");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(3.0F);
        setResistance(12.0F);
        setSoundType(SoundType.STONE);
        setLightLevel(12.0F / 15.0F);
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
        return new TileAncientFire();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos,
                                            EnumFacing facing,
                                            float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer,
                                            EnumHand hand) {
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return hasSupport(world, pos, side);
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        for (EnumFacing side : EnumFacing.VALUES) {
            if (hasSupport(world, pos, side)) return true;
        }
        return false;
    }

    private static boolean hasSupport(World world, BlockPos pos, EnumFacing facing) {
        BlockPos supportPos = pos.offset(facing.getOpposite());
        return world.isSideSolid(supportPos, facing, true);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block block, BlockPos fromPos) {
        if (!hasSupport(world, pos, state.getValue(FACING))) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta);
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state,
                                                 IBlockAccess world,
                                                 BlockPos pos) {
        return NULL_AABB;
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

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }
}
