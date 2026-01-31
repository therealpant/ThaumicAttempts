package therealpant.thaumicattempts.capability;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import therealpant.thaumicattempts.ThaumicAttempts;

public final class AmberCasterCapability {
    public static final ResourceLocation ID = new ResourceLocation(ThaumicAttempts.MODID, "amber_casting");

    @CapabilityInject(IAmberCasterData.class)
    public static final Capability<IAmberCasterData> CAPABILITY = null;

    private AmberCasterCapability() {}

    public static void register() {
        CapabilityManager.INSTANCE.register(IAmberCasterData.class, new AmberCasterStorage(), AmberCasterData::new);
    }

    @Nullable
    public static IAmberCasterData get(EntityPlayer player) {
        return player != null ? player.getCapability(CAPABILITY, null) : null;
    }
}
