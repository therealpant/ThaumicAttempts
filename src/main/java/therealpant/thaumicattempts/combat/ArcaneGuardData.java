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

public class ArcaneGuardData extends WorldSavedData {
    private static final String DATA_NAME = ThaumicAttempts.MODID + "_arcane_guard";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_STACKS = "stacks";
    private static final String TAG_EXPIRE = "expire";
    private static final String TAG_LAST_STACK = "lastStack";
    private static final String TAG_EMPOWERED_UNTIL = "empoweredUntil";
    private static final String TAG_COOLDOWN_UNTIL = "cooldownUntil";

    private final Map<UUID, GuardState> states = new HashMap<>();

    public ArcaneGuardData() {
        super(DATA_NAME);
    }

    public ArcaneGuardData(String name) {
        super(name);
    }

    public static ArcaneGuardData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        ArcaneGuardData data = (ArcaneGuardData) storage.getOrLoadData(ArcaneGuardData.class, DATA_NAME);
        if (data == null) {
            data = new ArcaneGuardData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public GuardState getState(UUID uuid) {
        return states.computeIfAbsent(uuid, key -> new GuardState());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        states.clear();
        NBTTagList list = nbt.getTagList(TAG_PLAYERS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (!tag.hasUniqueId(TAG_UUID)) continue;
            UUID uuid = tag.getUniqueId(TAG_UUID);
            GuardState state = new GuardState();
            state.stacks = tag.getInteger(TAG_STACKS);
            state.expireTime = tag.getLong(TAG_EXPIRE);
            state.lastStackTime = tag.getLong(TAG_LAST_STACK);
            state.empoweredUntil = tag.getLong(TAG_EMPOWERED_UNTIL);
            state.cooldownUntil = tag.getLong(TAG_COOLDOWN_UNTIL);
            states.put(uuid, state);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, GuardState> entry : states.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId(TAG_UUID, entry.getKey());
            GuardState state = entry.getValue();
            tag.setInteger(TAG_STACKS, state.stacks);
            tag.setLong(TAG_EXPIRE, state.expireTime);
            tag.setLong(TAG_LAST_STACK, state.lastStackTime);
            tag.setLong(TAG_EMPOWERED_UNTIL, state.empoweredUntil);
            tag.setLong(TAG_COOLDOWN_UNTIL, state.cooldownUntil);
            list.appendTag(tag);
        }
        nbt.setTag(TAG_PLAYERS, list);
        return nbt;
    }

    public static class GuardState {
        public int stacks;
        public long expireTime;
        public long lastStackTime;
        public long empoweredUntil;
        public long cooldownUntil;
    }
}
