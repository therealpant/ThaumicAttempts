package therealpant.thaumicattempts.items;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.tile.TileRiftStonePortal;

import javax.annotation.Nullable;
import java.util.List;

public class ItemPortalRune extends Item {
    private static final String TAG_ROOT = "PortalRune";
    private static final String TAG_PRIMARY = "Primary";
    private static final String TAG_SECONDARY = "Secondary";

    private static final String TAG_WORLD = "World";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";

    public ItemPortalRune() {
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".portal_rune");
        setRegistryName(ThaumicAttempts.MODID, "portal_rune");
        setMaxStackSize(1);
    }

    public static boolean hasPrimary(ItemStack stack) {
        return getPoint(stack, TAG_PRIMARY) != null;
    }

    public static boolean hasSecondary(ItemStack stack) {
        return getPoint(stack, TAG_SECONDARY) != null;
    }

    public static boolean bindPrimaryIfEmpty(ItemStack stack, World world, BlockPos pos) {
        if (hasPrimary(stack)) {
            return false;
        }
        setPoint(stack, TAG_PRIMARY, world, pos);
        return true;
    }

    public static void bindSecondary(ItemStack stack, World world, BlockPos pos) {
        setPoint(stack, TAG_SECONDARY, world, pos);
    }

    @Nullable
    public static PortalPoint getPrimary(ItemStack stack) {
        return readPoint(getPoint(stack, TAG_PRIMARY));
    }

    @Nullable
    public static PortalPoint getSecondary(ItemStack stack) {
        return readPoint(getPoint(stack, TAG_SECONDARY));
    }

    @Nullable
    public static PortalPoint getTargetForPortal(ItemStack stack, World world, BlockPos pos) {
        NBTTagCompound primary = getPoint(stack, TAG_PRIMARY);
        if (primary == null) {
            return null;
        }
        if (!samePoint(primary, world, pos)) {
            return readPoint(primary);
        }
        return getSecondary(stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != TABlocks.RIFT_STONE_PORTAL) {
            return EnumActionResult.PASS;
        }

        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (player.isSneaking()) {
                bindPrimaryIfEmpty(stack, world, pos);
            } else {
                openPortalFromRune(stack, world, pos);
            }
        }
        return EnumActionResult.SUCCESS;
    }

    public static boolean openPortalFromRune(ItemStack stack, World world, BlockPos pos) {
        PortalPoint target = getTargetForPortal(stack, world, pos);
        if (target == null) {
            return false;
        }

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileRiftStonePortal)) {
            return false;
        }
        TileRiftStonePortal portal = (TileRiftStonePortal) tile;
        if (portal.isActive()) {
            portal.requestClose();
            return true;
        }

        PortalPoint primary = getPrimary(stack);
        if (primary != null && (primary.dimension != world.provider.getDimension() || !primary.pos.equals(pos))) {
            bindSecondary(stack, world, pos);
        }
        portal.openPortal(target.dimension, target.pos);
        return true;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return hasPrimary(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        if (!GuiScreen.isShiftKeyDown()) {
            return;
        }

        NBTTagCompound primary = getPoint(stack, TAG_PRIMARY);
        NBTTagCompound secondary = getPoint(stack, TAG_SECONDARY);
        if (primary != null) {
            tooltip.add(TextFormatting.AQUA + formatPoint(primary));
        }
        if (secondary != null) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + formatPoint(secondary));
        }
    }

    private static void setPoint(ItemStack stack, String key, World world, BlockPos pos) {
        NBTTagCompound root = stack.getOrCreateSubCompound(TAG_ROOT);
        NBTTagCompound point = new NBTTagCompound();
        point.setString(TAG_WORLD, getWorldName(world));
        point.setInteger(TAG_DIM, world.provider.getDimension());
        point.setInteger(TAG_X, pos.getX());
        point.setInteger(TAG_Y, pos.getY());
        point.setInteger(TAG_Z, pos.getZ());
        root.setTag(key, point);
    }

    @Nullable
    private static NBTTagCompound getPoint(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        NBTTagCompound root = stack.getSubCompound(TAG_ROOT);
        if (root == null || !root.hasKey(key, 10)) {
            return null;
        }
        return root.getCompoundTag(key);
    }

    private static boolean samePoint(NBTTagCompound point, World world, BlockPos pos) {
        return point.getInteger(TAG_DIM) == world.provider.getDimension()
                && point.getInteger(TAG_X) == pos.getX()
                && point.getInteger(TAG_Y) == pos.getY()
                && point.getInteger(TAG_Z) == pos.getZ();
    }

    private static String formatPoint(NBTTagCompound point) {
        return point.getString(TAG_WORLD)
                + " " + point.getInteger(TAG_X)
                + ", " + point.getInteger(TAG_Y)
                + ", " + point.getInteger(TAG_Z);
    }

    private static String getWorldName(World world) {
        String name = world.provider.getDimensionType().getName();
        return name == null || name.isEmpty() ? ("Dim " + world.provider.getDimension()) : name;
    }

    @Nullable
    private static PortalPoint readPoint(@Nullable NBTTagCompound point) {
        if (point == null) {
            return null;
        }
        return new PortalPoint(
                point.getString(TAG_WORLD),
                point.getInteger(TAG_DIM),
                new BlockPos(
                        point.getInteger(TAG_X),
                        point.getInteger(TAG_Y),
                        point.getInteger(TAG_Z)
                )
        );
    }

    public static final class PortalPoint {
        public final String worldName;
        public final int dimension;
        public final BlockPos pos;

        private PortalPoint(String worldName, int dimension, BlockPos pos) {
            this.worldName = worldName;
            this.dimension = dimension;
            this.pos = pos;
        }
    }
}
