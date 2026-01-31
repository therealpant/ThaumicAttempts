package therealpant.thaumicattempts.capability;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

public class AmberCasterProvider implements ICapabilitySerializable<NBTTagCompound> {
    private final IAmberCasterData instance = new AmberCasterData();

    @Override
    public boolean hasCapability(Capability<?> capability, net.minecraft.util.EnumFacing facing) {
        return capability == AmberCasterCapability.CAPABILITY;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, net.minecraft.util.EnumFacing facing) {
        return capability == AmberCasterCapability.CAPABILITY
                ? AmberCasterCapability.CAPABILITY.cast(instance)
                : null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) AmberCasterCapability.CAPABILITY.getStorage().writeNBT(
                AmberCasterCapability.CAPABILITY, instance, null);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        AmberCasterCapability.CAPABILITY.getStorage().readNBT(AmberCasterCapability.CAPABILITY, instance, null, nbt);
    }
}