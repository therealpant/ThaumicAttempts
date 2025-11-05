// src/main/java/therealpant/thaumicattempts/client/SealGuiGuard.java
package therealpant.thaumicattempts.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class SealGuiGuard {

    // Блокируем клики, если контейнер/мир/игрок уже ушли
    @SubscribeEvent
    public static void onMousePre(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (!isSealGuiSafe()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyPre(GuiScreenEvent.KeyboardInputEvent.Pre e) {
        if (!isSealGuiSafe()) e.setCanceled(true);
    }

    // На всякий случай: если мир выгружается — закрыть GUI
    @SubscribeEvent
    public static void onGuiOpen(GuiOpenEvent e) {
        // ничего, просто наличие подписки помогает в моменты смены экранов
        // при выгрузке мира Forge сам вызовет закрытие экрана
    }

    private static boolean isSealGuiSafe() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return false;
        if (!(mc.currentScreen instanceof thaumcraft.common.golems.client.gui.SealBaseGUI))
            return true; // нас интересует только GUI печатей

        return mc.world != null && mc.player != null && mc.player.openContainer != null;
    }
}
