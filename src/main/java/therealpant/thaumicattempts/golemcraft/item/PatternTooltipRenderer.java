package therealpant.thaumicattempts.golemcraft.item;

import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;

/**
 * Client-side renderer that overlays pattern ingredient icons inside tooltips when Shift is held.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class PatternTooltipRenderer {

    private PatternTooltipRenderer() {}

    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.PostText event) {
    }
}