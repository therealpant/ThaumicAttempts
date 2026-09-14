package therealpant.thaumicattempts.world.tile.automation;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TilePedestal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared otherworldly-pedestal transport used by Paradoxum machines. */
public final class ParadoxPedestalAutomation {
    public static final double SEARCH_RADIUS = 6.0D;
    public static final int FLIGHT_TICKS = 30;
    private static final int SEARCH_INTERVAL = 20;
    private static final double PEDESTAL_ITEM_HEIGHT = 1.25D;
    private static final Map<World, Map<Long, BlockPos>> OUTPUT_RESERVATIONS =
            new WeakHashMap<>();

    private final TileEntity owner;
    private final ParadoxPedestalAutomationHost host;
    private final List<Transfer> transfers = new ArrayList<>();
    private BlockPos[] origins = new BlockPos[0];

    public ParadoxPedestalAutomation(TileEntity owner, ParadoxPedestalAutomationHost host) {
        this.owner = owner;
        this.host = host;
    }

    public void tick() {
        World world = owner.getWorld();
        if (world == null || world.isRemote) return;
        ensureOriginSize();
        long now = world.getTotalWorldTime();
        finishTransfers(now);
        if (now % SEARCH_INTERVAL != Math.floorMod(owner.getPos().toLong(), SEARCH_INTERVAL)) return;

        for (int slot = 0; slot < host.getAutomationSlotCount(); slot++) {
            if (isSlotBusy(slot)) continue;
            ItemStack current = host.getAutomationStack(slot);
            if (!current.isEmpty() && host.isAutomatedStackComplete(current)) {
                startOutput(slot, current, now);
            }
        }
        for (int slot = 0; slot < host.getAutomationSlotCount(); slot++) {
            if (isSlotBusy(slot) || !host.getAutomationStack(slot).isEmpty()) continue;
            startInput(slot, now);
        }
    }

    public boolean isSlotBusy(int slot) {
        for (Transfer transfer : transfers) if (transfer.slot == slot) return true;
        return false;
    }

    public void clearOrigin(int slot) {
        ensureOriginSize();
        if (slot >= 0 && slot < origins.length) origins[slot] = null;
    }

    public boolean isDestination(BlockPos pedestalPos) {
        if (pedestalPos == null) return false;
        for (Transfer transfer : transfers) {
            if (!transfer.inbound && pedestalPos.equals(transfer.pedestalPos)) return true;
        }
        return false;
    }

    public List<Transfer> getTransfers() {
        return Collections.unmodifiableList(transfers);
    }

    public void dropAllTransfers() {
        Vec3d dropPoint = new Vec3d(owner.getPos().getX() + 0.5D,
                owner.getPos().getY() + 1.0D, owner.getPos().getZ() + 0.5D);
        for (Transfer transfer : transfers) {
            if (!transfer.inbound) release(transfer.pedestalPos);
            drop(transfer.stack, dropPoint);
        }
        transfers.clear();
        host.onAutomationChanged();
    }

    private void startInput(int slot, long now) {
        TilePedestal pedestal = findPedestal(true, null);
        if (pedestal == null) return;
        ItemStack source = pedestal.getStackInSlot(0);
        if (source.isEmpty() || !host.canAcceptAutomatedStack(source)) return;
        ItemStack travelling = pedestal.decrStackSize(0, 1);
        if (travelling.isEmpty()) return;
        travelling.setCount(1);
        syncPedestal(pedestal);
        BlockPos pedestalPos = pedestal.getPos().toImmutable();
        transfers.add(new Transfer(slot, true, travelling, pedestalPos, now,
                pedestalPoint(pedestalPos), host.getAutomationSlotPosition(slot)));
        host.onAutomationChanged();
    }

    private void startOutput(int slot, ItemStack current, long now) {
        BlockPos excluded = slot < origins.length ? origins[slot] : null;
        TilePedestal pedestal = findPedestal(false, excluded);
        if (pedestal == null || !reserve(pedestal.getPos())) return;
        ItemStack travelling = host.extractAutomatedStack(slot);
        if (travelling.isEmpty()) {
            release(pedestal.getPos());
            return;
        }
        BlockPos pedestalPos = pedestal.getPos().toImmutable();
        transfers.add(new Transfer(slot, false, travelling, pedestalPos, now,
                host.getAutomationSlotPosition(slot), pedestalPoint(pedestalPos)));
        host.onAutomationChanged();
    }

    private void finishTransfers(long now) {
        for (int index = transfers.size() - 1; index >= 0; index--) {
            Transfer transfer = transfers.get(index);
            if (now - transfer.startedAt < FLIGHT_TICKS) {
                if (!transfer.inbound) reserve(transfer.pedestalPos);
                continue;
            }
            boolean completed = transfer.inbound
                    ? finishInput(transfer) : finishOutput(transfer);
            if (completed) {
                transfers.remove(index);
                host.onAutomationChanged();
            }
        }
    }

