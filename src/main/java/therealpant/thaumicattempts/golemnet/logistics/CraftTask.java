package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.LinkedHashMap;
import java.util.Map;

public class CraftTask extends RuntimeTask {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger("ThaumicAttempts/CraftTask");

    public EndpointRef crafter;
    public ItemKey recipeKey;
    public EndpointRef outputEndpoint;
    public final LinkedHashMap<ItemKey, Integer> requiredInputs = new LinkedHashMap<ItemKey, Integer>();

    @Override
    public String getTaskType() {
        return "CRAFT";
    }

    @Override
    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        writeCommon(tag);
        if (crafter != null) {
            tag.setTag("crafter", crafter.writeToNbt());
        } else {
            LOG.warn("[CraftTask] writeToNbt missing required field crafter taskId={} orderId={}", taskId, orderId);
        }
        if (recipeKey != null && recipeKey != ItemKey.EMPTY) {
            tag.setTag("recipeKey", recipeKey.toStack(1).writeToNBT(new NBTTagCompound()));
        } else {
            LOG.warn("[CraftTask] writeToNbt missing required field recipeKey taskId={} orderId={}", taskId, orderId);
        }
        if (outputEndpoint != null) {
            tag.setTag("output", outputEndpoint.writeToNbt());
        } else {
            LOG.warn("[CraftTask] writeToNbt missing required field outputEndpoint taskId={} orderId={}", taskId, orderId);
        }


        NBTTagList ins = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : requiredInputs.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            row.setTag("key", e.getKey().toStack(1).writeToNBT(new NBTTagCompound()));
            row.setInteger("amount", e.getValue());
            ins.appendTag(row);
        }
        tag.setTag("inputs", ins);
        return tag;
    }

    @Override
    protected void readFromNbtImpl(NBTTagCompound tag) {
        readCommon(tag);
        crafter = tag.hasKey("crafter", Constants.NBT.TAG_COMPOUND)
                ? EndpointRef.readFromNbt(tag.getCompoundTag("crafter")) : null;
        recipeKey = tag.hasKey("recipeKey", Constants.NBT.TAG_COMPOUND)
                ? ItemKey.of(new net.minecraft.item.ItemStack(tag.getCompoundTag("recipeKey"))) : ItemKey.EMPTY;
        outputEndpoint = tag.hasKey("output", Constants.NBT.TAG_COMPOUND)
                ? EndpointRef.readFromNbt(tag.getCompoundTag("output")) : null;
        requiredInputs.clear();
        NBTTagList ins = tag.getTagList("inputs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ins.tagCount(); i++) {
            NBTTagCompound row = ins.getCompoundTagAt(i);
            ItemKey key = ItemKey.of(new net.minecraft.item.ItemStack(row.getCompoundTag("key")));
            if (key == null || key == ItemKey.EMPTY) continue;
            requiredInputs.put(key, Math.max(1, row.getInteger("amount")));
        }
    }
}