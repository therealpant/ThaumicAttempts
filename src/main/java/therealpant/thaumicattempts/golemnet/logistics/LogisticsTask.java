package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LogisticsTask {
    public UUID taskId;
    public UUID orderId;
    public TaskType type;
    public TaskStatus status;
    @Nullable public BlockPos executorPos;
    @Nullable public BlockPos sourcePos;
    @Nullable public BlockPos destinationPos;
    public ItemKey key;
    public int amount;
    public int reservedAmount;
    public int completedAmount;
    public long createdTick;
    public long updatedTick;
    public final List<UUID> dependsOn = new ArrayList<UUID>();
    public final LinkedHashMap<String, String> meta = new LinkedHashMap<String, String>();

    public boolean isFinished() {
        return status == TaskStatus.DONE || status == TaskStatus.CANCELLED || status == TaskStatus.FAILED;
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("id", taskId.toString());
        t.setString("order", orderId.toString());
        t.setString("type", type.name());
        t.setString("status", status.name());
        if (executorPos != null) t.setLong("exec", executorPos.toLong());
        if (sourcePos != null) t.setLong("src", sourcePos.toLong());
        if (destinationPos != null) t.setLong("dst", destinationPos.toLong());
        t.setTag("key", key.toStack(1).writeToNBT(new NBTTagCompound()));
        t.setInteger("amount", amount);
        t.setInteger("reserved", reservedAmount);
        t.setInteger("completed", completedAmount);
        t.setLong("created", createdTick);
        t.setLong("updated", updatedTick);

        NBTTagList deps = new NBTTagList();
        for (UUID dep : dependsOn) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", dep.toString());
            deps.appendTag(row);
        }
        t.setTag("deps", deps);

        NBTTagList metaTag = new NBTTagList();
        for (Map.Entry<String, String> e : meta.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("k", e.getKey());
            row.setString("v", e.getValue());
            metaTag.appendTag(row);
        }
        t.setTag("meta", metaTag);

        return t;
    }

    public static LogisticsTask readFromNbt(NBTTagCompound t) {
        LogisticsTask task = new LogisticsTask();
        task.taskId = UUID.fromString(t.getString("id"));
        task.orderId = UUID.fromString(t.getString("order"));
        task.type = TaskType.valueOf(t.getString("type"));
        task.status = TaskStatus.valueOf(t.getString("status"));
        if (t.hasKey("exec", Constants.NBT.TAG_LONG)) task.executorPos = BlockPos.fromLong(t.getLong("exec"));
        if (t.hasKey("src", Constants.NBT.TAG_LONG)) task.sourcePos = BlockPos.fromLong(t.getLong("src"));
        if (t.hasKey("dst", Constants.NBT.TAG_LONG)) task.destinationPos = BlockPos.fromLong(t.getLong("dst"));
        task.key = ItemKey.of(new net.minecraft.item.ItemStack(t.getCompoundTag("key")));
        task.amount = Math.max(1, t.getInteger("amount"));
        task.reservedAmount = Math.max(0, t.getInteger("reserved"));
        task.completedAmount = Math.max(0, t.getInteger("completed"));
        task.createdTick = t.getLong("created");
        task.updatedTick = t.getLong("updated");

        NBTTagList deps = t.getTagList("deps", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < deps.tagCount(); i++) task.dependsOn.add(UUID.fromString(deps.getCompoundTagAt(i).getString("id")));

        NBTTagList metaTag = t.getTagList("meta", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < metaTag.tagCount(); i++) {
            NBTTagCompound row = metaTag.getCompoundTagAt(i);
            task.meta.put(row.getString("k"), row.getString("v"));
        }
        return task;
    }
}