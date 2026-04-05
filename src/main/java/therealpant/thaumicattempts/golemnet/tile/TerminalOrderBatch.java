package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TerminalOrderBatch {
    public final UUID batchId;
    public final boolean craftTab;
    public final List<UUID> slotIds;
    public final List<UUID> rootOrderIds;
    public boolean done;

    public TerminalOrderBatch(UUID batchId, boolean craftTab) {
        this.batchId = batchId;
        this.craftTab = craftTab;
        this.slotIds = new ArrayList<>();
        this.rootOrderIds = new ArrayList<>();
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("batchId", batchId.toString());
        tag.setBoolean("craftTab", craftTab);
        tag.setBoolean("done", done);
        tag.setTag("slotIds", writeUuidList(slotIds));
        tag.setTag("rootOrderIds", writeUuidList(rootOrderIds));
        return tag;
    }

    public static TerminalOrderBatch readFromNbt(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("batchId", Constants.NBT.TAG_STRING)) return null;
        UUID batchId;
        try {
            batchId = UUID.fromString(tag.getString("batchId"));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        TerminalOrderBatch batch = new TerminalOrderBatch(batchId, tag.getBoolean("craftTab"));
        batch.done = tag.getBoolean("done");
        readUuidList(tag.getTagList("slotIds", Constants.NBT.TAG_STRING), batch.slotIds);
        readUuidList(tag.getTagList("rootOrderIds", Constants.NBT.TAG_STRING), batch.rootOrderIds);
        return batch;
    }

    private static NBTTagList writeUuidList(List<UUID> ids) {
        NBTTagList list = new NBTTagList();
        for (UUID id : ids) {
            if (id == null) continue;
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", id.toString());
            list.appendTag(row);
        }
        return list;
    }

    private static void readUuidList(NBTTagList list, List<UUID> out) {
        out.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound row = list.getCompoundTagAt(i);
            if (!row.hasKey("id", Constants.NBT.TAG_STRING)) continue;
            try {
                out.add(UUID.fromString(row.getString("id")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}