    private boolean finishInput(Transfer transfer) {
        if (host.insertAutomatedStack(transfer.slot, transfer.stack.copy())) {
            origins[transfer.slot] = transfer.pedestalPos;
            return true;
        }
        return returnToPedestalOrDrop(transfer);
    }

    private boolean finishOutput(Transfer transfer) {
        release(transfer.pedestalPos);
        TilePedestal pedestal = getUsablePedestal(transfer.pedestalPos);
        if (pedestal != null && pedestal.getStackInSlot(0).isEmpty()) {
            pedestal.setInventorySlotContents(0, transfer.stack.copy());
            syncPedestal(pedestal);
            origins[transfer.slot] = null;
            return true;
        }
        if (host.getAutomationStack(transfer.slot).isEmpty()
                && host.insertAutomatedStack(transfer.slot, transfer.stack.copy())) return true;
        drop(transfer.stack, transfer.end);
        return true;
    }

    private boolean returnToPedestalOrDrop(Transfer transfer) {
        TilePedestal pedestal = getUsablePedestal(transfer.pedestalPos);
        if (pedestal != null && pedestal.getStackInSlot(0).isEmpty()) {
            pedestal.setInventorySlotContents(0, transfer.stack.copy());
            syncPedestal(pedestal);
        } else {
            drop(transfer.stack, transfer.start);
        }
        return true;
    }

