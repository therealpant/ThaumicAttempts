package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.world.tile.TileRiftPortalPlatform;
import therealpant.thaumicattempts.world.tile.TileRiftExtractor;

import javax.annotation.Nullable;

public class BlockRiftPortalPlatform extends Block {
    private static final AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

    public BlockRiftPortalPlatform() {
        super(Material.ROCK);
        setHardness(45.0F);
        setResistance(200.0F);
        setLightOpacity(0);
        setTranslationKey(ThaumicAttempts.MODID + ".rift_portal_platform");
        setRegistryName(ThaumicAttempts.MODID, "rift_portal_platform");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRiftPortalPlatform();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return AABB;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
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
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!MultiblockBreakHelper.isRestoring()) {
            MultiblockBreakHelper.Match match = MultiblockBreakHelper.findMatch(
                    world, pos, state,
                    TAMultiblockTriggers.RIFT_EXTRACTOR_MB_ID,
                    TAMultiblockTriggers.TRANSMUTATION_ALTAR_MB_ID,
                    TAMultiblockTriggers.RIFT_PORTAL_MB_ID
            );
            if (match != null) {
                if (TAMultiblockTriggers.RIFT_EXTRACTOR_MB_ID.equals(match.getBlueprintId())) {
                    BlockPos controllerPos = match.getCenter().up();
                    TileEntity tile = world.getTileEntity(controllerPos);
                    if (tile instanceof TileRiftExtractor) {
                        TileRiftExtractor extractor = (TileRiftExtractor) tile;
                        dropStack(world, controllerPos, extractor.getCrownStack());
                        dropStack(world, controllerPos, extractor.getCoreStack());
                    }
                }
                MultiblockBreakHelper.restoreMatch(world, pos, match);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        MultiblockBreakHelper.addSourceDrops(
                drops, world, pos, state, fortune,
                TAMultiblockTriggers.RIFT_EXTRACTOR_MB_ID,
                TAMultiblockTriggers.TRANSMUTATION_ALTAR_MB_ID,
                TAMultiblockTriggers.RIFT_PORTAL_MB_ID
        );
    }

    private static void dropStack(World world, BlockPos pos, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    }
}
