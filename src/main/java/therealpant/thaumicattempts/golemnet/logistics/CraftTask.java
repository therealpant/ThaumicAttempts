package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.LinkedHashMap;
import java.util.Map;

public class CraftTask extends RuntimeTask {
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
        tag.setTag("crafter", crafter.writeToNbt());
        tag.setTag("recipeKey", recipeKey.toStack(1).writeToNBT(new NBTTagCompound()));
        tag.setTag("output", outputEndpoint.writeToNbt());

        NBTTagList ins = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : requiredInputs.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
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
        crafter = EndpointRef.readFromNbt(tag.getCompoundTag("crafter"));
        recipeKey = ItemKey.of(new net.minecraft.item.ItemStack(tag.getCompoundTag("recipeKey")));
        outputEndpoint = EndpointRef.readFromNbt(tag.getCompoundTag("output"));
        requiredInputs.clear();
        NBTTagList ins = tag.getTagList("inputs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ins.tagCount(); i++) {
            NBTTagCompound row = ins.getCompoundTagAt(i);
            requiredInputs.put(ItemKey.of(new net.minecraft.item.ItemStack(row.getCompoundTag("key"))), Math.max(1, row.getInteger("amount")));
        }
    }
}