package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import thaumcraft.api.golems.GolemHelper;
import therealpant.thaumicattempts.api.PatternResourceList;
import therealpant.thaumicattempts.api.IPatternedWorksite;
import therealpant.thaumicattempts.api.PatternProvisioningSpec;
import therealpant.thaumicattempts.api.PatternRedstoneMode;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;
import thaumcraft.common.golems.EntityThaumcraftGolem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Черновая реализация инфузионного реквестера.
 * Содержит 15 слотов под ItemInfusionPattern, один спец-слот
 * и четыре слота под результаты третьей стадии.
 */
public class TileInfusionRequester extends TileEntity implements ITickable, IPatternedWorksite {

    public static final int PATTERN_SLOT_COUNT = 15;
    private static final String TAG_PATTERNS = "patterns";
    private static final String TAG_SPECIAL = "special";
    private static final String TAG_RESULTS = "results";
    private static final String TAG_STORAGES = "storages";
    private static final String TAG_TARGET = "target";
    private static final String TAG_GOLEMS = "golems";
    private static final String TAG_GOLEM_ID = "id";
    private static final String TAG_MANAGER = "manager";

    private static final int MAX_QUEUED_ORDERS = 8;

    private final ItemStackHandler patterns = new ItemStackHandler(PATTERN_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ItemInfusionPattern;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }
    };

    private final ItemStackHandler specialSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }
    };

    private final ItemStackHandler results = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }
    };

    private final IItemHandler combined = new CombinedInvWrapper(patterns, specialSlot, results);

    private final List<BlockPos> storages = new ArrayList<>();
    @Nullable
    private BlockPos targetPos = null;
    private final LinkedHashSet<UUID> boundGolems = new LinkedHashSet<>();

    private final ArrayDeque<Integer> queuedTriggers = new ArrayDeque<>();
    private int lastSignal = 0;
    private boolean jobActive = false;
    private int activeSlot = -1;
    private int tickCounter = 0;

    private enum Stage { NONE, WAIT_RESOURCES, INTERACT_TARGET, WAIT_PICKUP }

    private Stage stage = Stage.NONE;
    private final Map<Integer, Map<ItemKey, Integer>> pendingByStorage = new LinkedHashMap<>();
    private final Map<Integer, Map<ItemKey, Integer>> baselinesByStorage = new HashMap<>();
    private boolean needsEnsure = false;
    private int lastRequestTick = 0;
    private int lastEnsureTick = -9999;
    private int nextProvisionTick = 0;
    private static final int PROVISION_INTERVAL = 10;
    private static final int ENSURE_INTERVAL = 20;
    private int pickupBaseline = -1;

    @Nullable
    private BlockPos managerPos = null;
    private boolean managerFromPattern = false;

    @Override
    public PatternRedstoneMode getRedstoneMode() {
        return PatternRedstoneMode.RISING_EDGE_SELECTS_SLOT;
    }

    @Override
    public PatternProvisioningSpec getProvisioningSpec() {
        return new PatternProvisioningSpec(EnumFacing.UP, true, true, PROVISION_INTERVAL);
    }

    @Override
    public ItemStackHandler getPatternHandler() {
        return patterns;
    }

    public ItemStackHandler getSpecialHandler() {
        return specialSlot;
    }

    public ItemStackHandler getResultHandler() {
        return results;
    }

    public @Nullable BlockPos getManagerPos() {
        return managerPos;
    }

    @Override
    public void setManagerPosFromPattern(@Nullable BlockPos managerPos) {
        setManagerPos(managerPos);
    }

    @Override
    public @Nullable BlockPos getManagerPosForPattern() {
        return managerPos;
    }
    public void setManagerPos(@Nullable BlockPos pos) {
        setManagerPos(pos, false);
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

    private void setManagerPos(@Nullable BlockPos pos, boolean fromPattern) {
        boolean previousManager = useManagerForProvision();
        BlockPos newPos = pos == null ? null : pos.toImmutable();
        boolean newFlag = fromPattern && newPos != null;
        if (!Objects.equals(this.managerPos, newPos) || this.managerFromPattern != newFlag) {
            this.managerPos = newPos;
            this.managerFromPattern = newFlag;
            markDirtyAndSync();

            boolean nowManager = useManagerForProvision();
            if (nowManager) {
                needsEnsure = needsEnsure || !pendingByStorage.isEmpty();
                nextProvisionTick = tickCounter;
            } else if (previousManager && !pendingByStorage.isEmpty()) {
                lastRequestTick = tickCounter;
                nextProvisionTick = tickCounter;
            }
        }
    }

    public boolean tryInsertPattern(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemInfusionPattern)) return false;
        for (int i = 0; i < patterns.getSlots(); i++) {
            if (patterns.getStackInSlot(i).isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                patterns.setStackInSlot(i, copy);
                markDirtyAndSync();
                return true;
            }
        }
        return false;
    }

    @Override
    public int enqueueFromPatternRequester(int patternSlot, int times) {
        int before = queuedTriggers.size();
        enqueueTrigger(patternSlot, Math.max(1, times));
        int after = queuedTriggers.size();
        return Math.max(0, after - before);
    }

    @Override
    public int enqueueFromPatternRequester(ItemStack resultLike, int times) {
        if (resultLike == null || resultLike.isEmpty()) return 0;
        int slot = findPatternSlotFor(resultLike);
        if (slot < 0) return 0;
        return enqueueFromPatternRequester(slot, times);
    }

    public ItemStack tryExtractPattern() {
        for (int i = patterns.getSlots() - 1; i >= 0; i--) {
            ItemStack st = patterns.getStackInSlot(i);
            if (!st.isEmpty()) {
                patterns.setStackInSlot(i, ItemStack.EMPTY);
                markDirtyAndSync();
                return st;
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
        if (signal != lastSignal) {
            if (signal > 0) {
                int slot = patternIndexFromSignal(signal);
                if (slot >= 0) {
                    enqueueTrigger(slot, 1);
                    tryStartNextJob();
                }
            }
            lastSignal = signal;
        }

        if (!jobActive) {
            tryStartNextJob();
            return;
        }

        if (stage == Stage.WAIT_RESOURCES) {
            boolean changed = reconcilePendingWithStorages();
            ensurePendingDeliveries(changed ? 0 : ENSURE_INTERVAL);
            if (pendingByStorage.isEmpty()) {
                stage = Stage.INTERACT_TARGET;
                pickupBaseline = -1;
                markDirtyAndSync();
            }
        } else if (stage == Stage.INTERACT_TARGET) {
            if (performTargetInteraction()) {
                stage = Stage.WAIT_PICKUP;
                pickupBaseline = snapshotFirstStorageCount();
                markDirtyAndSync();
            }
        } else if (stage == Stage.WAIT_PICKUP) {
            if (pickupBaseline < 0) {
                pickupBaseline = snapshotFirstStorageCount();
            }
            int now = snapshotFirstStorageCount();
            if (now != pickupBaseline) {
                pullFirstStorageIntoResults();
                clearJob();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            this.lastSignal = readSignal();
            syncManagerFromPattern();
        }
    }

    public void triggerExternalRequest(int slot, int count) {
        if (world == null || world.isRemote) return;
        enqueueTrigger(slot, count);
        tryStartNextJob();
    }

    public List<ItemStack> listCraftableResults() {
        if (world == null) return java.util.Collections.emptyList();

        ArrayList<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < patterns.getSlots(); i++) {
            ItemStack pat = patterns.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemInfusionPattern)) continue;
            if (PatternResourceList.build(pat).isEmpty()) continue;
            ItemStack preview = ItemInfusionPattern.calcResultPreview(pat, world);
            if (preview.isEmpty()) continue;

            ItemStack one = preview.copy();
            if (one.getCount() <= 0) one.setCount(1);
            out.add(one);
        }
        return out;
    }

    public int findPatternSlotFor(ItemStack like) {
        if (world == null || like == null || like.isEmpty()) return -1;
        for (int i = 0; i < patterns.getSlots(); i++) {
            ItemStack pat = patterns.getStackInSlot(i);
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemInfusionPattern)) continue;
            if (PatternResourceList.build(pat).isEmpty()) continue;

            ItemStack preview = ItemInfusionPattern.calcResultPreview(pat, world);
            if (preview.isEmpty()) continue;

            boolean match = (preview.getMaxStackSize() == 1)
                    ? (preview.getItem() == like.getItem() && (!preview.getHasSubtypes() || preview.getMetadata() == like.getMetadata()))
                    : net.minecraftforge.items.ItemHandlerHelper.canItemStacksStackRelaxed(preview, like);
            if (match) return i;
        }
        return -1;
    }

    private void enqueueTrigger(int slot, int count) {
        if (slot < 0 || slot >= patterns.getSlots()) return;
        if (count <= 0) return;

        int room = MAX_QUEUED_ORDERS - queuedTriggers.size();
        if (room <= 0) return;

        int toAdd = Math.min(count, room);
        for (int i = 0; i < toAdd; i++) queuedTriggers.add(slot);
        markDirtyAndSync();
    }

    private void tryStartNextJob() {
        if (jobActive) return;
        Integer next = queuedTriggers.poll();
        if (next == null) return;

        if (!hasPatternInSlot(next)) {
            markDirtyAndSync();
            return;
        }

        jobActive = true;
        activeSlot = next;
        markDirtyAndSync();
        onJobTriggered(next);
    }

    private void onJobTriggered(int slot) {
        if (!hasPatternInSlot(slot) || storages.isEmpty()) {
            clearJob();
            return;
        }

        List<PatternResourceList.Entry> resources = getResourcesForSlot(slot);
        if (resources.isEmpty()) {
            clearJob();
            return;
        }

        pendingByStorage.clear();
        baselinesByStorage.clear();
        stage = Stage.WAIT_RESOURCES;
        jobActive = true;
        activeSlot = slot;
        pickupBaseline = -1;

        int storageCount = storages.size();
        int idx = 0;
        for (PatternResourceList.Entry entry : resources) {
            if (entry == null || entry.getKey() == null || entry.getKey() == ItemKey.EMPTY) continue;
            int target = Math.min(idx, storageCount - 1);
            Map<ItemKey, Integer> need = pendingByStorage.computeIfAbsent(target, k -> new LinkedHashMap<>());
            need.merge(entry.getKey(), entry.getCount(), Integer::sum);
            idx++;
        }

        // collect baselines for change detection
        for (Integer storageIdx : pendingByStorage.keySet()) {
            BlockPos storagePos = storages.get(Math.max(0, Math.min(storageIdx, storages.size() - 1)));
            Map<ItemKey, Integer> base = new HashMap<>();
            Map<ItemKey, Integer> need = pendingByStorage.get(storageIdx);
            if (need != null) {
                for (ItemKey key : need.keySet()) {
                    base.put(key, countAtStorageLike(storagePos, key));
                }
            }
            baselinesByStorage.put(storageIdx, base);
        }

        ensurePendingDeliveries(0);
        markDirtyAndSync();
    }

    private void clearJob() {
        jobActive = false;
        activeSlot = -1;
        stage = Stage.NONE;
        pendingByStorage.clear();
        baselinesByStorage.clear();
        needsEnsure = false;
        lastEnsureTick = -9999;
        lastRequestTick = tickCounter;
        nextProvisionTick = tickCounter;
        pickupBaseline = -1;
        markDirtyAndSync();
        tryStartNextJob();
    }

    private boolean hasPatternInSlot(int slot) {
        if (slot < 0 || slot >= patterns.getSlots()) return false;
        ItemStack stack = patterns.getStackInSlot(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemInfusionPattern)) return false;
        return !PatternResourceList.build(stack).isEmpty();
    }

    public List<PatternResourceList.Entry> getResourcesForSlot(int slot) {
        if (!hasPatternInSlot(slot)) return java.util.Collections.emptyList();
        return PatternResourceList.build(patterns.getStackInSlot(slot));
    }

    private boolean reconcilePendingWithStorages() {
        boolean changed = false;
        for (Map.Entry<Integer, Map<ItemKey, Integer>> entry : new ArrayList<>(pendingByStorage.entrySet())) {
            int idx = entry.getKey();
            Map<ItemKey, Integer> need = entry.getValue();
            if (need == null || need.isEmpty()) {
                pendingByStorage.remove(idx);
                continue;
            }
            BlockPos storagePos = storages.get(Math.max(0, Math.min(idx, storages.size() - 1)));
            Map<ItemKey, Integer> baselines = baselinesByStorage.getOrDefault(idx, java.util.Collections.emptyMap());
            for (Iterator<Map.Entry<ItemKey, Integer>> it = need.entrySet().iterator(); it.hasNext();) {
                Map.Entry<ItemKey, Integer> e = it.next();
                ItemKey key = e.getKey();
                int want = Math.max(1, e.getValue());
                int baseline = Math.max(0, baselines.getOrDefault(key, 0));
                int have = countAtStorageLike(storagePos, key);
                int delivered = Math.max(0, have - baseline);
                int left = Math.max(0, want - delivered);
                if (left <= 0) {
                    it.remove();
                    changed = true;
                } else if (left != e.getValue()) {
                    e.setValue(left);
                    changed = true;
                }
            }
            if (need.isEmpty()) {
                pendingByStorage.remove(idx);
                changed = true;
            }
        }
        if (changed) {
            needsEnsure = true;
            markDirtyAndSync();
        }
        return changed;
    }

    private void ensurePendingDeliveries(int interval) {
        if (world == null || pendingByStorage.isEmpty()) return;
        if (!needsEnsure && (tickCounter - lastEnsureTick) < interval) return;

        if (useManagerForProvision() && world.getTileEntity(managerPos) instanceof TileMirrorManager) {
            TileMirrorManager mgr = (TileMirrorManager) world.getTileEntity(managerPos);
            for (Map.Entry<Integer, Map<ItemKey, Integer>> entry : pendingByStorage.entrySet()) {
                BlockPos storagePos = storages.get(Math.max(0, Math.min(entry.getKey(), storages.size() - 1)));
                mgr.ensureDeliveryForExact(storagePos, new LinkedHashMap<>(entry.getValue()), 0);
            }
            needsEnsure = false;
            lastEnsureTick = tickCounter;
            return;
        }

        if (tickCounter < nextProvisionTick) return;
        if (!useManagerForProvision()) {
            for (Map.Entry<Integer, Map<ItemKey, Integer>> entry : pendingByStorage.entrySet()) {
                BlockPos storagePos = storages.get(Math.max(0, Math.min(entry.getKey(), storages.size() - 1)));
                requestProvisionForStorage(storagePos, entry.getValue());
            }
        }
        lastRequestTick = tickCounter;
        nextProvisionTick = tickCounter + PROVISION_INTERVAL;
        needsEnsure = false;
    }

    private void requestProvisionForStorage(BlockPos storagePos, Map<ItemKey, Integer> needs) {
        if (world == null || storagePos == null || needs == null || needs.isEmpty()) return;
        for (Map.Entry<ItemKey, Integer> entry : needs.entrySet()) {
            ItemStack like = entry.getKey().toStack(1);
            if (like.isEmpty()) continue;
            int remaining = Math.max(1, entry.getValue());
            while (remaining > 0) {
                int chunk = Math.min(remaining, Math.max(1, like.getMaxStackSize()));
                ItemStack req = normalizeForProvision(like, chunk);
                if (!req.isEmpty()) {
                    GolemHelper.requestProvisioning(world, storagePos, EnumFacing.UP, req, 0);
                }
                remaining -= chunk;
            }
        }
    }

    private static ItemStack normalizeForProvision(ItemStack like, int amount) {
        if (like == null || like.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = like.copy();
        copy.setCount(Math.max(1, amount));
        return copy;
    }

    private int countAtStorageLike(BlockPos storagePos, ItemKey key) {
        if (world == null || storagePos == null || key == null || key == ItemKey.EMPTY) return 0;
        TileEntity te = world.getTileEntity(storagePos);
        if (te == null || !te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP)) return 0;
        IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (handler == null) return 0;
        ItemStack like = key.toStack(1);
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            if (net.minecraftforge.items.ItemHandlerHelper.canItemStacksStackRelaxed(slot, like)) {
                total += slot.getCount();
            }
        }
        return total;
    }

    private boolean performTargetInteraction() {
        if (world == null) return false;
        if (targetPos == null) return true;
        ItemStack stack = specialSlot.getStackInSlot(0);
        if (stack.isEmpty()) return true;
        // Заглушка: передаём предмет дальше по логике поставленного блока через таумкрафтовских големов
        return true;
    }

    private int snapshotFirstStorageCount() {
        if (storages.isEmpty()) return 0;
        BlockPos storagePos = storages.get(0);
        int total = 0;
        TileEntity te = world == null ? null : world.getTileEntity(storagePos);
        if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP)) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack s = handler.getStackInSlot(i);
                    if (!s.isEmpty()) total += s.getCount();
                }
            }
        }
        return total;
    }

    private void dropStack(ItemStack stack) {
        if (world == null || stack == null || stack.isEmpty()) return;
        EntityItem entity = new EntityItem(world,
                pos.getX() + 0.5,
                pos.getY() + 0.2,
                pos.getZ() + 0.5,
                stack);
        entity.setDefaultPickupDelay();
        world.spawnEntity(entity);
    }

    private void pullFirstStorageIntoResults() {
        if (world == null || storages.isEmpty()) return;
        BlockPos storagePos = storages.get(0);
        TileEntity te = world.getTileEntity(storagePos);
        if (te == null || !te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP)) return;
        IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (handler == null) return;

        Consumer<ItemStack> inserter = stack -> {
            ItemStack remaining = stack;
            for (int i = 0; i < results.getSlots() && !remaining.isEmpty(); i++) {
                remaining = net.minecraftforge.items.ItemHandlerHelper.insertItem(results, remaining, false);
            }
            if (!remaining.isEmpty()) {
                dropStack(remaining);
            }
        };

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slot = handler.extractItem(i, handler.getSlotLimit(i), true);
            if (slot.isEmpty()) continue;
            ItemStack extracted = handler.extractItem(i, slot.getCount(), false);
            if (!extracted.isEmpty()) {
                inserter.accept(extracted);
            }
        }
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

    public int bindStorage(BlockPos pos) {
        if (pos == null) return -1;
        BlockPos immutable = pos.toImmutable();
        int existing = storages.indexOf(immutable);
        if (existing >= 0) {
            return existing + 1;
        }
        storages.add(immutable);
        markDirtyAndSync();
        return storages.size();
    }

    public List<BlockPos> getStorages() {
        return new ArrayList<>(storages);
    }

    public boolean setTargetPos(BlockPos pos) {
        BlockPos immutable = pos == null ? null : pos.toImmutable();
        if ((targetPos == null && immutable == null) || (targetPos != null && targetPos.equals(immutable))) {
            return false;
        }
        targetPos = immutable;
        markDirtyAndSync();
        return true;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    public boolean tryBindGolem(EntityThaumcraftGolem golem) {
        if (world == null || world.isRemote) return false;
        if (golem == null || golem.isDead) return false;

        UUID id = golem.getUniqueID();
        if (id == null) return false;
        if (boundGolems.contains(id)) return true;
        boundGolems.add(id);
        markDirtyAndSync();
        return true;
    }

    public Set<UUID> getBoundGolemsSnapshot() {
        return new LinkedHashSet<>(boundGolems);
    }

    public void dropContents() {
        if (world == null || world.isRemote) return;
        dropHandler(patterns);
        dropHandler(specialSlot);
        dropHandler(results);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combined);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag(TAG_PATTERNS, patterns.serializeNBT());
        compound.setTag(TAG_SPECIAL, specialSlot.serializeNBT());
        compound.setTag(TAG_RESULTS, results.serializeNBT());

        NBTTagList storeList = new NBTTagList();
        for (BlockPos pos : storages) {
            if (pos == null) continue;
            NBTTagCompound el = new NBTTagCompound();
            el.setLong("p", pos.toLong());
            storeList.appendTag(el);
        }
        compound.setTag(TAG_STORAGES, storeList);

        if (targetPos != null) {
            compound.setLong(TAG_TARGET, targetPos.toLong());
        }

        if (managerPos != null) {
            compound.setLong(TAG_MANAGER, managerPos.toLong());
        }
        compound.setBoolean("ManagerPattern", managerFromPattern && managerPos != null);

        NBTTagList golemList = new NBTTagList();
        for (UUID id : boundGolems) {
            if (id == null) continue;
            NBTTagCompound el = new NBTTagCompound();
            el.setUniqueId(TAG_GOLEM_ID, id);
            golemList.appendTag(el);
        }
        compound.setTag(TAG_GOLEMS, golemList);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(TAG_PATTERNS)) patterns.deserializeNBT(compound.getCompoundTag(TAG_PATTERNS));
        if (compound.hasKey(TAG_SPECIAL)) specialSlot.deserializeNBT(compound.getCompoundTag(TAG_SPECIAL));
        if (compound.hasKey(TAG_RESULTS)) results.deserializeNBT(compound.getCompoundTag(TAG_RESULTS));

        storages.clear();
        if (compound.hasKey(TAG_STORAGES, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(TAG_STORAGES, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound el = list.getCompoundTagAt(i);
                if (el.hasKey("p")) {
                    storages.add(BlockPos.fromLong(el.getLong("p")));
                }
            }
        }

        targetPos = compound.hasKey(TAG_TARGET) ? BlockPos.fromLong(compound.getLong(TAG_TARGET)) : null;

        managerPos = compound.hasKey(TAG_MANAGER) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER)) : null;

        managerFromPattern = compound.getBoolean("ManagerPattern") && managerPos != null;

        boundGolems.clear();
        if (compound.hasKey(TAG_GOLEMS, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(TAG_GOLEMS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound el = list.getCompoundTagAt(i);
                if (el.hasUniqueId(TAG_GOLEM_ID)) {
                    boundGolems.add(el.getUniqueId(TAG_GOLEM_ID));
                }
            }
        }
    }

    private void dropHandler(ItemStackHandler handler) {
        if (handler == null || world == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                handler.setStackInSlot(i, ItemStack.EMPTY);
                ItemStack copy = stack.copy();
                EntityItem ei = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, copy);
                world.spawnEntity(ei);
            }
        }
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }
}