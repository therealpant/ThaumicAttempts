package therealpant.thaumicattempts.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

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

    public static GlStateSnapshot captureGlState() {
        return new GlStateSnapshot(
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glIsEnabled(GL11.GL_LIGHTING),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_BLEND_SRC),
                GL11.glGetInteger(GL11.GL_BLEND_DST),
                GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        );
    }

    public static void restoreGlState(GlStateSnapshot state) {
        if (state == null) return;

        if (state.blend) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(state.blendSrc, state.blendDst);
        } else {
            GlStateManager.disableBlend();
        }
        setEnabledAlpha(state.alpha);
        setEnabledDepth(state.depth);
        setEnabledLighting(state.lighting);
        setEnabledCull(state.cull);
        GlStateManager.depthMask(state.depthMask);
        GlStateManager.setActiveTexture(state.activeTexture);
        GlStateManager.color(1F, 1F, 1F, 1F);
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

    private static void setEnabledAlpha(boolean enabled) {
        if (enabled) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
    }

    private static void setEnabledDepth(boolean enabled) {
        if (enabled) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
    }

    private static void setEnabledLighting(boolean enabled) {
        if (enabled) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
    }

    private static void setEnabledCull(boolean enabled) {
        if (enabled) GlStateManager.enableCull();
        else GlStateManager.disableCull();
    }

    public static final class GlStateSnapshot {
        private final boolean blend;
        private final boolean alpha;
        private final boolean depth;
        private final boolean lighting;
        private final boolean cull;
        private final boolean depthMask;
        private final int blendSrc;
        private final int blendDst;
        private final int activeTexture;

        private GlStateSnapshot(boolean blend, boolean alpha, boolean depth, boolean lighting, boolean cull,
                                boolean depthMask, int blendSrc, int blendDst, int activeTexture) {
            this.blend = blend;
            this.alpha = alpha;
            this.depth = depth;
            this.lighting = lighting;
            this.cull = cull;
            this.depthMask = depthMask;
            this.blendSrc = blendSrc;
            this.blendDst = blendDst;
            this.activeTexture = activeTexture;
        }
    }
}
