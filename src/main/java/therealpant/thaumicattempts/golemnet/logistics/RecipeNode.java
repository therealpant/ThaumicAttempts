package therealpant.thaumicattempts.golemnet.logistics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import therealpant.thaumicattempts.golemnet.planner.ProviderType;
import therealpant.thaumicattempts.util.ItemKey;

import java.util.LinkedHashMap;
import java.util.Map;

public class RecipeNode {
    public final ItemKey result;
    public final BlockPos source;
    public final ProviderType providerType;
    public final int outputPerCycle;
    public final LinkedHashMap<ItemKey, Integer> inputs;

    public RecipeNode(ItemKey result, BlockPos source, ProviderType providerType, int outputPerCycle, Map<ItemKey, Integer> inputs) {
        this.result = result;
        this.source = source == null ? BlockPos.ORIGIN : source.toImmutable();
        this.providerType = providerType == null ? ProviderType.GOLEM_CRAFTER : providerType;
        this.outputPerCycle = Math.max(1, outputPerCycle);
        this.inputs = new LinkedHashMap<>();
        if (inputs != null) {
            for (Map.Entry<ItemKey, Integer> e : inputs.entrySet()) {
                if (e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
                this.inputs.put(e.getKey(), Math.max(1, e.getValue()));
            }
        }
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound out = new NBTTagCompound();
        out.setTag("result", result.toStack(1).writeToNBT(new NBTTagCompound()));
        out.setLong("source", source.toLong());
        out.setString("provider", providerType.name());
        out.setInteger("outPerCycle", outputPerCycle);
        NBTTagList in = new NBTTagList();
        for (Map.Entry<ItemKey, Integer> e : inputs.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("key", e.getKey().toStack(1).writeToNBT(new NBTTagCompound()));
            row.setInteger("amount", Math.max(1, e.getValue()));
            in.appendTag(row);
        }
        out.setTag("inputs", in);
        return out;
    }

    public static RecipeNode readFromNbt(NBTTagCompound tag) {
        ItemKey result = ItemKey.of(new net.minecraft.item.ItemStack(tag.getCompoundTag("result")));
        BlockPos source = tag.hasKey("source", Constants.NBT.TAG_LONG) ? BlockPos.fromLong(tag.getLong("source")) : BlockPos.ORIGIN;
        ProviderType pt;
        try {
            pt = ProviderType.valueOf(tag.getString("provider"));
        } catch (Exception ignored) {
            pt = ProviderType.GOLEM_CRAFTER;
        }
        int out = Math.max(1, tag.getInteger("outPerCycle"));
        LinkedHashMap<ItemKey, Integer> inputs = new LinkedHashMap<ItemKey, Integer>();
        NBTTagList in = tag.getTagList("inputs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < in.tagCount(); i++) {
            NBTTagCompound row = in.getCompoundTagAt(i);
            ItemKey key = ItemKey.of(new net.minecraft.item.ItemStack(row.getCompoundTag("key")));
            if (key == ItemKey.EMPTY) continue;
            inputs.put(key, Math.max(1, row.getInteger("amount")));
        }
        return new RecipeNode(result, source, pt, out, inputs);
    }
}