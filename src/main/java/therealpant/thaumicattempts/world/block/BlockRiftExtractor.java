package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileRiftExtractor;

import javax.annotation.Nullable;

public class BlockRiftExtractor extends Block {

    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 45.0 / 16.0, 1);
    private static final double SLOT_SPLIT_Y = 2.0;

    public BlockRiftExtractor() {
        super(Material.ROCK);
        setLightOpacity(0);
        setHardness(3.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".rift_extractor");
        setRegistryName(ThaumicAttempts.MODID, "rift_extractor");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return AABB;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRiftExtractor();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRiftExtractor) {
            TileRiftExtractor extractor = (TileRiftExtractor) te;
            dropStack(world, pos, extractor.getCrownStack());
            dropStack(world, pos, extractor.getCoreStack());
        }
        super.breakBlock(world, pos, state);
    }

    private void dropStack(World world, BlockPos pos, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileRiftExtractor)) {
            return false;
        }
        TileRiftExtractor extractor = (TileRiftExtractor) te;
        boolean upper = getLocalHitY(player, pos, hitY) > SLOT_SPLIT_Y;
        boolean emptyHand = player.getHeldItem(hand).isEmpty();

        if (upper) {
            if (player.isSneaking() && emptyHand) {
                extractor.tryExtractCrown(player);
                return true;
            }
            if (!player.isSneaking()) {
                extractor.tryInsertCrown(player, hand);
                return true;
            }
            return true;
        }

        if (player.isSneaking() && emptyHand) {
            extractor.tryExtractCore(player);
            return true;
        }
        return true;
    }

    private double getLocalHitY(EntityPlayer player, BlockPos pos, float fallbackHitY) {
        double reach = 5.0D;
        if (player instanceof EntityPlayerMP) {
            reach = ((EntityPlayerMP) player).interactionManager.getBlockReachDistance();
        }
        RayTraceResult result = player.rayTrace(reach, 1.0F);
        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK && pos.equals(result.getBlockPos())) {
            return result.hitVec.y - pos.getY();
        }
        return fallbackHitY;
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
}
