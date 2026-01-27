package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
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

    private static final double CROWN_Y = 37.5 / 16.0;
    private static final double CORE_Y = 23.0 / 16.0;

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
            renderItemStack(te.getCoreStack(), x, y + CORE_Y, z, partialTicks, te.getCoreAlpha());
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

        float ticks = (float) mc.getRenderViewEntity().ticksExisted + partialTicks;

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float) x + 0.5F, (float) y, (float) z + 0.5F);
            GL11.glScaled(1.25D, 1.25D, 1.25D);
            GL11.glRotatef(ticks % 360.0F, 0.0F, 1.0F, 0.0F);

            if (alpha < 1.0F) {
                GlStateManager.enableBlend();
                GlStateManager.color(1F, 1F, 1F, alpha);
            }

            ItemStack is = stack.copy();
            is.setCount(1);

            EntityItem entityitem = new EntityItem(mc.world, 0.0D, 0.0D, 0.0D, is);
            entityitem.hoverStart = 0.0F;

            RenderManager rendermanager = mc.getRenderManager();
            rendermanager.renderEntity(entityitem, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, false);

            if (alpha < 1.0F) {
                GlStateManager.color(1F, 1F, 1F, 1F);
                GlStateManager.disableBlend();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }
}