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

    public String validationError() {
        if (taskId == null) return "missing-task-id";
        if (orderId == null) return "missing-order-id";
        if (crafter == null || crafter.pos == null) return "missing-crafter-endpoint";
        if (recipeKey == null || recipeKey == ItemKey.EMPTY) return "missing-recipe-key";
        if (outputEndpoint == null || outputEndpoint.pos == null) return "missing-output-endpoint";
        if (requiredInputs.isEmpty()) return "missing-required-inputs";
        for (Map.Entry<ItemKey, Integer> e : requiredInputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) return "invalid-required-input-key";
            if (e.getValue() == null || e.getValue() <= 0) return "invalid-required-input-amount";
        }
        return null;
    }

    public boolean isValidRuntimeTask() {
        return validationError() == null;
    }

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
        }

        if (recipeKey != null && recipeKey != ItemKey.EMPTY) {
            tag.setTag("recipeKey", recipeKey.toStack(1).writeToNBT(new NBTTagCompound()));
        }

        if (outputEndpoint != null) {
            tag.setTag("output", outputEndpoint.writeToNbt());
        }

        NBTTagList ins = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : requiredInputs.entrySet()) {
            if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
            if (e.getValue() == null || e.getValue() <= 0) continue;

            NBTTagCompound row = new NBTTagCompound();
            row.setTag("key", e.getKey().toStack(1).writeToNBT(new NBTTagCompound()));
            row.setInteger("amount", e.getValue());
            ins.appendTag(row);
        }
        tag.setTag("inputs", ins);

        String err = validationError();
        if (err != null) {
            LOG.warn("[CraftTask] writeToNbt invalid taskId={} orderId={} reason={}", taskId, orderId, err);
        }

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
        String err = validationError();
        if (err != null) {
            LOG.warn("[CraftTask] readFromNbt invalid taskId={} orderId={} reason={}", taskId, orderId, err);
        }
    }
}