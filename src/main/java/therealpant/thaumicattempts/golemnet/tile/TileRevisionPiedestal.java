package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import thaumcraft.api.blocks.BlocksTC;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.api.AutomationOrderSubmitHelper;
import therealpant.thaumicattempts.api.ITerminalOrderIconProvider;
import therealpant.thaumicattempts.api.ITerminalOrderAcceptor;
import therealpant.thaumicattempts.api.TerminalOrderApi;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.util.ResourceIdentity;


import javax.annotation.Nullable;
import java.util.List;

public class TileRevisionPiedestal extends TileEntity implements IAnimatable, ITickable {

    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_COUNTER = "Counter";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_MANAGER_POS = "ManagerPos";
    private static final String TAG_NEXT_RETRY_TICK = "NextRetryTick";
    private static final String TAG_LAST_ORDER_AVAILABLE = "LastOrderAvailable";
    private static final String TAG_AWAITING_ORDER_COMPLETION = "AwaitingOrderCompletion";

    private final AnimationFactory factory = new AnimationFactory(this);
    private boolean active = true;
    private ItemStack pedestalItem = ItemStack.EMPTY;
    private static final String TAG_LAST_REDSTONE_POWERED = "LastRedstonePowered";
    private static final String TAG_ORDER_TARGET_POS = "OrderTargetPos";
    private static final String TAG_ORDER_TARGET_SLOT = "OrderTargetSlot";
    private static final String TAG_ORDER_TARGET_BASELINE = "OrderTargetBaseline";
    private int counter = 1;
    private int outSignal = 0;
    private int tickAccumulator = 0;
    private long nextRetryTick = 0L;
    private int lastOrderAvailable = -1;
    private boolean awaitingOrderCompletion = false;
    private boolean lastRedstonePowered = false;
    @Nullable
    private BlockPos orderTargetPos = null;
    private int orderTargetSlot = -1;
    private int orderTargetBaseline = -1;
    @Nullable
    private BlockPos managerPos = null;

    public boolean isActive() {
        return active;
    }

    public void toggleActive() {
        setActive(!active);
    }

    public void setActive(boolean active) {
        this.active = active;
        recalcSignalAndNotify(true);
        markDirtyAndSync();
    }

    public ItemStack getPedestalItem() {
        return pedestalItem;
    }

    public ItemStack getRequestedItem() {
        if (pedestalItem == null || pedestalItem.isEmpty()) return ItemStack.EMPTY;
        ItemStack requested = TerminalOrderApi.stripOrderIconData(pedestalItem);
        if (requested == null || requested.isEmpty()) return ItemStack.EMPTY;
        ItemStack one = requested.copy();
        one.setCount(1);
        return one;
    }

    public int getCounter() {
        return counter;
    }

    public int getMultiplier() {
        if (world == null) return 1;
        IBlockState below = world.getBlockState(pos.down());
        Block b = below.getBlock();
        if (b == BlocksTC.stoneArcane) return 10;
        if (b == BlocksTC.stoneEldritchTile) return 64;
        return 1;
    }

    public int getThreshold() {
        return Math.max(1, counter) * Math.max(1, getMultiplier());
    }

