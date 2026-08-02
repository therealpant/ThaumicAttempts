package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.world.tile.TileAnomalyArchive;

import javax.annotation.Nullable;

public class BlockAnomalyArchive extends Block {

    private static final AxisAlignedBB SELECTION_BOX =
            new AxisAlignedBB(-0.40D, -0.20D, -0.40D, 1.40D, 2.90D, 1.40D);
    public BlockAnomalyArchive() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID, "anomaly_archive");
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_archive");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setHardness(12.0F);
        setResistance(40.0F);
        setLightOpacity(0);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAnomalyArchive();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SELECTION_BOX;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SELECTION_BOX;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        return harvestWithPickaxe(world, pos, player, hand);
    }

    public boolean harvestWithPickaxe(World world, BlockPos pos,
                                      EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!isPickaxe(held)) return false;

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileAnomalyArchive)) return false;
        if (world.isRemote) return true;
        if (((TileAnomalyArchive) tile).hideRandomVisibleBone()) {
            ItemStack reward = world.rand.nextInt(10) == 0
                    ? new ItemStack(ModBlocksItems.ARCHIVE_FRAGMENTS)
                    : new ItemStack(ModBlocksItems.RIFT_STONE);
            spawnRewardAtVerticalFace(world, pos, player, reward);
        }
        return true;
    }

    private static void spawnRewardAtVerticalFace(World world, BlockPos pos,
                                                  EntityPlayer player, ItemStack reward) {
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double dx = player.posX - centerX;
        double dz = player.posZ - centerZ;
        EnumFacing face;
        if (Math.abs(dx) >= Math.abs(dz)) {
            face = dx >= 0.0D ? EnumFacing.EAST : EnumFacing.WEST;
        } else {
            face = dz >= 0.0D ? EnumFacing.SOUTH : EnumFacing.NORTH;
        }

        // The archive reaches 0.4 blocks beyond its block on every side.
        // Spawn another 0.15 blocks outside the nearest vertical face.
        double x = centerX + face.getXOffset() * 1.05D;
        double y = pos.getY() + 1.2D;
        double z = centerZ + face.getZOffset() * 1.05D;
        EntityItem drop = new EntityItem(world, x, y, z, reward);
        drop.motionX = face.getXOffset() * 0.08D;
        drop.motionY = 0.06D;
        drop.motionZ = face.getZOffset() * 0.08D;
        drop.setDefaultPickupDelay();
        world.spawnEntity(drop);
    }

    public static boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof ItemPickaxe
                || stack.getItem().getToolClasses(stack).contains("pickaxe"));
    }

    public static AxisAlignedBB getInteractionBox(BlockPos pos) {
        return SELECTION_BOX.offset(pos);
    }

    @Nullable
    public static RayTraceResult rayTraceInteraction(BlockPos pos,
                                                     Vec3d start, Vec3d end) {
        return getInteractionBox(pos).calculateIntercept(start, end);
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
