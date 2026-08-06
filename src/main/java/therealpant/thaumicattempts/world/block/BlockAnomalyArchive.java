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
import thaumcraft.api.items.ItemsTC;
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
        setBlockUnbreakable();
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
        int hiddenBone = ((TileAnomalyArchive) tile).hideRandomVisibleBoneAndGetId();
        if (hiddenBone >= 0) {
            float rewardRoll = world.rand.nextFloat();
            if (rewardRoll >= 0.30F) {
                ItemStack reward;
                if (rewardRoll < 0.35F) {
                    reward = new ItemStack(ItemsTC.curio, 1, 4);
                } else {
                    reward = world.rand.nextInt(5) == 0
                            ? new ItemStack(ModBlocksItems.ARCHIVE_FRAGMENTS)
                            : new ItemStack(ModBlocksItems.RIFT_STONE);
                }
                spawnRewardAtBone(world, pos, hiddenBone, reward);
            }
        }
        return true;
    }

    private static void spawnRewardAtBone(World world, BlockPos pos,
                                          int bone, ItemStack reward) {
        double[] local = getBoneCenter(bone);

        // The three tablet sections complete one revolution every 18 seconds.
        // Apply the same rotation to the GEO bone center on the logical side.
        double phase = -2.0D * Math.PI
                * (world.getTotalWorldTime() % 360L) / 360.0D;
        double cos = Math.cos(phase);
        double sin = Math.sin(phase);
        double rotatedX = local[0] * cos + local[2] * sin;
        double rotatedZ = -local[0] * sin + local[2] * cos;

        // Move the item a little outside the tablet so it cannot remain inside
        // the two-pixel-thick geometry after appearing at the removed fragment.
        double horizontalLength = Math.sqrt(rotatedX * rotatedX + rotatedZ * rotatedZ);
        double outwardX = horizontalLength > 1.0E-4D ? rotatedX / horizontalLength : 0.0D;
        double outwardZ = horizontalLength > 1.0E-4D ? rotatedZ / horizontalLength : 0.0D;
        double x = pos.getX() + 0.5D + rotatedX + outwardX * 0.18D;
        double y = pos.getY() + 0.01D + local[1];
        double z = pos.getZ() + 0.5D + rotatedZ + outwardZ * 0.18D;
        EntityItem drop = new EntityItem(world, x, y, z, reward);
        drop.motionX = outwardX * 0.06D;
        drop.motionY = 0.04D;
        drop.motionZ = outwardZ * 0.06D;
        drop.setDefaultPickupDelay();
        world.spawnEntity(drop);
    }

    /** Centers of the removable GEO bones, in block units before idle rotation. */
    private static double[] getBoneCenter(int bone) {
        switch (bone) {
            case 17: return new double[] {-1.062D, 3.469D, 0.500D};
            case 18: return new double[] {-1.062D, 2.703D, -0.016D};
            case 19: return new double[] {-1.094D, 3.125D, 0.188D};
            case 20: return new double[] {-1.062D, 2.969D, 0.094D};
            case 16: return new double[] {0.261D, 3.906D, -1.076D};
            case 21: return new double[] {0.572D, 3.266D, -0.897D};
            case 22: return new double[] {0.384D, 3.500D, -1.041D};
            case 23: return new double[] {0.423D, 3.531D, -0.983D};
            case 27: return new double[] {0.964D, 2.531D, 0.670D};
            case 28: return new double[] {0.491D, 2.047D, 0.944D};
            case 31: return new double[] {0.709D, 2.125D, 0.853D};
            case 32: return new double[] {0.640D, 2.094D, 0.858D};
            default: return new double[] {0.0D, 1.2D, 0.0D};
        }
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
