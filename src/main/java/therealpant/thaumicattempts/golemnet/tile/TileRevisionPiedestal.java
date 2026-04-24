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
import net.minecraftforge.items.ItemStackHandler;
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
import therealpant.thaumicattempts.api.TerminalOrderApi;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;


import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

public class TileRevisionPiedestal extends TileEntity implements IAnimatable, ITickable {

    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_COUNTER = "Counter";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_MANAGER_POS = "ManagerPos";
    private static final String TAG_NEXT_RETRY_TICK = "NextRetryTick";
    private static final String TAG_LAST_ORDER_AVAILABLE = "LastOrderAvailable";
    private static final String TAG_AWAITING_ORDER_COMPLETION = "AwaitingOrderCompletion";
    private static final String TAG_IMPOSSIBLE_ORDER_LOCKED = "ImpossibleOrderLocked";
    private static final String TAG_OUTPUT = "Output";
    private static final String TAG_PENDING_CRAFT = "PendingCraft";
    private static final String TAG_SUPPRESS_RECONCILE_DEPTH = "SuppressReconcileDepth";
    private static final long SUCCESS_COOLDOWN_TICKS = 100L;
    private static final long RETRY_COOLDOWN_TICKS = 20L;

    private final AnimationFactory factory = new AnimationFactory(this);
    private boolean active = true;
    private ItemStack pedestalItem = ItemStack.EMPTY;
    private static final String TAG_LAST_REDSTONE_POWERED = "LastRedstonePowered";
    private int counter = 1;
    private int outSignal = 0;
    private int tickAccumulator = 0;
    private long nextRetryTick = 0L;
    private int lastOrderAvailable = -1;
    private boolean awaitingOrderCompletion = false;
    private boolean impossibleOrderLocked = false;
    private boolean lastRedstonePowered = false;
    @Nullable
    private BlockPos managerPos = null;
    private int suppressOutputReconcileDepth = 0;
    private final LinkedHashMap<therealpant.thaumicattempts.util.ItemKey, Integer> pendingCraft = new LinkedHashMap<>();
    private final ItemStackHandler output = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
            if (world != null && !world.isRemote) {
                if (suppressOutputReconcileDepth <= 0) {
                    reconcilePendingByOutputInstant();
                }
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
    };
    private final IItemHandler extractOnlyDown = new IItemHandler() {
        @Override
        public int getSlots() { return output.getSlots(); }
        @Override
        public ItemStack getStackInSlot(int slot) { return output.getStackInSlot(slot); }
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return output.extractItem(slot, amount, simulate); }
        @Override
        public int getSlotLimit(int slot) { return output.getSlotLimit(slot); }
    };

    public boolean isActive() {
        return active;
    }

    public void toggleActive() {
        setActive(!active);
    }

    public void setActive(boolean active) {
        boolean changed = this.active != active;
        this.active = active;
        if (changed) {
            fullResetForModeSwitch();
        }
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
        if (world == null || world.isRemote) return;
        if (!pedestalItem.isEmpty()) {
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), pedestalItem.copy());
            pedestalItem = ItemStack.EMPTY;
        }
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (!stack.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                output.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        resetOrderTracking();
        recalcSignalAndNotify(true);
        markDirty();
    }

    private void resetOrderTracking() {
        nextRetryTick = 0L;
        lastOrderAvailable = -1;
        awaitingOrderCompletion = false;
        impossibleOrderLocked = false;
        pendingCraft.clear();
    }

    private void fullResetForModeSwitch() {
        resetOrderTracking();
        lastRedstonePowered = false;
        suppressOutputReconcileDepth = 0;
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
        if (compound.hasKey(TAG_OUTPUT)) {
            output.deserializeNBT(compound.getCompoundTag(TAG_OUTPUT));
        }
        outSignal = Math.max(0, Math.min(15, compound.getInteger("OutSignal")));
        managerPos = compound.hasKey(TAG_MANAGER_POS) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER_POS)) : null;
        nextRetryTick = compound.getLong(TAG_NEXT_RETRY_TICK);
        lastOrderAvailable = compound.hasKey(TAG_LAST_ORDER_AVAILABLE) ? compound.getInteger(TAG_LAST_ORDER_AVAILABLE) : -1;
        awaitingOrderCompletion = compound.getBoolean(TAG_AWAITING_ORDER_COMPLETION);
        impossibleOrderLocked = compound.getBoolean(TAG_IMPOSSIBLE_ORDER_LOCKED);
        lastRedstonePowered = compound.getBoolean(TAG_LAST_REDSTONE_POWERED);
        suppressOutputReconcileDepth = Math.max(0, compound.getInteger(TAG_SUPPRESS_RECONCILE_DEPTH));
        readPendingMap(compound, TAG_PENDING_CRAFT, pendingCraft);
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
        compound.setTag(TAG_OUTPUT, output.serializeNBT());
        if (managerPos != null) {
            compound.setLong(TAG_MANAGER_POS, managerPos.toLong());
        }
        compound.setLong(TAG_NEXT_RETRY_TICK, Math.max(0L, nextRetryTick));
        if (lastOrderAvailable >= 0) {
            compound.setInteger(TAG_LAST_ORDER_AVAILABLE, lastOrderAvailable);
        }
        compound.setBoolean(TAG_AWAITING_ORDER_COMPLETION, awaitingOrderCompletion);
        compound.setBoolean(TAG_IMPOSSIBLE_ORDER_LOCKED, impossibleOrderLocked);
        compound.setBoolean(TAG_LAST_REDSTONE_POWERED, lastRedstonePowered);
        compound.setInteger(TAG_SUPPRESS_RECONCILE_DEPTH, Math.max(0, suppressOutputReconcileDepth));
        writePendingMap(compound, TAG_PENDING_CRAFT, pendingCraft);
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
        if (!(te instanceof TileMirrorManager)) {
            lastRedstonePowered = powered;
            return;
        }

        ItemStack requested = getRequestedItem();

        long now = world.getTotalWorldTime();

        if (awaitingOrderCompletion) {
            reconcilePendingByOutputInstant();
            if (pendingCraft.isEmpty()) {
                if (isOutputEmpty()) {
                    awaitingOrderCompletion = false;
                    lastOrderAvailable = -1;
                    nextRetryTick = now + SUCCESS_COOLDOWN_TICKS;
                    markDirtyAndSync();
                }
            } else {
                markDirtyAndSync();
            }
            return;
        }

        if (!powered) {
            return;
        }

        if (now < nextRetryTick) {
            return;
        }

        if (!isOutputEmpty()) {
            return;
        }

        int orderBatch = Math.max(1, counter);

        if (requested.isEmpty()) {
            impossibleOrderLocked = false;
            lastOrderAvailable = -1;
            nextRetryTick = now + RETRY_COOLDOWN_TICKS;
            markDirtyAndSync();
            return;
        }

        requested.setCount(1);
        ItemKey requestedKey = ItemKey.of(requested);
        if (requestedKey == ItemKey.EMPTY) {
            impossibleOrderLocked = false;
            lastOrderAvailable = -1;
            nextRetryTick = now + RETRY_COOLDOWN_TICKS;
            markDirtyAndSync();
            return;
        }

        LinkedHashMap<ItemKey, Integer> acceptedByOutput = CloudOrderSubmitHelper.submitBatchCraft(
                world,
                managerPos,
                this.pos,
                -1,
                Collections.singletonList(new java.util.AbstractMap.SimpleEntry<>(requestedKey, orderBatch))
        );

        int accepted = 0;
        for (Map.Entry<ItemKey, Integer> e : acceptedByOutput.entrySet()) {
            int take = Math.max(0, e.getValue());
            if (take <= 0) continue;
            pendingCraft.merge(e.getKey(), take, Integer::sum);
            accepted += take;
        }

        if (accepted > 0) {
            lastOrderAvailable = -1;
            awaitingOrderCompletion = true;
            impossibleOrderLocked = false;
            nextRetryTick = 0L;
            markDirtyAndSync();
        } else {
            awaitingOrderCompletion = false;
            impossibleOrderLocked = false;
            lastOrderAvailable = -1;
            nextRetryTick = now + RETRY_COOLDOWN_TICKS;
            markDirtyAndSync();
        }
    }

    public void beginManagerBufferInsert() { suppressOutputReconcileDepth++; }
    public void endManagerBufferInsert() { if (suppressOutputReconcileDepth > 0) suppressOutputReconcileDepth--; }

    public void onDelivered(ItemStack like, int count) {
        if (like == null || like.isEmpty() || count <= 0 || pendingCraft.isEmpty()) return;
        therealpant.thaumicattempts.util.ItemKey key = findMatchingKeyRelaxed(pendingCraft, like);
        if (key == null) return;
        int have = Math.max(0, pendingCraft.getOrDefault(key, 0));
        int take = Math.min(have, count);
        int left = have - take;
        if (left <= 0) pendingCraft.remove(key); else pendingCraft.put(key, left);
        markDirty();
    }

    private boolean isOutputEmpty() {
        for (int i = 0; i < output.getSlots(); i++) {
            if (!output.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    private void reconcilePendingByOutputInstant() {
        if (pendingCraft.isEmpty()) return;
        Map<therealpant.thaumicattempts.util.ItemKey, Integer> updates = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<therealpant.thaumicattempts.util.ItemKey, Integer> e : pendingCraft.entrySet()) {
            int have = countLikeInHandler(output, e.getKey().toStack(1));
            int left = Math.max(0, e.getValue() - have);
            if (left > 0) updates.put(e.getKey(), left);
            if (left != e.getValue()) changed = true;
        }
        if (changed) {
            pendingCraft.clear();
            pendingCraft.putAll(updates);
            markDirty();
        }
    }
    @Nullable
    private therealpant.thaumicattempts.util.ItemKey findMatchingKeyRelaxed(Map<therealpant.thaumicattempts.util.ItemKey, Integer> map, ItemStack like) {
        if (like == null || like.isEmpty() || map == null || map.isEmpty()) return null;
        therealpant.thaumicattempts.util.ItemKey exact = therealpant.thaumicattempts.util.ItemKey.of(like);
        if (map.containsKey(exact)) return exact;
        for (therealpant.thaumicattempts.util.ItemKey k : map.keySet()) {
            if (ResourceIdentity.sameResource(k.toStack(1), like)) return k;
        }
        return null;
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

    private static void writePendingMap(NBTTagCompound to, String key, Map<therealpant.thaumicattempts.util.ItemKey, Integer> map) {
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (Map.Entry<therealpant.thaumicattempts.util.ItemKey, Integer> e : map.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("k", e.getKey().toStack(1).writeToNBT(new NBTTagCompound()));
            tag.setInteger("c", Math.max(1, e.getValue()));
            list.appendTag(tag);
        }
        to.setTag(key, list);
    }

    private static void readPendingMap(NBTTagCompound from, String key, Map<therealpant.thaumicattempts.util.ItemKey, Integer> out) {
        out.clear();
        if (!from.hasKey(key)) return;
        net.minecraft.nbt.NBTTagList list = from.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag.getCompoundTag("k"));
            if (stack.isEmpty()) continue;
            out.put(therealpant.thaumicattempts.util.ItemKey.of(stack), Math.max(1, tag.getInteger("c")));
        }
    }

    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == EnumFacing.DOWN) return (T) extractOnlyDown;
            return (T) output;
        }
        return super.getCapability(capability, facing);
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