    public int getOutSignal() {
        return outSignal;
    }

    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos managerPos) {
        this.managerPos = managerPos == null ? null : managerPos.toImmutable();
        recalcSignalAndNotify(true);
        markDirtyAndSync();
    }

    public void clearManagerPosFromManager(BlockPos managerPos) {
        if (this.managerPos != null && this.managerPos.equals(managerPos)) {
            this.managerPos = null;
            recalcSignalAndNotify(true);
            markDirtyAndSync();
        }
    }

    public void cycleCounter() {
        counter++;
        if (counter > 16) {
            counter = 1;
        }
        recalcSignalAndNotify(true);
        markDirtyAndSync();
    }

    public boolean tryInsertFromHand(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty() || !pedestalItem.isEmpty()) return false;

        pedestalItem = held.copy();
        pedestalItem.setCount(1);
        resetOrderTracking();
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        recalcSignalAndNotify(true);
        markDirtyAndSync();
        return true;
    }

    public boolean tryExtractToPlayer(EntityPlayer player) {
        if (player == null || pedestalItem.isEmpty()) return false;

        ItemStack out = pedestalItem.copy();
        pedestalItem = ItemStack.EMPTY;
        resetOrderTracking();
        if (!player.inventory.addItemStackToInventory(out) && world != null) {
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), out);
        }
        recalcSignalAndNotify(true);
        markDirtyAndSync();
        return true;
    }

    public void dropContents() {
        if (world == null || world.isRemote || pedestalItem.isEmpty()) return;
        InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), pedestalItem.copy());
        pedestalItem = ItemStack.EMPTY;
        resetOrderTracking();
        recalcSignalAndNotify(true);
        markDirty();
    }

    private void resetOrderTracking() {
        nextRetryTick = 0L;
        lastOrderAvailable = -1;
        awaitingOrderCompletion = false;
        orderTargetPos = null;
        orderTargetSlot = -1;
        orderTargetBaseline = -1;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this,
                "main_controller",
                0,
                this::animationPredicate
        ));
    }

    private <E extends IAnimatable> PlayState animationPredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation.revision.1", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        active = !compound.hasKey(TAG_ACTIVE) || compound.getBoolean(TAG_ACTIVE);
        counter = compound.hasKey(TAG_COUNTER) ? compound.getInteger(TAG_COUNTER) : 1;
        if (counter < 1) counter = 1;
        if (counter > 16) counter = 16;
        pedestalItem = compound.hasKey(TAG_ITEM) ? new ItemStack(compound.getCompoundTag(TAG_ITEM)) : ItemStack.EMPTY;
        outSignal = Math.max(0, Math.min(15, compound.getInteger("OutSignal")));
        managerPos = compound.hasKey(TAG_MANAGER_POS) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER_POS)) : null;
        nextRetryTick = compound.getLong(TAG_NEXT_RETRY_TICK);
        lastOrderAvailable = compound.hasKey(TAG_LAST_ORDER_AVAILABLE) ? compound.getInteger(TAG_LAST_ORDER_AVAILABLE) : -1;
        awaitingOrderCompletion = compound.getBoolean(TAG_AWAITING_ORDER_COMPLETION);
        lastRedstonePowered = compound.getBoolean(TAG_LAST_REDSTONE_POWERED);
        orderTargetPos = compound.hasKey(TAG_ORDER_TARGET_POS) ? BlockPos.fromLong(compound.getLong(TAG_ORDER_TARGET_POS)) : null;
        orderTargetSlot = compound.hasKey(TAG_ORDER_TARGET_SLOT) ? compound.getInteger(TAG_ORDER_TARGET_SLOT) : -1;
        orderTargetBaseline = compound.hasKey(TAG_ORDER_TARGET_BASELINE) ? compound.getInteger(TAG_ORDER_TARGET_BASELINE) : -1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean(TAG_ACTIVE, active);
        compound.setInteger(TAG_COUNTER, counter);
        compound.setInteger("OutSignal", outSignal);
        if (!pedestalItem.isEmpty()) {
            compound.setTag(TAG_ITEM, pedestalItem.writeToNBT(new NBTTagCompound()));
        }
        if (managerPos != null) {
            compound.setLong(TAG_MANAGER_POS, managerPos.toLong());
        }
        compound.setLong(TAG_NEXT_RETRY_TICK, Math.max(0L, nextRetryTick));
        if (lastOrderAvailable >= 0) {
            compound.setInteger(TAG_LAST_ORDER_AVAILABLE, lastOrderAvailable);
        }
        compound.setBoolean(TAG_AWAITING_ORDER_COMPLETION, awaitingOrderCompletion);
        compound.setBoolean(TAG_LAST_REDSTONE_POWERED, lastRedstonePowered);
        if (orderTargetPos != null) {
            compound.setLong(TAG_ORDER_TARGET_POS, orderTargetPos.toLong());
        }
        if (orderTargetSlot >= 0) {
            compound.setInteger(TAG_ORDER_TARGET_SLOT, orderTargetSlot);
        }
        if (orderTargetBaseline >= 0) {
            compound.setInteger(TAG_ORDER_TARGET_BASELINE, orderTargetBaseline);
        }
        return compound;
    }

    private void recalcSignalAndNotify(boolean notifyNeighbors) {
        int newSignal = 0;
        ItemStack requested = getRequestedItem();
        if (active && world != null && !world.isRemote && managerPos != null && !requested.isEmpty()) {
            TileEntity te = world.getTileEntity(managerPos);
            if (te instanceof TileMirrorManager) {
                int count = ((TileMirrorManager) te).getAvailableCountFor(requested);
                int threshold = getThreshold();
                if (count > 0 && threshold > 0) {
                    newSignal = Math.max(1, Math.min(15, (count * 15) / threshold));
                }
            }
        }

        if (newSignal != outSignal) {
            outSignal = newSignal;
            markDirtyAndSync();
            if (notifyNeighbors && world != null) {
                world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), true);
            }
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++tickAccumulator >= 10) {
            tickAccumulator = 0;
            recalcSignalAndNotify(true);
        }

        boolean powered = world.isBlockPowered(pos);
        if (active || managerPos == null || pedestalItem.isEmpty()) {
            lastRedstonePowered = powered;
            return;
        }
        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return;

        ItemStack requested = getRequestedItem();
        if (requested.isEmpty()) return;

        TileMirrorManager manager = (TileMirrorManager) te;
        int available = Math.max(0, manager.getAvailableCountFor(requested));

        int cap = getThreshold();
        if (available >= cap) {
            resetOrderTracking();
            return;
        }

        long now = world.getTotalWorldTime();

        if (awaitingOrderCompletion) {
            if (isOrderCraftCompleted(requested)) {
                awaitingOrderCompletion = false;
                lastOrderAvailable = -1;
                nextRetryTick = now + 100L;
                orderTargetPos = null;
                orderTargetSlot = -1;
                orderTargetBaseline = -1;// 5 секунд после подтверждённого завершения крафта
                markDirtyAndSync();
            }
            lastRedstonePowered = powered;
            return;
        }

        if (!powered || lastRedstonePowered) {
            lastRedstonePowered = powered;
            return;
        }

        if (now < nextRetryTick) return;

        int deficit = Math.max(1, cap - available);
        int orderBatch = Math.max(1, Math.min(counter, deficit));

        int accepted = trySubmitRestockOrder(orderBatch, requested);
        if (accepted > 0) {
            lastOrderAvailable = available;
            awaitingOrderCompletion = true;
            nextRetryTick = 0L;
            markDirtyAndSync();
        } else {
            nextRetryTick = now + 200L;
            markDirty();
        }
        lastRedstonePowered = powered;
    }

    private int trySubmitRestockOrder(int items, ItemStack requested) {
        if (items <= 0 || pedestalItem.isEmpty() || world == null) return 0;
        ItemStack orderIcon = resolveOrderIconForCurrentItem();
        if (orderIcon.isEmpty()) return 0;

        BlockPos targetPos = TerminalOrderApi.getOrderIconPos(orderIcon);
        int slot = TerminalOrderApi.getOrderIconSlot(orderIcon);
        if (targetPos == null || slot < 0) return 0;

        int accepted = AutomationOrderSubmitHelper.submitAutomationOrderByIcon(
                world,
                orderIcon,
                items,
                managerPos,
                this.pos,
                -1
        );
        if (accepted > 0) {
            orderTargetPos = targetPos.toImmutable();
            orderTargetSlot = slot;
            orderTargetBaseline = countCraftOutputForTarget(targetPos, slot, requested);
            return accepted;
        }

        TileEntity te = world.getTileEntity(targetPos);
        if (!(te instanceof ITerminalOrderAcceptor)) return 0;

        ((ITerminalOrderAcceptor) te).triggerFromTerminal(slot, items);
        orderTargetPos = targetPos.toImmutable();
        orderTargetSlot = slot;
        orderTargetBaseline = countCraftOutputForTarget(targetPos, slot, requested);
        return Math.max(1, items);
    }

    private boolean isOrderCraftCompleted(ItemStack requested) {
        if (requested.isEmpty() || orderTargetPos == null || orderTargetSlot < 0 || orderTargetBaseline < 0) return false;
        return countCraftOutputForTarget(orderTargetPos, orderTargetSlot, requested) > orderTargetBaseline;
    }

    private int countCraftOutputForTarget(BlockPos targetPos, int slot, ItemStack requested) {
        if (world == null || targetPos == null || requested.isEmpty()) return 0;

        TileEntity targetTe = world.getTileEntity(targetPos);
        if (targetTe instanceof TilePatternRequester) {
            TileEntity below = world.getTileEntity(targetPos.down());
            if (below instanceof TileEntityGolemCrafter) {
                return countLikeInHandler(((TileEntityGolemCrafter) below).getOutputHandler(), requested);
            }
        }
        if (targetTe instanceof TileResourceRequester) {
            return countLikeInHandler(((TileResourceRequester) targetTe).getBufferHandler(), requested);
        }
        if (targetTe instanceof TileInfusionRequester) {
            return countLikeInHandler(((TileInfusionRequester) targetTe).getResultHandler(), requested);
        }

        int byCapability = countLikeInHandler(targetTe != null ? targetTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) : null, requested);
        if (byCapability > 0) return byCapability;

        int best = 0;
        for (EnumFacing facing : EnumFacing.values()) {
            IItemHandler side = targetTe == null ? null : targetTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing);
            best = Math.max(best, countLikeInHandler(side, requested));
        }
        return best;
    }

    private int countLikeInHandler(@Nullable IItemHandler handler, ItemStack like) {
        if (handler == null || like == null || like.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack in = handler.getStackInSlot(i);
            if (in.isEmpty()) continue;
            if (ResourceIdentity.sameResource(in, like)) {
                count += in.getCount();
            }
        }
        return count;
    }

    private ItemStack resolveOrderIconForCurrentItem() {
        if (pedestalItem.isEmpty() || world == null) return ItemStack.EMPTY;
        if (TerminalOrderApi.isOrderIcon(pedestalItem)) return pedestalItem;
        if (managerPos == null) return ItemStack.EMPTY;

        TileEntity managerTe = world.getTileEntity(managerPos);
        if (!(managerTe instanceof TileMirrorManager)) return ItemStack.EMPTY;

        ItemStack requested = getRequestedItem();
        if (requested.isEmpty()) return ItemStack.EMPTY;

        for (BlockPos requesterPos : ((TileMirrorManager) managerTe).getRequestersSnapshot()) {
            TileEntity requesterTe = world.getTileEntity(requesterPos);
            if (!(requesterTe instanceof ITerminalOrderIconProvider)) continue;

            List<ItemStack> icons = ((ITerminalOrderIconProvider) requesterTe).listTerminalOrderIcons();
            if (icons == null || icons.isEmpty()) continue;

            for (ItemStack icon : icons) {
                if (icon == null || icon.isEmpty() || !TerminalOrderApi.isOrderIcon(icon)) continue;
                ItemStack preview = TerminalOrderApi.stripOrderIconData(icon);
                if (preview.isEmpty()) continue;
                if (!ResourceIdentity.sameResource(preview, requested)) continue;
                ItemStack one = icon.copy();
                one.setCount(1);
                return one;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt != null && pkt.getNbtCompound() != null) {
            handleUpdateTag(pkt.getNbtCompound());
        }
    }
}