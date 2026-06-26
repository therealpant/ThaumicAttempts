package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
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
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.items.ItemPortalRune;
import therealpant.thaumicattempts.world.tile.TileRiftStonePortal;

import javax.annotation.Nullable;

public class BlockRiftStonePortal extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 2.5, 1);

    public BlockRiftStonePortal() {
        super(Material.ROCK);
        setHardness(45.0F);
        setResistance(200.0F);
        setLightOpacity(0);
        setTranslationKey(ThaumicAttempts.MODID + ".rift_stone_portal");
        setRegistryName(ThaumicAttempts.MODID, "rift_stone_portal");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRiftStonePortal();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 3);
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() && player.isSneaking()) {
            if (!world.isRemote) {
                TileEntity tile = world.getTileEntity(pos);
                if (tile instanceof TileRiftStonePortal) {
                    ((TileRiftStonePortal) tile).rotateModelClockwise();
                }
            }
            return true;
        }

        if (!held.isEmpty() && held.getItem() == ModBlocksItems.PORTAL_RUNE) {
            if (!world.isRemote) {
                if (player.isSneaking()) {
                    ItemPortalRune.bindPrimaryIfEmpty(held, world, pos);
                } else {
                    ItemPortalRune.openPortalFromRune(held, world, pos);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public void onEntityCollision(World worldIn, BlockPos pos, IBlockState state, Entity entityIn) {
        if (worldIn.isRemote || !(entityIn instanceof EntityPlayer)) {
            return;
        }
        TileEntity tile = worldIn.getTileEntity(pos);
        if (tile instanceof TileRiftStonePortal) {
            ((TileRiftStonePortal) tile).tryTeleport((EntityPlayer) entityIn);
        }
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
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        TileEntity tile = worldIn.getTileEntity(pos);
        if (tile instanceof TileRiftStonePortal && ((TileRiftStonePortal) tile).isActive()) {
            return NULL_AABB;
        }
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
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!RiftPortalStructureHelper.isRestoring()) {
            RiftPortalStructureHelper.restoreFromPortal(world, pos);
        }
        super.breakBlock(world, pos, state);
    }
}
