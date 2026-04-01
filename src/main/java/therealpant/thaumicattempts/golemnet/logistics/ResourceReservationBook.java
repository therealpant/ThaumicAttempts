package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ResourceReservationBook {
    private static final class Reservation {
        final UUID taskId;
        final UUID orderId;
        @Nullable final BlockPos provider;
        @Nullable final BlockPos destination;
        final ItemKey key;
        final int amount;
        final boolean expectedOutput;

        Reservation(UUID taskId, UUID orderId, @Nullable BlockPos provider, @Nullable BlockPos destination, ItemKey key, int amount, boolean expectedOutput) {
            this.taskId = taskId;
            this.orderId = orderId;
            this.provider = provider == null ? null : provider.toImmutable();
            this.destination = destination == null ? null : destination.toImmutable();
            this.key = key;
            this.amount = Math.max(1, amount);
            this.expectedOutput = expectedOutput;
        }
    }

    private final Map<UUID, Reservation> byTask = new HashMap<UUID, Reservation>();

    public int reserveStock(@Nullable BlockPos providerPos, ItemKey key, int amount, UUID orderId, UUID taskId) {
        if (key == null || key == ItemKey.EMPTY || amount <= 0 || taskId == null) return 0;
        byTask.put(taskId, new Reservation(taskId, orderId, providerPos, null, key, amount, false));
        return amount;
    }

    public int claimExpectedOutput(@Nullable BlockPos crafterPos, ItemKey key, int amount, UUID orderId, UUID taskId) {
        if (key == null || key == ItemKey.EMPTY || amount <= 0 || taskId == null) return 0;
        byTask.put(taskId, new Reservation(taskId, orderId, crafterPos, null, key, amount, true));
        return amount;
    }

    public void releaseReservation(UUID taskId) {
        if (taskId != null) byTask.remove(taskId);
    }

    public int getReservedAmount(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return 0;
        int out = 0;
        for (Reservation r : byTask.values()) if (!r.expectedOutput && key.equals(r.key)) out += r.amount;
        return out;
    }

    public int getExpectedAmount(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return 0;
        int out = 0;
        for (Reservation r : byTask.values()) if (r.expectedOutput && key.equals(r.key)) out += r.amount;
        return out;
    }

    public int getIncomingReservedFor(BlockPos destination, ItemKey key) {
        if (destination == null || key == null || key == ItemKey.EMPTY) return 0;
        int out = 0;
        for (Reservation r : byTask.values()) {
            if (r.destination == null || !destination.equals(r.destination)) continue;
            if (key.equals(r.key)) out += r.amount;
        }
        return out;
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList rows = new NBTTagList();
        for (Reservation r : byTask.values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("task", r.taskId.toString());
            row.setString("order", r.orderId.toString());
            if (r.provider != null) row.setLong("provider", r.provider.toLong());
            if (r.destination != null) row.setLong("destination", r.destination.toLong());
            row.setTag("key", r.key.toStack(1).writeToNBT(new NBTTagCompound()));
            row.setInteger("amount", r.amount);
            row.setBoolean("expected", r.expectedOutput);
            rows.appendTag(row);
        }
        tag.setTag("rows", rows);
        return tag;
    }

    public void readFromNbt(NBTTagCompound tag) {
        byTask.clear();
        NBTTagList rows = tag.getTagList("rows", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            UUID taskId = UUID.fromString(row.getString("task"));
            UUID orderId = UUID.fromString(row.getString("order"));
            BlockPos provider = row.hasKey("provider", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(row.getLong("provider")) : null;
            BlockPos destination = row.hasKey("destination", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(row.getLong("destination")) : null;
            ItemKey key = ItemKey.of(new net.minecraft.item.ItemStack(row.getCompoundTag("key")));
            int amount = Math.max(1, row.getInteger("amount"));
            boolean expected = row.getBoolean("expected");
            byTask.put(taskId, new Reservation(taskId, orderId, provider, destination, key, amount, expected));
        }
    }
}