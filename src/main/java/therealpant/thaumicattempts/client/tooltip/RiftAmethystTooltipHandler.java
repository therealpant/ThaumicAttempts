package therealpant.thaumicattempts.client.tooltip;

import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID, value = Side.CLIENT)
public final class RiftAmethystTooltipHandler {
    private RiftAmethystTooltipHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removeExtraTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty()
                || event.getItemStack().getItem() != ModBlocksItems.RIFT_AMETIST) {
            return;
        }
        // Thaumcraft reserves whitespace-only tooltip rows and later draws
        // the standard aspect panel over them in PostBackground. Preserve
        // those rows, but remove every additional textual description.
        for (int index = event.getToolTip().size() - 1; index >= 1; index--) {
            String line = event.getToolTip().get(index);
            if (line != null && !line.trim().isEmpty()) {
                event.getToolTip().remove(index);
            }
        }
    }
}
