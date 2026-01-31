package therealpant.thaumicattempts.events;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import therealpant.thaumicattempts.capability.AmberCasterCapability;
import therealpant.thaumicattempts.capability.AmberCasterProvider;
import therealpant.thaumicattempts.capability.IAmberCasterData;

public class AmberCasterCapabilityHandler {
    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof EntityPlayer)) return;
        event.addCapability(AmberCasterCapability.ID, new AmberCasterProvider());
    }

    @SubscribeEvent
    public void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        EntityPlayer player = event.getEntityPlayer();
        IAmberCasterData data = AmberCasterCapability.get(player);
        if (data != null) {
            data.reset();
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        IAmberCasterData data = AmberCasterCapability.get(event.player);
        if (data != null) {
            data.reset();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        IAmberCasterData data = AmberCasterCapability.get(event.player);
        if (data != null) {
            data.reset();
        }
    }
}
