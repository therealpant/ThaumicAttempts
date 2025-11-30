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
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPatternInfusion;
import thaumcraft.common.golems.EntityThaumcraftGolem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Черновая реализация инфузионного реквестера.
 * Содержит 15 слотов под ItemCraftPatternInfusion, один спец-слот
 * и четыре слота под результаты третьей стадии.
 */
public class TileInfusionRequester extends TileEntity implements ITickable {

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
            return !stack.isEmpty() && stack.getItem() instanceof ItemCraftPatternInfusion;
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

    @Nullable
    private BlockPos managerPos = null;

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

    public void setManagerPos(@Nullable BlockPos pos) {
        if ((managerPos == null && pos == null) || (managerPos != null && managerPos.equals(pos))) return;
        managerPos = pos == null ? null : pos.toImmutable();
        markDirtyAndSync();
    }

    public boolean tryInsertPattern(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemCraftPatternInfusion)) return false;
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

        if (world.isRemote) return;

        int signal = readSignal();
        if (signal != lastSignal) {
            if (signal > 0) {
                int slot = patternIndexFromSignal(signal);
                if (slot >= 0) {
                    enqueueTrigger(slot, 1);
                }
            }
            lastSignal = signal;
        }

        if (!jobActive) {
            tryStartNextJob();
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
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemCraftPatternInfusion)) continue;
            ItemStack preview = ItemCraftPatternInfusion.calcResultPreview(pat, world);
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
            if (pat.isEmpty() || !(pat.getItem() instanceof ItemCraftPatternInfusion)) continue;

            ItemStack preview = ItemCraftPatternInfusion.calcResultPreview(pat, world);
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
        // Заглушка: сразу освобождаем очередь, пока полная логика стадий не реализована.
        clearJob();
    }

    private void clearJob() {
        jobActive = false;
        activeSlot = -1;
        markDirtyAndSync();
        tryStartNextJob();
    }

    private boolean hasPatternInSlot(int slot) {
        if (slot < 0 || slot >= patterns.getSlots()) return false;
        ItemStack stack = patterns.getStackInSlot(slot);
        return !stack.isEmpty() && stack.getItem() instanceof ItemCraftPatternInfusion;
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