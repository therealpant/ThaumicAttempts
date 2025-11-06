package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.block.state.IBlockState;
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
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.*;

public class TileResourceRequester extends TileEntity implements ITickable {

    private final ItemStackHandler patterns = new ItemStackHandler(9) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemResourceList;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    private final ItemStackHandler buffer = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    private final LinkedHashMap<ItemKey, Integer> pending = new LinkedHashMap<>();
    private final LinkedHashMap<ItemKey, Integer> baselines = new LinkedHashMap<>();
    private final List<ItemStack> sequence = new ArrayList<>();

    private boolean jobActive = false;
    private int activeSlot = -1;

    private int lastSignal = 0;
    private int tickCounter = 0;
    private int lastEnsureTick = -9999;
    private boolean needEnsure = false;

    private @Nullable BlockPos managerPos = null;

    public ItemStackHandler getPatternHandler() { return patterns; }
    public ItemStackHandler getBufferHandler() { return buffer; }

    public boolean tryInsertPattern(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemResourceList)) return false;
        for (int i = 0; i < patterns.getSlots(); i++) {
            if (patterns.getStackInSlot(i).isEmpty()) {
                patterns.setStackInSlot(i, stack.copy());
                markDirty();
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
                markDirty();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        tickCounter++;

        int signal = readSignal();
        if (!jobActive && lastSignal == 0 && signal > 0) {
            int idx = patternIndexFromSignal(signal);
            if (idx >= 0) startJob(idx);
        }
        lastSignal = signal;

        if (jobActive) {
            reconcilePending();
            ensurePendingWithManager(20);

            if (pending.isEmpty()) {
                deliverSequence();
                clearJob();
            }
        }
    }

    private void startJob(int slot) {
        ItemStack pattern = patterns.getStackInSlot(slot);
        if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) return;

        pending.clear();
        baselines.clear();
        sequence.clear();

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
            return;
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
        markDirty();

        if (pending.isEmpty()) {
            deliverSequence();
            clearJob();
        }
    }

    private void clearJob() {
        pending.clear();
        baselines.clear();
        sequence.clear();
        jobActive = false;
        activeSlot = -1;
        needEnsure = false;
        lastEnsureTick = -9999;
        markDirty();
    }

    public void cancelActiveJob() {
        clearJob();
    }

    private void reconcilePending() {
        if (pending.isEmpty()) return;

        boolean changed = false;
        for (Iterator<Map.Entry<ItemKey, Integer>> it = pending.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ItemKey, Integer> e = it.next();
            ItemStack like = e.getKey().toStack(1);
            int baseline = Math.max(0, baselines.getOrDefault(e.getKey(), 0));
            int have = countInBufferLike(like);
            int delta = Math.max(0, have - baseline);
            int left = Math.max(0, e.getValue() - delta);
            if (left <= 0) {
                it.remove();
                changed = true;
            } else if (left != e.getValue()) {
                e.setValue(left);
                changed = true;
            }
        }

        if (changed) {
            needEnsure = true;
            markDirty();
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

        EnumFacing facing = EnumFacing.NORTH;
        IBlockState state = world.getBlockState(pos);
        if (state.getPropertyKeys().contains(net.minecraft.block.BlockHorizontal.FACING)) {
            facing = state.getValue(net.minecraft.block.BlockHorizontal.FACING);
        }

        BlockPos targetPos = pos.offset(facing);
        IItemHandler dest = getNeighborHandler(targetPos, facing.getOpposite());

        for (ItemStack order : sequence) {
            if (order == null || order.isEmpty()) continue;
            int remaining = Math.max(1, order.getCount());
            while (remaining > 0) {
                ItemStack chunk = extractFromBuffer(order, remaining);
                if (chunk.isEmpty()) break;
                remaining -= chunk.getCount();
                if (dest != null) {
                    ItemStack leftover = ItemHandlerHelper.insertItem(dest, chunk, false);
                    if (!leftover.isEmpty()) {
                        dropStack(leftover);
                    }
                } else {
                    dropStack(chunk);
                }
            }
        }
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
        if (!Objects.equals(this.managerPos, pos)) {
            this.managerPos = (pos == null) ? null : pos.toImmutable();
            markDirty();
        }
    }

    public void clearManagerPosFromManager(BlockPos pos) {
        if (pos != null && pos.equals(this.managerPos)) {
            setManagerPos(null);
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == EnumFacing.UP) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(patterns);
            }
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(buffer);
        }
        return super.getCapability(capability, facing);
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

        pending.clear();
        baselines.clear();
        sequence.clear();

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
    }
}
