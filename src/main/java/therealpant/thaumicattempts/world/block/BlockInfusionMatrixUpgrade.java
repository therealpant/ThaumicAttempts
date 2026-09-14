package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.integration.infusion.IParallelInfusionMatrixAccess;
import therealpant.thaumicattempts.world.tile.TileInfusionMatrixUpgrade;

import javax.annotation.Nullable;

public class BlockInfusionMatrixUpgrade extends Block implements IInteractWithCaster {
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class);

    public BlockInfusionMatrixUpgrade() {
        super(Material.IRON);
        setRegistryName(ThaumicAttempts.MODID, "infusion_matrix_upgrade");
        setTranslationKey(ThaumicAttempts.MODID + ".infusion_matrix_upgrade");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(4.0F);
        setResistance(12.0F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta);
        return getDefaultState().withProperty(FACING, facing == null ? EnumFacing.UP : facing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing clickedFace,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing relative = findRelativeFacing(world, pos);
        return getDefaultState().withProperty(FACING, relative == null ? clickedFace : relative);
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && findRelativeFacing(world, pos) != null;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        notifyMatrix(world, pos, state.getValue(FACING));
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        EnumFacing facing = state.getValue(FACING);
        if (!(world.getTileEntity(pos.offset(facing.getOpposite())) instanceof TileInfusionMatrix)) {
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        notifyMatrix(world, pos, state.getValue(FACING));
        super.breakBlock(world, pos, state);
    }

    @Nullable
    private EnumFacing findRelativeFacing(World world, BlockPos pos) {
        for (EnumFacing fromBlockToMatrix : EnumFacing.VALUES) {
            if (world.getTileEntity(pos.offset(fromBlockToMatrix)) instanceof TileInfusionMatrix) {
                return fromBlockToMatrix.getOpposite();
            }
        }
        return null;
    }

    private void notifyMatrix(World world, BlockPos pos, EnumFacing relativeFacing) {
        if (world == null || world.isRemote || relativeFacing == null) return;
        TileEntity tile = world.getTileEntity(pos.offset(relativeFacing.getOpposite()));
        if (tile instanceof IParallelInfusionMatrixAccess) {
            ((IParallelInfusionMatrixAccess) tile).ta$setCheckSurroundings(true);
            tile.markDirty();
        }
    }

    @Override
    public boolean onCasterRightClick(World world, ItemStack caster, EntityPlayer player, BlockPos pos,
                                      EnumFacing side, EnumHand hand) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != this) return false;

        EnumFacing relativeFacing = state.getValue(FACING);
        BlockPos matrixPos = pos.offset(relativeFacing.getOpposite());
        TileEntity tile = world.getTileEntity(matrixPos);
        if (tile instanceof TileInfusionMatrix) {
            return ((TileInfusionMatrix) tile).onCasterRightClick(
                    world, caster, player, matrixPos, relativeFacing, hand);
        }
        return false;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileInfusionMatrixUpgrade();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean isPassable(IBlockAccess world, BlockPos pos) { return true; }
    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state,
                                            BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
    @Override public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) { return 0; }
}
