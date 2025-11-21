package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.builder.Animation;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.api.golems.GolemHelper;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.util.ItemKey;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.ItemTCEssentiaContainer;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nullable;
import java.util.*;

public class TileResourceRequester extends TileEntity implements ITickable, IAnimatable {

    private static final int PATTERN_SLOT_COUNT = 15;
    private static final int REQUEST_STALE_TICKS = 100;
    private static final int REQUEST_RETRY_TICKS = 200;
    private static final int PROVISION_MIN_INTERVAL = 5;

    private boolean prevWaitingFlag = false;
    private int animLogicPhase = 0;
    // ===== Geckolib =====
    private final AnimationFactory factory = new AnimationFactory(this);
    // последнее известное состояние "есть ли ожидания"
    private boolean lastWaiting = false;

    private static final String ORDER_TAG_ROOT = "thaumicattempts_rr";
    private static final String ORDER_TAG_POS = "Pos";
    private static final String ORDER_TAG_SLOT = "Slot";

    private final ItemStackHandler patterns = new ItemStackHandler(PATTERN_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemResourceList;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }
    };

    private final ItemStackHandler buffer = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }
    };

    private final LinkedHashMap<ItemKey, Integer> pending = new LinkedHashMap<>();
    private final LinkedHashMap<ItemKey, Integer> baselines = new LinkedHashMap<>();
    private final List<ItemStack> sequence = new ArrayList<>();
    private final Deque<ItemStack> provisionQueue = new ArrayDeque<>();

    private static final class QueuedTrigger {
        int slot;
        int count;
        QueuedTrigger(int slot, int count) {
            this.slot = slot;
            this.count = count;
        }
    }

    // === Состояния анимации крышки ===
    private enum CapAnimPhase {
        IDLE,       // ничто не играет
        OPENING,    // cap1
        LOOPING,    // cap2 циклически
        CLOSING     // cap3
    }

    private CapAnimPhase capPhase = CapAnimPhase.IDLE;
    // флаг "закрыть после текущего цикла cap2"
    private boolean capShouldCloseAfterLoop = false;

    private final Deque<QueuedTrigger> queuedSignals = new ArrayDeque<>();

    private boolean jobActive = false;
    private int activeSlot = -1;

    private int lastSignal = 0;
    private int tickCounter = 0;
    private int lastEnsureTick = -9999;
    private boolean needEnsure = false;
    private int lastProgressTick = 0;
    private int lastRequestTick = 0;
    private int nextProvisionTick = 0;

    private @Nullable BlockPos managerPos = null;
    private boolean managerFromPattern = false;

    public ItemStackHandler getPatternHandler() { return patterns; }
    public ItemStackHandler getBufferHandler() { return buffer; }

    public boolean tryInsertPattern(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemResourceList)) return false;
        for (int i = 0; i < patterns.getSlots(); i++) {
            if (patterns.getStackInSlot(i).isEmpty()) {
                patterns.setStackInSlot(i, stack.copy());
                markDirtyAndSync();
                return true;
            }
        }
        return false;
    }

    public ItemStack tryExtractPattern() {
        for (int i = patterns.getSlots() - 1; i >= 0; i--) {
            ItemStack stack = patterns.getStackInSlot(i);
            if (!stack.isEmpty()) {
                patterns.setStackInSlot(i, ItemStack.EMPTY);
                markDirtyAndSync();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void update() {
        if (world == null) return;

        if (!world.isRemote) {
            syncManagerFromPattern();
        }

        if (world.isRemote) return;

        tickCounter++;

        int signal = readSignal();
        if (signal > 0 && signal != lastSignal) {
            int idx = patternIndexFromSignal(signal);
            if (idx >= 0) {
                enqueueTrigger(idx, 1);
                tryStartQueuedJob();
            }
        }
        lastSignal = signal;

        if (!jobActive) {
            tryStartQueuedJob();
        }

        if (jobActive) {
            reconcilePending();
            ensurePendingWithManager(20);

            if (!useManagerForProvision()) {
                drainProvisionQueue();
            } else if (!provisionQueue.isEmpty()) {
                provisionQueue.clear();
                markDirtyAndSync();
            }

            if (!pending.isEmpty()) {
                if ((tickCounter - lastProgressTick) > REQUEST_STALE_TICKS
                        && (tickCounter - lastRequestTick) >= REQUEST_RETRY_TICKS) {
                    requestProvisionForPending();
                }
            } else {
                deliverSequence();
                clearJob();
            }
        }
    }

    private boolean tryStartJob(int slot) {
        ItemStack pattern = patterns.getStackInSlot(slot);
        if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) return false;

        pending.clear();
        baselines.clear();
        sequence.clear();
        lastProgressTick = tickCounter;

        NonNullList<ItemStack> grid = ItemResourceList.readGrid(pattern);
        for (ItemStack s : grid) {
            if (s == null || s.isEmpty()) continue;
            ItemStack copy = s.copy();
            if (copy.getCount() <= 0) copy.setCount(1);
            sequence.add(copy);
            ItemKey key = ItemKey.of(copy);
            int total = pending.getOrDefault(key, 0) + copy.getCount();
            pending.put(key, total);
        }

        if (pending.isEmpty()) {
            sequence.clear();
            return false;
        }

        // учтём содержимое буфера
        for (Iterator<Map.Entry<ItemKey, Integer>> it = pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ItemKey, Integer> e = it.next();
            ItemStack like = e.getKey().toStack(1);
            int have = countInBufferLike(like);
            int take = Math.min(Math.max(0, e.getValue()), Math.max(0, have));
            baselines.put(e.getKey(), take);
            int left = e.getValue() - take;
            if (left <= 0) {
                it.remove();
            } else {
                e.setValue(left);
            }
        }

        jobActive = true;
        activeSlot = slot;
        needEnsure = true;
        ensurePendingWithManager(0);
        if (!useManagerForProvision()) {
            resetProvisionQueueFromPending();
        }
        markDirtyAndSync();

        if (pending.isEmpty()) {
            deliverSequence();
            clearJob();
            return true;
        }

        requestProvisionForSlot(slot);

        return true;
    }

    private void clearJob() {
        pending.clear();
        baselines.clear();
        sequence.clear();
        provisionQueue.clear();
        jobActive = false;
        activeSlot = -1;
        needEnsure = false;
        lastEnsureTick = -9999;
        lastProgressTick = tickCounter;
        lastRequestTick = tickCounter;
        nextProvisionTick = tickCounter;
        markDirtyAndSync();

        tryStartQueuedJob();
    }

    public void cancelActiveJob() {
        clearJob();
    }

    private void reconcilePending() {
        if (pending.isEmpty()) return;

        boolean changed = false;
        boolean progressed = false;
        for (Iterator<Map.Entry<ItemKey, Integer>> it = pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ItemKey, Integer> e = it.next();
            ItemStack like = e.getKey().toStack(1);
            int baseline = Math.max(0, baselines.getOrDefault(e.getKey(), 0));
            int have = countInBufferLike(like);
            int delta = Math.max(0, have - baseline);
            int prev = e.getValue();
            int left = Math.max(0, e.getValue() - delta);
            if (left <= 0) {
                it.remove();
                changed = true;
                if (prev > 0 && delta > 0) progressed = true;
            } else if (left != e.getValue()) {
                e.setValue(left);
                changed = true;
                if (left < prev) progressed = true;
            }
        }

        if (progressed) {
            lastProgressTick = tickCounter;
        }
        if (changed) {
            needEnsure = true;
            markDirtyAndSync();
        }
    }

    private void ensurePendingWithManager(int period) {
        if (world == null || managerPos == null || pending.isEmpty()) return;
        if (!needEnsure && period > 0 && (tickCounter - lastEnsureTick) < period) return;

        TileEntity te = world.getTileEntity(managerPos);
        if (!(te instanceof TileMirrorManager)) return;
        TileMirrorManager mgr = (TileMirrorManager) te;

        Map<ItemKey, Integer> copy = new LinkedHashMap<>(pending);
        if (!copy.isEmpty()) {
            mgr.ensureDeliveryForExact(this.pos, copy, 0);
            lastEnsureTick = tickCounter;
            needEnsure = false;
        }
    }

    private void deliverSequence() {
        if (world == null || sequence.isEmpty()) return;

        Map<EnumFacing, IItemHandler> neighbors = new EnumMap<>(EnumFacing.class);
        for (EnumFacing dir : EnumFacing.values()) {
            IItemHandler handler = getNeighborHandler(pos.offset(dir), dir.getOpposite());
            if (handler != null) {
                neighbors.put(dir, handler);
            }
        }

        for (ItemStack order : sequence) {
            if (order == null || order.isEmpty()) continue;
            int remaining = Math.max(1, order.getCount());
            while (remaining > 0) {
                ItemStack chunk = extractFromBuffer(order, remaining);
                if (chunk.isEmpty()) break;
                remaining -= chunk.getCount();
                ItemStack leftover = distributeToNeighbors(chunk, neighbors);
                if (!leftover.isEmpty()) {
                    dropStackDown(leftover);
                }
            }
        }
    }

    private ItemStack distributeToNeighbors(ItemStack stack, Map<EnumFacing, IItemHandler> neighbors) {
        if (world == null || stack == null || stack.isEmpty()) return stack;

        if (neighbors == null || neighbors.isEmpty()) {
            return stack;
        }

        ItemStack remaining = stack;
        for (EnumFacing dir : EnumFacing.values()) {
            IItemHandler handler = neighbors.get(dir);
            if (handler == null) continue;
            remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
            if (remaining.isEmpty()) break;
        }
        return remaining;
    }

    private void dropStackDown(ItemStack stack) {
        if (world == null || stack == null || stack.isEmpty()) return;
        BlockPos dropPos = pos.down();
        double x = dropPos.getX() + 0.5;
        double y = dropPos.getY() + 0.2;
        double z = dropPos.getZ() + 0.5;
        EntityItem entity = new EntityItem(world, x, y, z, stack);
        entity.motionX = 0;
        entity.motionY = -0.1;
        entity.motionZ = 0;
        entity.setDefaultPickupDelay();
        world.spawnEntity(entity);
    }

    private void dropStack(ItemStack stack) {
        if (world == null || stack == null || stack.isEmpty()) return;
        EntityItem entity = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack);
        entity.setDefaultPickupDelay();
        world.spawnEntity(entity);
    }

    private ItemStack extractFromBuffer(ItemStack want, int amount) {
        if (want == null || want.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack acc = ItemStack.EMPTY;
        int left = amount;

        if (want.getMaxStackSize() == 1) {
            for (int i = 0; i < buffer.getSlots() && left > 0; i++) {
                ItemStack s = buffer.getStackInSlot(i);
                if (s.isEmpty() || !matchesForDelivery(s, want)) continue;
                ItemStack taken = buffer.extractItem(i, 1, false);
                if (taken.isEmpty()) continue;
                if (acc.isEmpty()) acc = taken;
                else acc.grow(taken.getCount());
                left -= taken.getCount();
            }
        } else {
            for (int i = 0; i < buffer.getSlots() && left > 0; i++) {
                ItemStack s = buffer.getStackInSlot(i);
                if (s.isEmpty() || !matchesForDelivery(s, want)) continue;
                int take = Math.min(left, s.getCount());
                ItemStack taken = buffer.extractItem(i, take, false);
                if (taken.isEmpty()) continue;
                if (acc.isEmpty()) acc = taken;
                else acc.grow(taken.getCount());
                left -= taken.getCount();
            }
        }

        return acc;
    }

    private boolean matchesForDelivery(ItemStack stack, ItemStack like) {
        if (stack == null || like == null || stack.isEmpty() || like.isEmpty()) return false;
        if (stack.getItem() != like.getItem()) return false;
        if (stack.getHasSubtypes() && stack.getMetadata() != like.getMetadata()) return false;
        if (stack.getMaxStackSize() == 1) return true;
        return ItemStack.areItemStackTagsEqual(stack, like);
    }

    private int countInBufferLike(ItemStack like) {
        if (like == null || like.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (matchesForDelivery(s, like)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private IItemHandler getNeighborHandler(BlockPos targetPos, EnumFacing side) {
        if (world == null || targetPos == null) return null;
        TileEntity te = world.getTileEntity(targetPos);
        if (te == null) return null;
        IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        if (handler != null) return handler;
        return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
    }

    private int readSignal() {
        if (world == null) return 0;
        int signal = 0;
        for (EnumFacing f : EnumFacing.values()) {
            signal = Math.max(signal, world.getRedstonePower(pos, f));
        }
        signal = Math.max(signal, world.getStrongPower(pos));
        return Math.max(0, Math.min(15, signal));
    }

    private int patternIndexFromSignal(int signal) {
        if (signal <= 0) return -1;
        return Math.min(signal - 1, patterns.getSlots() - 1);
    }

    private boolean hasPatternRequesterAbove() {
        if (world == null) return false;
        TileEntity te = world.getTileEntity(pos.up());
        return te instanceof TilePatternRequester;
    }

    private boolean useManagerForProvision() {
        return hasPatternRequesterAbove() && managerPos != null;
    }

    private void syncManagerFromPattern() {
        if (world == null || world.isRemote) return;

        TileEntity above = world.getTileEntity(pos.up());
        if (above instanceof TilePatternRequester) {
            TilePatternRequester requester = (TilePatternRequester) above;
            BlockPos patternManager = requester.getManagerPos();
            if (patternManager != null) {
                setManagerPos(patternManager, true);
            } else if (managerFromPattern) {
                setManagerPos(null, false);
            }
        } else if (managerFromPattern) {
            setManagerPos(null, false);
        }
    }

    private void enqueueTrigger(int slot, int count) {
        if (slot < 0 || slot >= patterns.getSlots()) return;
        if (count <= 0) return;


        boolean changed = false;
        QueuedTrigger tail = queuedSignals.peekLast();
        if (tail != null && tail.slot == slot) {
            long merged = (long) tail.count + (long) count;
            int newCount = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, merged));
            if (tail.count != newCount) {
                tail.count = newCount;
                changed = true;
            }
        } else {
            queuedSignals.addLast(new QueuedTrigger(slot, Math.min(Integer.MAX_VALUE, count)));
            changed = true;
        }

        if (changed) {
            markDirtyAndSync();
        }
    }

    private void tryStartQueuedJob() {
        while (!queuedSignals.isEmpty() && !jobActive) {
            QueuedTrigger head = queuedSignals.peekFirst();
            if (head == null) {
                queuedSignals.pollFirst();
                markDirtyAndSync();
                continue;
            }
            if (head.slot < 0 || head.slot >= patterns.getSlots()) {
                queuedSignals.pollFirst();
                markDirtyAndSync();
                continue;
            }
            if (tryStartJob(head.slot)) {
                head.count--;
                if (head.count <= 0) {
                    queuedSignals.pollFirst();
                    markDirtyAndSync();
                }
                break;
            }
            queuedSignals.pollFirst();
            markDirtyAndSync();
        }
    }

    private void requestProvisionForSlot(int slot) {
        if (world == null || world.isRemote) return;
        if (slot < 0 || slot >= patterns.getSlots()) return;

        ItemStack pattern = patterns.getStackInSlot(slot);
        if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) return;

        if (useManagerForProvision()) {
            lastRequestTick = tickCounter;
            needEnsure = true;
            return;
        }

        if (provisionQueue.isEmpty()) {
            resetProvisionQueueFromPending();
        }
        drainProvisionQueue();
    }

    private void requestProvisionForPending() {
        if (world == null || world.isRemote || pending.isEmpty()) return;

        if (useManagerForProvision()) {
            lastRequestTick = tickCounter;
            needEnsure = true;
            return;
        }

        if (provisionQueue.isEmpty()) {
            resetProvisionQueueFromPending();
        }
        drainProvisionQueue();
    }

    private static ItemStack normalizeForProvision(ItemStack like, int amount) {
        if (like == null || like.isEmpty()) return ItemStack.EMPTY;

        if (isCrystal(like)) {
            Aspect aspect = aspectOf(like);
            if (aspect != null) {
                return ThaumcraftApiHelper.makeCrystal(aspect, Math.max(1, amount));
            }
        }

        if (like.getMaxStackSize() == 1) {
            return new ItemStack(like.getItem(), Math.max(1, amount), like.getMetadata());
        }

        ItemStack copy = like.copy();
        copy.setCount(Math.max(1, amount));
        return copy;
    }

    private void resetProvisionQueueFromPending() {
        provisionQueue.clear();
        if (pending.isEmpty()) {
            return;
        }
        for (Map.Entry<ItemKey, Integer> entry : pending.entrySet()) {
            ItemStack like = entry.getKey().toStack(1);
            if (like == null || like.isEmpty()) continue;
            int amount = Math.max(1, entry.getValue());
            enqueueProvisionChunks(like, amount);
        }
        if (!provisionQueue.isEmpty()) {
            nextProvisionTick = tickCounter;
            markDirtyAndSync();
        }
    }

    private void enqueueProvisionChunks(ItemStack like, int amount) {
        if (like == null || like.isEmpty() || amount <= 0) return;

        if (like.getMaxStackSize() == 1) {
            for (int i = 0; i < amount; i++) {
                ItemStack request = normalizeForProvision(like, 1);
                if (!request.isEmpty()) {
                    provisionQueue.addLast(request);
                }
            }
            return;
        }

        int maxStack = Math.max(1, like.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            ItemStack request = normalizeForProvision(like, chunk);
            if (!request.isEmpty()) {
                provisionQueue.addLast(request);
            }
            remaining -= chunk;
        }
    }

    private void drainProvisionQueue() {
        if (world == null || world.isRemote) return;
        if (provisionQueue.isEmpty()) return;

        if (tickCounter < nextProvisionTick) return;

        while (!provisionQueue.isEmpty()) {
            ItemStack head = provisionQueue.peekFirst();
            if (head == null || head.isEmpty()) {
                provisionQueue.pollFirst();
                continue;
            }

            ItemStack request = head.copy();
            GolemHelper.requestProvisioning(world, pos, EnumFacing.UP, request, 0);
            provisionQueue.pollFirst();
            lastRequestTick = tickCounter;
            nextProvisionTick = tickCounter + PROVISION_MIN_INTERVAL;
            markDirtyAndSync();
        }
    }

    private static boolean isCrystal(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ItemsTC.crystalEssence;
    }

    @Nullable
    private static Aspect aspectOf(ItemStack stack) {
        if (!isCrystal(stack)) return null;
        AspectList aspects = ((ItemTCEssentiaContainer) ItemsTC.crystalEssence).getAspects(stack);
        return (aspects != null && aspects.size() == 1) ? aspects.getAspects()[0] : null;
    }

    public void dropContents() {
        if (world == null || world.isRemote) return;
        for (int i = 0; i < patterns.getSlots(); i++) {
            ItemStack s = patterns.getStackInSlot(i);
            if (!s.isEmpty()) {
                dropStack(s.copy());
                patterns.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < buffer.getSlots(); i++) {
            ItemStack s = buffer.getStackInSlot(i);
            if (!s.isEmpty()) {
                dropStack(s.copy());
                buffer.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }


    public @Nullable BlockPos getManagerPos() { return managerPos; }

    public void setManagerPos(@Nullable BlockPos pos) {
        setManagerPos(pos, false);
    }

    private void setManagerPos(@Nullable BlockPos pos, boolean fromPattern) {
        boolean previousManager = useManagerForProvision();
        BlockPos newPos = (pos == null) ? null : pos.toImmutable();
        boolean newFlag = fromPattern && newPos != null;
        if (!Objects.equals(this.managerPos, newPos) || this.managerFromPattern != newFlag) {
            this.managerPos = newPos;
            this.managerFromPattern = newFlag;
            markDirtyAndSync();

            boolean nowManager = useManagerForProvision();
            if (nowManager) {
                if (!provisionQueue.isEmpty()) {
                    provisionQueue.clear();
                    nextProvisionTick = tickCounter;
                }
            } else if (previousManager && jobActive && provisionQueue.isEmpty() && !pending.isEmpty()) {
                resetProvisionQueueFromPending();
            }
        }
    }

    public void clearManagerPosFromManager(BlockPos pos) {
        if (pos != null && pos.equals(this.managerPos)) {
            setManagerPos(null);
        }
    }

    public void triggerExternalRequest(int slot, int times) {
        if (times <= 0) return;
        if (world != null && !world.isRemote) {
            syncManagerFromPattern();
        }
        enqueueTrigger(slot, times);
        tryStartQueuedJob();
    }

    public static ItemStack makeOrderIcon(ItemStack base, BlockPos requesterPos, int slot) {
        if (requesterPos == null) return ItemStack.EMPTY;
        ItemStack icon = (base == null) ? ItemStack.EMPTY : base.copy();
        if (icon.isEmpty()) return ItemStack.EMPTY;
        icon.setCount(1);
        NBTTagCompound tag = icon.hasTagCompound() ? icon.getTagCompound().copy() : new NBTTagCompound();
        NBTTagCompound inner = new NBTTagCompound();
        inner.setLong(ORDER_TAG_POS, requesterPos.toLong());
        inner.setInteger(ORDER_TAG_SLOT, Math.max(0, slot));
        tag.setTag(ORDER_TAG_ROOT, inner);
        icon.setTagCompound(tag);
        return icon;
    }

    public static boolean isOrderIcon(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(ORDER_TAG_ROOT, Constants.NBT.TAG_COMPOUND);
    }

    @Nullable
    public static BlockPos getOrderIconPos(ItemStack stack) {
        if (!isOrderIcon(stack)) return null;
        NBTTagCompound inner = stack.getTagCompound().getCompoundTag(ORDER_TAG_ROOT);
        if (!inner.hasKey(ORDER_TAG_POS, Constants.NBT.TAG_LONG)) return null;
        return BlockPos.fromLong(inner.getLong(ORDER_TAG_POS));
    }

    public static int getOrderIconSlot(ItemStack stack) {
        if (!isOrderIcon(stack)) return -1;
        NBTTagCompound inner = stack.getTagCompound().getCompoundTag(ORDER_TAG_ROOT);
        return inner.getInteger(ORDER_TAG_SLOT);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(buffer);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(
                this,
                "provision_controller",
                0,
                this::provisionAnimPredicate
        ));
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    private <E extends IAnimatable> PlayState provisionAnimPredicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();

        // есть ли сейчас вообще работа (активный заказ/очередь/ожидание доставки)
        boolean waiting = hasAnyWaitingWork();

        // что сейчас реально играет
        Animation current = controller.getCurrentAnimation();
        AnimationState state = controller.getAnimationState();

        switch (capPhase) {
            case IDLE:
                // Ничего не играет. Если появляется работа — стартуем последовательность cap1 -> cap2.
                if (waiting) {
                    capShouldCloseAfterLoop = false;
                    controller.markNeedsReload();
                    controller.setAnimation(new AnimationBuilder()
                            .addAnimation("animation.model.cap1", false)); // открыть крышку
                    capPhase = CapAnimPhase.OPENING;
                } else {
                    // вообще ничего интересного — можно остановить контроллер
                    return PlayState.STOP;
                }
                break;

            case OPENING:
                // Крышка поднимается (cap1). Если очередь успела опустеть — помечаем,
                // что после ПЕРВОГО cap2 надо закрываться.
                if (!waiting) {
                    capShouldCloseAfterLoop = true;
                }

                // Переход в LOOPING после окончания cap1
                if (current != null
                        && "animation.model.cap1".equals(current.animationName)
                        && state == AnimationState.Stopped) {
                    controller.markNeedsReload();
                    controller.setAnimation(new AnimationBuilder()
                            .addAnimation("animation.model.cap2", false)); // один цикл cap2
                    capPhase = CapAnimPhase.LOOPING;
                }
                break;

            case LOOPING:
                // Пока есть работа — после окончания кап2 запускаем его снова.
                // Если работы нет — помечаем, что этот цикл — последний.
                if (!waiting) {
                    capShouldCloseAfterLoop = true;
                }

                if (state == AnimationState.Stopped) {
                    if (capShouldCloseAfterLoop) {
                        // этот цикл cap2 был последним → играем cap3 (закрытие)
                        controller.markNeedsReload();
                        controller.setAnimation(new AnimationBuilder()
                                .addAnimation("animation.model.cap3", false));
                        capPhase = CapAnimPhase.CLOSING;
                    } else {
                        // работы ещё полно → крутим cap2 дальше
                        controller.markNeedsReload();
                        controller.setAnimation(new AnimationBuilder()
                                .addAnimation("animation.model.cap2", false));
                    }
                }
                break;

            case CLOSING:
                // Играем cap3. Когда закончился — уходим в IDLE.
                if (state == AnimationState.Stopped) {
                    capPhase = CapAnimPhase.IDLE;
                    capShouldCloseAfterLoop = false;

                    // Если пока закрывались пришла новая работа —
                    // сразу запускаем новый цикл cap1 → cap2.
                    if (waiting) {
                        controller.markNeedsReload();
                        controller.setAnimation(new AnimationBuilder()
                                .addAnimation("animation.model.cap1", false));
                        capPhase = CapAnimPhase.OPENING;
                    } else {
                        return PlayState.STOP;
                    }
                }
                break;
        }

        return PlayState.CONTINUE;
    }


    private boolean hasAnyWaitingWork() {
        // «Очередь ожидания» = всё, что блок ждёт вообще:
        // активная работа, pending, очередь заказов, незапрошенные чанки
        if (jobActive) return true;
        if (!pending.isEmpty()) return true;
        if (!queuedSignals.isEmpty()) return true;
        if (!provisionQueue.isEmpty()) return true;
        return false;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
                             net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Patterns", patterns.serializeNBT());
        compound.setTag("Buffer", buffer.serializeNBT());
        compound.setBoolean("Job", jobActive);
        compound.setInteger("Slot", activeSlot);
        compound.setInteger("LastSig", lastSignal);
        if (managerPos != null) compound.setLong("Manager", managerPos.toLong());
        compound.setBoolean("ManagerPattern", managerFromPattern);

        NBTTagList pend = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : pending.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setTag("Key", e.getKey().toStack(1).writeToNBT(new NBTTagCompound()));
            c.setInteger("Count", Math.max(0, e.getValue()));
            pend.appendTag(c);
        }
        compound.setTag("Pending", pend);

        NBTTagList seq = new NBTTagList();
        for (ItemStack s : sequence) {
            if (s == null || s.isEmpty()) continue;
            seq.appendTag(s.writeToNBT(new NBTTagCompound()));
        }
        compound.setTag("Sequence", seq);

        NBTTagList queued = new NBTTagList();
        for (QueuedTrigger trigger : queuedSignals) {
            if (trigger == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", Math.max(0, trigger.slot));
            entry.setInteger("Count", Math.max(1, trigger.count));
            queued.appendTag(entry);
        }
        compound.setTag("Queued", queued);

        NBTTagList prov = new NBTTagList();
        for (ItemStack stack : provisionQueue) {
            if (stack == null || stack.isEmpty()) continue;
            prov.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        compound.setTag("ProvisionQueue", prov);
        int delay = Math.max(0, nextProvisionTick - tickCounter);
        compound.setInteger("ProvisionDelay", delay);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        patterns.deserializeNBT(compound.getCompoundTag("Patterns"));
        buffer.deserializeNBT(compound.getCompoundTag("Buffer"));
        jobActive = compound.getBoolean("Job");
        activeSlot = compound.getInteger("Slot");
        lastSignal = compound.getInteger("LastSig");
        managerPos = compound.hasKey("Manager", Constants.NBT.TAG_LONG)
                ? BlockPos.fromLong(compound.getLong("Manager")) : null;
        managerFromPattern = compound.getBoolean("ManagerPattern") && managerPos != null;

        pending.clear();
        baselines.clear();
        sequence.clear();
        queuedSignals.clear();
        provisionQueue.clear();
        nextProvisionTick = 0;

        if (compound.hasKey("Pending", Constants.NBT.TAG_LIST)) {
            NBTTagList pend = compound.getTagList("Pending", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < pend.tagCount(); i++) {
                NBTTagCompound c = pend.getCompoundTagAt(i);
                ItemStack like = new ItemStack(c.getCompoundTag("Key"));
                int count = c.getInteger("Count");
                if (like.isEmpty() || count <= 0) continue;
                pending.put(ItemKey.of(like), count);
            }
        }

        if (compound.hasKey("Sequence", Constants.NBT.TAG_LIST)) {
            NBTTagList seq = compound.getTagList("Sequence", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < seq.tagCount(); i++) {
                ItemStack s = new ItemStack(seq.getCompoundTagAt(i));
                if (!s.isEmpty()) sequence.add(s);
            }
        }

        if (compound.hasKey("Queued", Constants.NBT.TAG_LIST)) {
            NBTTagList queued = compound.getTagList("Queued", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < queued.tagCount(); i++) {
                NBTTagCompound entry = queued.getCompoundTagAt(i);
                int idx = entry.getInteger("Slot");
                int cnt = entry.getInteger("Count");
                if (idx >= 0 && idx < patterns.getSlots() && cnt > 0) {
                    enqueueTrigger(idx, cnt);
                }
            }
        }

        if (compound.hasKey("ProvisionQueue", Constants.NBT.TAG_LIST)) {
            NBTTagList prov = compound.getTagList("ProvisionQueue", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < prov.tagCount(); i++) {
                ItemStack s = new ItemStack(prov.getCompoundTagAt(i));
                if (!s.isEmpty()) {
                    provisionQueue.addLast(s);
                }
            }
        }

        nextProvisionTick = compound.getInteger("ProvisionDelay");
    }
}