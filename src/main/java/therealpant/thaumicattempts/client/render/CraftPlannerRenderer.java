package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.CraftPlannerModel;
import therealpant.thaumicattempts.golemnet.tile.TileCraftPlanner;

@SideOnly(Side.CLIENT)
public class CraftPlannerRenderer extends GeoBlockRenderer<TileCraftPlanner> {

    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/planer_e.png");

    private static final float EMISSIVE_Y_OFFSET = 0.01F;

    public CraftPlannerRenderer() {
        super(new CraftPlannerModel());
    }

    @Override
    public void render(TileCraftPlanner te,
                       double x, double y, double z,
                       float partialTicks,
                       int destroyStage,
                       float alpha) {

        if (te == null || te.getWorld() == null) return;

        float prevX = OpenGlHelper.lastBrightnessX;
        float prevY = OpenGlHelper.lastBrightnessY;
        int packedLight = te.getWorld().getCombinedLight(te.getPos(), 0);
        float blockLightX = packedLight & 0xFFFF;
        float blockLightY = (packedLight >> 16) & 0xFFFF;

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, blockLightX, blockLightY);
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
        renderEmissiveLayer(te, x, y, z, partialTicks);
    }

    private void renderEmissiveLayer(TileCraftPlanner te,
                                     double x, double y, double z,
                                     float partialTicks) {
        if (te == null || te.getWorld() == null) return;

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

            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_EMISSIVE);
            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);

            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();

        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }
}