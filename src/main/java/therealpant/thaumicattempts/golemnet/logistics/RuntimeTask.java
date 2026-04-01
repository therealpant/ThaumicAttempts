package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class RuntimeTask {
    public UUID taskId;
    public UUID orderId;
    public TaskStatus status;
    public long createdTick;
    public long updatedTick;
    public long amount;
    public long completedAmount;
    public long reservedAmount;
    public String metaPurpose = "";
    public final List<UUID> dependsOn = new ArrayList<UUID>();

    public abstract String getTaskType();

    protected void writeCommon(NBTTagCompound tag) {
        tag.setString("taskType", getTaskType());
        tag.setString("id", taskId.toString());
        tag.setString("order", orderId.toString());
        tag.setString("status", status.name());
        tag.setLong("amount", amount);
        tag.setLong("completed", completedAmount);
        tag.setLong("reserved", reservedAmount);
        tag.setLong("created", createdTick);
        tag.setLong("updated", updatedTick);
        tag.setString("purpose", metaPurpose == null ? "" : metaPurpose);
        NBTTagList deps = new NBTTagList();
        for (UUID dep : dependsOn) {
            NBTTagCompound d = new NBTTagCompound();
            d.setString("id", dep.toString());
            deps.appendTag(d);
        }
        tag.setTag("deps", deps);
    }

    protected void readCommon(NBTTagCompound tag) {
        taskId = UUID.fromString(tag.getString("id"));
        orderId = UUID.fromString(tag.getString("order"));
        status = TaskStatus.valueOf(tag.getString("status"));
        amount = tag.getLong("amount");
        completedAmount = tag.getLong("completed");
        reservedAmount = tag.getLong("reserved");
        createdTick = tag.getLong("created");
        updatedTick = tag.getLong("updated");
        metaPurpose = tag.getString("purpose");
        dependsOn.clear();
        NBTTagList deps = tag.getTagList("deps", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < deps.tagCount(); i++) {
            dependsOn.add(UUID.fromString(deps.getCompoundTagAt(i).getString("id")));
        }
    }

    public abstract NBTTagCompound writeToNbt();

    public static RuntimeTask readFromNbt(NBTTagCompound tag) {
        String taskType = tag.getString("taskType");
        RuntimeTask task;
        if ("CRAFT".equals(taskType)) {
            task = new CraftTask();
        } else {
            task = new TransferTask();
        }
        task.readFromNbtImpl(tag);
        return task;
    }

    protected abstract void readFromNbtImpl(NBTTagCompound tag);
}
