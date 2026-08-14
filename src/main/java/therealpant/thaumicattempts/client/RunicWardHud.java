package therealpant.thaumicattempts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.effects.AmethystEffects;

/** Draws the independently synchronized runic ward pool above the health bar. */
public final class RunicWardHud {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            ThaumicAttempts.MODID, "textures/ef/rune.png");

    @SubscribeEvent
    public void afterHealth(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HEALTH
                || !RunicWardClientState.isActive()) return;

        float shield = RunicWardClientState.getCurrent();
        int max = Math.min(AmethystEffects.MAX_VISIBLE_SHIELD, RunicWardClientState.getMax());
        if (shield <= 0f || max <= 0) return;

        int slots = Math.min(AmethystEffects.MAX_WARDS,
                (int) Math.ceil(max / (float) AmethystEffects.WARD_UNIT));
        int left = event.getResolution().getScaledWidth() / 2 - 90;
        int y = event.getResolution().getScaledHeight() - 38;

        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        for (int i = 0; i < slots; i++) {
            float remaining = shield - i * AmethystEffects.WARD_UNIT;
            int quarters = Math.min(4, Math.max(0, (int) Math.ceil(remaining)));
            if (quarters <= 0) continue;
            int state = 4 - quarters;
            int u = (state & 1) * 9;
            int v = (state >> 1) * 9;
            Gui.drawModalRectWithCustomSizedTexture(left + i * 8, y, u, v,
                    7, 7, 16f, 16f);
        }
        GlStateManager.disableBlend();
    }
}
