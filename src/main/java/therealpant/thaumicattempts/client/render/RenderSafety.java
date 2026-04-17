package therealpant.thaumicattempts.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;

/**
 * Shared client-side render safety helpers.
 */
public final class RenderSafety {

    private static final ThreadLocal<Integer> ITEM_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);

    private RenderSafety() {
    }

    public static void pushItemRender() {
        ITEM_RENDER_DEPTH.set(ITEM_RENDER_DEPTH.get() + 1);
    }

    public static void popItemRender() {
        int depth = ITEM_RENDER_DEPTH.get() - 1;
        if (depth <= 0) {
            ITEM_RENDER_DEPTH.remove();
        } else {
            ITEM_RENDER_DEPTH.set(depth);
        }
    }

    public static boolean isItemRender() {
        return ITEM_RENDER_DEPTH.get() > 0;
    }

    public static float[] captureLightmap() {
        return new float[]{OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY};
    }

    public static void setFullBrightLightmap() {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
    }

    public static void setLightmapFromPacked(int packedLight) {
        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                (float) (packedLight & 0xFFFF),
                (float) ((packedLight >> 16) & 0xFFFF)
        );
    }

    public static void restoreLightmap(float[] lightmap) {
        if (lightmap != null && lightmap.length >= 2) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightmap[0], lightmap[1]);
        }
    }

    public static void resetGlState() {
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
    }

    public static void beginItemLighting() {
        RenderHelper.enableStandardItemLighting();
    }

    public static void endItemLighting() {
        RenderHelper.disableStandardItemLighting();
        resetGlState();
    }
}