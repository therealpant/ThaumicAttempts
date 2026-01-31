package therealpant.thaumicattempts.capability;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

public class AmberCasterStorage implements Capability.IStorage<IAmberCasterData> {
    private static final String TAG_FREQUENCY = "frequency";
    private static final String TAG_LAST_UPDATE = "last_update";

    @Override
    public NBTBase writeNBT(Capability<IAmberCasterData> capability, IAmberCasterData instance, EnumFacing side) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setFloat(TAG_FREQUENCY, instance.getFrequency());
        tag.setLong(TAG_LAST_UPDATE, instance.getLastUpdateTick());
        return tag;
    }

    @Override
    public void readNBT(Capability<IAmberCasterData> capability, IAmberCasterData instance, EnumFacing side, NBTBase nbt) {
        if (!(nbt instanceof NBTTagCompound)) return;
        NBTTagCompound tag = (NBTTagCompound) nbt;
        instance.setFrequency(tag.getFloat(TAG_FREQUENCY));
        instance.setLastUpdateTick(tag.getLong(TAG_LAST_UPDATE));
    }
}
