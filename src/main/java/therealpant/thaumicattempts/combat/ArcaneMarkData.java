package therealpant.thaumicattempts.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import therealpant.thaumicattempts.ThaumicAttempts;

public class ArcaneMarkData extends WorldSavedData {
    private static final String DATA_NAME = ThaumicAttempts.MODID + "_arcane_mark";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_HIT_COUNTER = "hitCounter";

    private final Map<UUID, MarkState> states = new HashMap<>();

    public ArcaneMarkData() {
        super(DATA_NAME);
    }

    public ArcaneMarkData(String name) {
        super(name);
    }

    public static ArcaneMarkData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        ArcaneMarkData data = (ArcaneMarkData) storage.getOrLoadData(ArcaneMarkData.class, DATA_NAME);
        if (data == null) {
            data = new ArcaneMarkData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public MarkState getState(UUID uuid) {
        return states.computeIfAbsent(uuid, key -> new MarkState());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        states.clear();
        NBTTagList list = nbt.getTagList(TAG_PLAYERS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (!tag.hasUniqueId(TAG_UUID)) continue;
            UUID uuid = tag.getUniqueId(TAG_UUID);
            MarkState state = new MarkState();
            state.hitCounter = tag.getInteger(TAG_HIT_COUNTER);
            states.put(uuid, state);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, MarkState> entry : states.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId(TAG_UUID, entry.getKey());
            tag.setInteger(TAG_HIT_COUNTER, entry.getValue().hitCounter);
            list.appendTag(tag);
        }
        nbt.setTag(TAG_PLAYERS, list);
        return nbt;
    }

    public static class MarkState {
        public int hitCounter;
    }
}