    @Nullable
    private TilePedestal findPedestal(boolean occupied, @Nullable BlockPos excluded) {
        World world = owner.getWorld();
        if (world == null) return null;
        List<TilePedestal> candidates = new ArrayList<>();
        double rangeSq = SEARCH_RADIUS * SEARCH_RADIUS;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TilePedestal) || tile.isInvalid()
                    || tile.getPos().distanceSq(owner.getPos()) > rangeSq) continue;
            TilePedestal pedestal = (TilePedestal) tile;
            if (world.getBlockState(pedestal.getPos()).getBlock() != BlocksTC.pedestalEldritch
                    || excluded != null && excluded.equals(pedestal.getPos())) continue;
            ItemStack stack = pedestal.getStackInSlot(0);
            if (occupied ? !stack.isEmpty() && host.canAcceptAutomatedStack(stack)
                    : stack.isEmpty() && !isReservedByOther(pedestal.getPos())) {
                candidates.add(pedestal);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator
                .comparingDouble((TilePedestal tile) -> tile.getPos().distanceSq(owner.getPos()))
                .thenComparingLong(tile -> tile.getPos().toLong()));
        return candidates.get(0);
    }

    @Nullable
    private TilePedestal getUsablePedestal(BlockPos pedestalPos) {
        World world = owner.getWorld();
        if (world == null || !world.isBlockLoaded(pedestalPos)
                || world.getBlockState(pedestalPos).getBlock() != BlocksTC.pedestalEldritch) return null;
        TileEntity tile = world.getTileEntity(pedestalPos);
        return tile instanceof TilePedestal ? (TilePedestal) tile : null;
    }

    private void syncPedestal(TilePedestal pedestal) {
        pedestal.markDirty();
        World world = owner.getWorld();
        if (world != null) world.notifyBlockUpdate(pedestal.getPos(),
                world.getBlockState(pedestal.getPos()), world.getBlockState(pedestal.getPos()), 3);
    }

    private void drop(ItemStack stack, Vec3d point) {
        World world = owner.getWorld();
        if (world != null && !stack.isEmpty()) world.spawnEntity(
                new EntityItem(world, point.x, point.y, point.z, stack.copy()));
    }

    private boolean reserve(BlockPos target) {
        World world = owner.getWorld();
        if (world == null) return false;
        synchronized (OUTPUT_RESERVATIONS) {
            Map<Long, BlockPos> reservations = OUTPUT_RESERVATIONS.computeIfAbsent(
                    world, ignored -> new java.util.HashMap<>());
            BlockPos otherOwner = reservations.get(target.toLong());
            if (otherOwner != null && !otherOwner.equals(owner.getPos())) {
                TileEntity other = world.getTileEntity(otherOwner);
                if (other instanceof ParadoxPedestalAutomationHost
                        && ((ParadoxPedestalAutomationHost) other)
                        .hasPedestalReservation(target)) return false;
            }
            reservations.put(target.toLong(), owner.getPos().toImmutable());
            return true;
        }
    }

    private void release(BlockPos target) {
        World world = owner.getWorld();
        if (world == null) return;
        synchronized (OUTPUT_RESERVATIONS) {
            Map<Long, BlockPos> reservations = OUTPUT_RESERVATIONS.get(world);
            if (reservations != null && owner.getPos().equals(reservations.get(target.toLong()))) {
                reservations.remove(target.toLong());
            }
        }
    }

    private boolean isReservedByOther(BlockPos target) {
        World world = owner.getWorld();
        if (world == null) return false;
        synchronized (OUTPUT_RESERVATIONS) {
            Map<Long, BlockPos> reservations = OUTPUT_RESERVATIONS.get(world);
            if (reservations == null) return false;
            BlockPos otherOwner = reservations.get(target.toLong());
            if (otherOwner == null || otherOwner.equals(owner.getPos())) return false;
            TileEntity other = world.getTileEntity(otherOwner);
            if (other instanceof ParadoxPedestalAutomationHost
                    && ((ParadoxPedestalAutomationHost) other).hasPedestalReservation(target)) {
                return true;
            }
            reservations.remove(target.toLong());
            return false;
        }
    }

    private void ensureOriginSize() {
        int size = host.getAutomationSlotCount();
        if (origins.length == size) return;
        BlockPos[] resized = new BlockPos[size];
        System.arraycopy(origins, 0, resized, 0, Math.min(origins.length, size));
        origins = resized;
    }

    public void writeToNBT(NBTTagCompound parent) {
        ensureOriginSize();
        NBTTagCompound tag = new NBTTagCompound();
        for (int slot = 0; slot < origins.length; slot++) {
            if (origins[slot] != null) tag.setLong("Origin" + slot, origins[slot].toLong());
        }
        NBTTagList list = new NBTTagList();
        for (Transfer transfer : transfers) list.appendTag(transfer.writeToNBT());
        tag.setTag("Transfers", list);
        parent.setTag("PedestalAutomation", tag);
    }

    public void readFromNBT(NBTTagCompound parent) {
        ensureOriginSize();
        transfers.clear();
        for (int slot = 0; slot < origins.length; slot++) origins[slot] = null;
        if (!parent.hasKey("PedestalAutomation", Constants.NBT.TAG_COMPOUND)) return;
        NBTTagCompound tag = parent.getCompoundTag("PedestalAutomation");
        for (int slot = 0; slot < origins.length; slot++) {
            if (tag.hasKey("Origin" + slot, Constants.NBT.TAG_LONG)) {
                origins[slot] = BlockPos.fromLong(tag.getLong("Origin" + slot));
            }
        }
        NBTTagList list = tag.getTagList("Transfers", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            Transfer transfer = Transfer.readFromNBT(list.getCompoundTagAt(index));
            if (transfer != null && transfer.slot >= 0
                    && transfer.slot < host.getAutomationSlotCount()) transfers.add(transfer);
        }
    }

    private static Vec3d pedestalPoint(BlockPos pedestalPos) {
        return new Vec3d(pedestalPos.getX() + 0.5D,
                pedestalPos.getY() + PEDESTAL_ITEM_HEIGHT, pedestalPos.getZ() + 0.5D);
    }

    public static final class Transfer {
        private final int slot;
        private final boolean inbound;
        private final ItemStack stack;
        private final BlockPos pedestalPos;
        private final long startedAt;
        private final Vec3d start;
        private final Vec3d end;

        private Transfer(int slot, boolean inbound, ItemStack stack, BlockPos pedestalPos,
                         long startedAt, Vec3d start, Vec3d end) {
            this.slot = slot;
            this.inbound = inbound;
            this.stack = stack;
            this.pedestalPos = pedestalPos;
            this.startedAt = startedAt;
            this.start = start;
            this.end = end;
        }

        public ItemStack getStack() { return stack; }
        public long getStartedAt() { return startedAt; }
        public Vec3d getStart() { return start; }
        public Vec3d getEnd() { return end; }

        private NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", slot);
            tag.setBoolean("Inbound", inbound);
            tag.setLong("Pedestal", pedestalPos.toLong());
            tag.setLong("StartedAt", startedAt);
            tag.setTag("Item", stack.writeToNBT(new NBTTagCompound()));
            writeVector(tag, "Start", start);
            writeVector(tag, "End", end);
            return tag;
        }

        @Nullable
        private static Transfer readFromNBT(NBTTagCompound tag) {
            ItemStack stack = new ItemStack(tag.getCompoundTag("Item"));
            if (stack.isEmpty() || !tag.hasKey("Pedestal", Constants.NBT.TAG_LONG)) return null;
            return new Transfer(tag.getInteger("Slot"), tag.getBoolean("Inbound"), stack,
                    BlockPos.fromLong(tag.getLong("Pedestal")), tag.getLong("StartedAt"),
                    readVector(tag, "Start"), readVector(tag, "End"));
        }

        private static void writeVector(NBTTagCompound tag, String key, Vec3d vector) {
            tag.setDouble(key + "X", vector.x);
            tag.setDouble(key + "Y", vector.y);
            tag.setDouble(key + "Z", vector.z);
        }

        private static Vec3d readVector(NBTTagCompound tag, String key) {
            return new Vec3d(tag.getDouble(key + "X"), tag.getDouble(key + "Y"),
                    tag.getDouble(key + "Z"));
        }
    }
}
