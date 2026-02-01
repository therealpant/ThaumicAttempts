package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.RiftExtractorModel;
import therealpant.thaumicattempts.world.tile.TileRiftExtractor;

@SideOnly(Side.CLIENT)
public class RenderRiftExtractor extends GeoBlockRenderer<TileRiftExtractor> {

    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/mirror_manager_e.png");
    private static final float EMISSIVE_Y_OFFSET = 0.01f;

    private static final double CROWN_Y = 33.5 / 16.0;
    private static final double CORE_Y = 17.0 / 16.0;

    public RenderRiftExtractor() {
        super(new RiftExtractorModel());
    }

    private void renderEmissiveLayer(TileRiftExtractor te, double x, double y, double z, float partialTicks) {
        GeoModel model = this.getGeoModelProvider().getModel(
                this.getGeoModelProvider().getModelLocation(te)
        );

        float prevX = OpenGlHelper.lastBrightnessX;
        float prevY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y + EMISSIVE_Y_OFFSET, z + 0.5);

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            GlStateManager.color(1F, 1F, 1F, 1F);

            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_EMISSIVE);
            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);

            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float computeCoreAlpha(int stage, int maxStage, float stageProgress01) {
        if (maxStage <= 0) return 1.0f;

        // Нормализуем общий прогресс: stage + прогресс внутри стадии
        float s = clamp01(stageProgress01);
        float t = (stage + s) / (float) maxStage;   // 0..1

        // Чтобы на 0 стадии было "едва видно", а не полностью 0
        float minAlpha = 0.05f;

        // Можно сделать более "плавно": smoothstep
        t = t * t * (3f - 2f * t);

        return minAlpha + (1.0f - minAlpha) * clamp01(t);
    }

    @Override
    public void render(TileRiftExtractor te,
                       double x, double y, double z,
                       float partialTicks,
                       int destroyStage,
                       float alpha) {

        if (te == null || te.getWorld() == null) return;

        boolean pushedAttrib = false;
        float prevX = OpenGlHelper.lastBrightnessX;
        float prevY = OpenGlHelper.lastBrightnessY;

        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            pushedAttrib = true;

            super.render(te, x, y, z, partialTicks, destroyStage, alpha);
            renderEmissiveLayer(te, x, y, z, partialTicks);

            renderItemStack(te.getCrownStack(), x, y + CROWN_Y, z, partialTicks, 1.0F);
            float coreAlpha = te.getCoreAlphaSmooth(partialTicks);
            renderItemStack(te.getCoreRenderStack(), x, y + CORE_Y, z, partialTicks, coreAlpha);

        } finally {
            if (pushedAttrib) {
                GL11.glPopAttrib();
            }
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
        }
    }

    private void renderItemStack(ItemStack stack, double x, double y, double z, float partialTicks, float alpha) {
        if (stack == null || stack.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getRenderViewEntity() == null) return;

        if (alpha <= 0.001F) return;

        float ticks = (float) mc.getRenderViewEntity().ticksExisted + partialTicks;

        GL11.glPushMatrix();
        try {
            // позиция слота
            GL11.glTranslatef((float) x + 0.5F, (float) y, (float) z + 0.5F);

            ItemStack is = stack.copy();
            is.setCount(1);

            // получаем модель + overrides
            IBakedModel model = mc.getRenderItem().getItemModelWithOverrides(is, mc.world, null);

            // применяем "display.ground" трансформ (как у нормального предмета на земле)
            // ВАЖНО: вращение должно быть ПОСЛЕ этого вызова
            model = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GROUND, false);

            // --- ВРАЩЕНИЕ ВОКРУГ ЦЕНТРА (исправляет "вокруг угла") ---
            float rot = ticks % 360.0F;

            // для ground у большинства предметов правильнее центр по XZ, без подъёма по Y
            GlStateManager.translate(0.0F, 0.0F, 0.0F);
            GlStateManager.rotate(rot, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5F, 0.0F, -0.5F);
            // ---------------------------------------------------------

            // ARGB с альфой
            int a = MathHelper.clamp((int) (alpha * 255.0F), 0, 255);
            int argb = (a << 24) | 0xFFFFFF;

            // blend только когда реально нужна прозрачность
            boolean translucent = alpha < 0.999F;
            if (translucent) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                );
                GlStateManager.depthMask(false);
            } else {
                GlStateManager.disableBlend();
                GlStateManager.depthMask(true);
            }

            RenderHelper.enableStandardItemLighting();
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);

            for (EnumFacing f : EnumFacing.values()) {
                for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(null, f, 0L)) {
                    LightUtil.renderQuadColor(buf, q, argb);
                }
            }
            for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(null, null, 0L)) {
                LightUtil.renderQuadColor(buf, q, argb);
            }

            tess.draw();

            RenderHelper.disableStandardItemLighting();

            if (translucent) {
                GlStateManager.depthMask(true);
                GlStateManager.disableBlend();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }
}