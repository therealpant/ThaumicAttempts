package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.RevisionPiedestalModel;
import therealpant.thaumicattempts.golemnet.tile.TileRevisionPiedestal;

@SideOnly(Side.CLIENT)
public class RenderRevisionPiedestalGeo extends GeoBlockRenderer<TileRevisionPiedestal> {

    private static final ResourceLocation TEX_EMISSIVE_ON =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/revision_on.png");
    private static final ResourceLocation TEX_EMISSIVE_OFF =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/revision_off.png");

    private static final float EMISSIVE_Y_OFFSET = 0.0015f;

    public RenderRevisionPiedestalGeo() {
        super(new RevisionPiedestalModel());
    }

    @Override
    public void render(TileRevisionPiedestal te,
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
        } finally {
            if (pushedAttrib) GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
        }
    }

    private void renderEmissiveLayer(TileRevisionPiedestal te,
                                     double x, double y, double z,
                                     float partialTicks) {
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

            Minecraft.getMinecraft().getTextureManager()
                    .bindTexture(te.isActive() ? TEX_EMISSIVE_ON : TEX_EMISSIVE_OFF);

            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);

            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }
}