package therealpant.thaumicattempts.items;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.tile.TileAnomalousMonolith;
import therealpant.thaumicattempts.world.tile.TileRiftWell;

import javax.annotation.Nullable;
import java.util.List;

public class ItemInterceptorStone extends Item {
    private static final String TAG_LINK = "MonolithLink";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POSITION = "Position";
    private static final String TAG_WORLD = "World";
    private static final int LINK_DURATION = 40;
    private static final double LINK_RANGE_SQ = 8.0D * 8.0D;

    public ItemInterceptorStone() {
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".interceptor_stone");
        setRegistryName(ThaumicAttempts.MODID, "intersepter_stone");
        setMaxStackSize(1);
    }

    public static boolean isLinked(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.hasTagCompound() && stack.getTagCompound().hasKey(TAG_LINK, 10);
    }

    public static int getLinkedDimension(ItemStack stack) {
        NBTTagCompound link = getLink(stack);
        return link == null ? Integer.MIN_VALUE : link.getInteger(TAG_DIMENSION);
    }

    @Nullable
    public static BlockPos getLinkedPosition(ItemStack stack) {
        NBTTagCompound link = getLink(stack);
        return link == null ? null : BlockPos.fromLong(link.getLong(TAG_POSITION));
    }

    public static ItemStack createLinkedStack(World world, BlockPos monolithPos) {
        ItemStack stack = new ItemStack(therealpant.thaumicattempts.init.ModBlocksItems.INTERCEPTOR_STONE);
        bind(stack, world, monolithPos);
        return stack;
    }

    public static boolean installInMonolith(World world, BlockPos pos,
                                             EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemInterceptorStone)) return false;
        if (world.isRemote) return true;

        if (player.isSneaking() && isLinked(stack)) {
            clearLink(stack);
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.unbound"), true);
            return true;
        }

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileAnomalousMonolith)) return true;
        TileAnomalousMonolith monolith = (TileAnomalousMonolith) tile;
        if (isLinked(stack)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.already_linked"), true);
            return true;
        }
        if (monolith.hasInterceptorStone()) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.monolith_occupied"), true);
            return true;
        }

        monolith.installInterceptorStone();
        consumeOne(player, stack);
        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.BLOCKS, 0.7F, 0.85F);
        player.sendStatusMessage(new TextComponentTranslation(
                "thaumicattempts.interceptor_stone.monolith_installed"), true);
        return true;
    }

    public static boolean installInWell(World world, BlockPos wellPos,
                                        EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemInterceptorStone)) return false;
        if (world.isRemote) return true;

        if (player.isSneaking() && isLinked(stack)) {
            clearLink(stack);
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.unbound"), true);
            return true;
        }

        if (!isLinked(stack)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.not_linked"), true);
            return true;
        }
        if (getLinkedDimension(stack) != world.provider.getDimension()) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.rift_well.wrong_dimension"), true);
            return true;
        }

        BlockPos monolithPos = getLinkedPosition(stack);
        if (monolithPos == null) return true;
        world.getChunkProvider().provideChunk(monolithPos.getX() >> 4, monolithPos.getZ() >> 4);
        TileEntity sourceTile = world.getTileEntity(monolithPos);
        TileEntity wellTile = world.getTileEntity(wellPos);
        if (!(sourceTile instanceof TileAnomalousMonolith)
                || !((TileAnomalousMonolith) sourceTile).hasInterceptorStone()) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.monolith_missing"), true);
            return true;
        }
        if (!(wellTile instanceof TileRiftWell)) return true;

        TileRiftWell well = (TileRiftWell) wellTile;
        if (!well.addMonolithLink(monolithPos)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.already_inserted"), true);
            return true;
        }
        consumeOne(player, stack);
        world.playSound(null, wellPos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL,
                SoundCategory.BLOCKS, 0.8F, 1.15F);
        player.sendStatusMessage(new TextComponentTranslation(
                "thaumicattempts.interceptor_stone.well_linked"), true);
        return true;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (isLinked(stack)) {
            if (player.isSneaking()) {
                if (!world.isRemote) {
                    clearLink(stack);
                    player.sendStatusMessage(new TextComponentTranslation(
                            "thaumicattempts.interceptor_stone.unbound"), true);
                }
                return new ActionResult<>(EnumActionResult.SUCCESS, stack);
            }
            if (!world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "thaumicattempts.interceptor_stone.already_linked"), true);
            }
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.isSneaking() || !isLinked(stack)) return EnumActionResult.PASS;
        if (!world.isRemote) {
            clearLink(stack);
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.unbound"), true);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return LINK_DURATION;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.BOW;
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, net.minecraft.entity.EntityLivingBase entity) {
        if (world.isRemote || !(entity instanceof EntityPlayer) || isLinked(stack)) return stack;
        EntityPlayer player = (EntityPlayer) entity;
        TileAnomalousMonolith nearest = null;
        double nearestDistance = LINK_RANGE_SQ;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileAnomalousMonolith)
                    || tile.isInvalid()
                    || !((TileAnomalousMonolith) tile).hasInterceptorStone()) continue;
            double distance = tile.getPos().distanceSq(player.posX, player.posY, player.posZ);
            if (distance <= nearestDistance) {
                nearest = (TileAnomalousMonolith) tile;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "thaumicattempts.interceptor_stone.no_monolith"), true);
            return stack;
        }

        bind(stack, world, nearest.getPos());
        world.playSound(null, player.getPosition(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.PLAYERS, 0.7F, 0.65F);
        player.sendStatusMessage(new TextComponentTranslation(
                "thaumicattempts.interceptor_stone.bound"), true);
        return stack;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return isLinked(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world,
                               List<String> tooltip, ITooltipFlag flag) {
        NBTTagCompound link = getLink(stack);
        if (link == null) return;
        tooltip.add(TextFormatting.AQUA + new TextComponentTranslation(
                "thaumicattempts.interceptor_stone.linked_tooltip").getFormattedText());
        if (GuiScreen.isShiftKeyDown()) {
            BlockPos pos = BlockPos.fromLong(link.getLong(TAG_POSITION));
            tooltip.add(TextFormatting.DARK_AQUA + link.getString(TAG_WORLD)
                    + " " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        }
    }

    private static void bind(ItemStack stack, World world, BlockPos pos) {
        NBTTagCompound link = new NBTTagCompound();
        link.setInteger(TAG_DIMENSION, world.provider.getDimension());
        link.setLong(TAG_POSITION, pos.toLong());
        String worldName = world.provider.getDimensionType().getName();
        link.setString(TAG_WORLD, worldName == null || worldName.isEmpty()
                ? "Dim " + world.provider.getDimension() : worldName);
        stack.setTagInfo(TAG_LINK, link);
    }

    private static void clearLink(ItemStack stack) {
        if (!stack.hasTagCompound()) return;
        stack.getTagCompound().removeTag(TAG_LINK);
        if (stack.getTagCompound().getKeySet().isEmpty()) stack.setTagCompound(null);
    }

    @Nullable
    private static NBTTagCompound getLink(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()
                || !stack.getTagCompound().hasKey(TAG_LINK, 10)) return null;
        return stack.getTagCompound().getCompoundTag(TAG_LINK);
    }

    private static void consumeOne(EntityPlayer player, ItemStack stack) {
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
    }
}
