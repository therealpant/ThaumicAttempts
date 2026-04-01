package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.util.ItemKey;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NetworkOrder {
    public UUID orderId;
    @Nullable
    public UUID parentOrderId;
    public OrderSourceType sourceType;
    public BlockPos sourcePos;
    @Nullable
    public BlockPos returnDestination;
    public ItemKey requestedKey;
    public int requestedAmount;
    public int completedAmount;
    public OrderStatus status;
    public long createdTick;
    public long updatedTick;
    public final List<UUID> childOrderIds = new ArrayList<UUID>();
    public final List<UUID> taskIds = new ArrayList<UUID>();
    public String debugReason = "";
    public String lastError = "";

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", orderId.toString());
        if (parentOrderId != null) tag.setString("parent", parentOrderId.toString());
        tag.setString("sourceType", sourceType.name());
        tag.setLong("sourcePos", sourcePos.toLong());
        if (returnDestination != null) tag.setLong("returnPos", returnDestination.toLong());
        tag.setTag("key", requestedKey.toStack(1).writeToNBT(new NBTTagCompound()));
        tag.setInteger("requested", requestedAmount);
        tag.setInteger("completed", completedAmount);
        tag.setString("status", status.name());
        tag.setLong("created", createdTick);
        tag.setLong("updated", updatedTick);
        tag.setString("debug", debugReason == null ? "" : debugReason);
        tag.setString("error", lastError == null ? "" : lastError);

        NBTTagList children = new NBTTagList();
        for (UUID id : childOrderIds) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", id.toString());
            children.appendTag(row);
        }
        tag.setTag("children", children);

        NBTTagList tasks = new NBTTagList();
        for (UUID id : taskIds) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", id.toString());
            tasks.appendTag(row);
        }
        tag.setTag("tasks", tasks);
        return tag;
    }

    public static NetworkOrder readFromNbt(NBTTagCompound tag) {
        NetworkOrder o = new NetworkOrder();
        o.orderId = UUID.fromString(tag.getString("id"));
        if (tag.hasKey("parent", Constants.NBT.TAG_STRING)) o.parentOrderId = UUID.fromString(tag.getString("parent"));
        try {
            o.sourceType = OrderSourceType.valueOf(tag.getString("sourceType"));
        } catch (Exception ignored) {
            o.sourceType = OrderSourceType.INTERNAL_SUBORDER;
        }
        o.sourcePos = tag.hasKey("sourcePos", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(tag.getLong("sourcePos")) : BlockPos.ORIGIN;
        if (tag.hasKey("returnPos", Constants.NBT.TAG_LONG))
            o.returnDestination = BlockPos.fromLong(tag.getLong("returnPos"));
        o.requestedKey = ItemKey.of(new net.minecraft.item.ItemStack(tag.getCompoundTag("key")));
        o.requestedAmount = Math.max(1, tag.getInteger("requested"));
        o.completedAmount = Math.max(0, tag.getInteger("completed"));
        try {
            o.status = OrderStatus.valueOf(tag.getString("status"));
        } catch (Exception ignored) {
            o.status = OrderStatus.PLACED;
        }
        o.createdTick = tag.getLong("created");
        o.updatedTick = tag.getLong("updated");
        o.debugReason = tag.getString("debug");
        o.lastError = tag.getString("error");

        NBTTagList children = tag.getTagList("children", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < children.tagCount(); i++)
            o.childOrderIds.add(UUID.fromString(children.getCompoundTagAt(i).getString("id")));
        NBTTagList tasks = tag.getTagList("tasks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tasks.tagCount(); i++)
            o.taskIds.add(UUID.fromString(tasks.getCompoundTagAt(i).getString("id")));
        return o;
    }
}