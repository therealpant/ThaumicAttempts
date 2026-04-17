package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.DispatcherModel;
import therealpant.thaumicattempts.golemnet.tile.TileGolemDispatcher;

@SideOnly(Side.CLIENT)
public class DispatcherRenderer extends GeoBlockRenderer<TileGolemDispatcher> {

    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/dispatcher_e.png");

    private static final float EMISSIVE_Y_OFFSET = 0.01F;

    public DispatcherRenderer() {
        super(new DispatcherModel());
    }

    @Override
    public void render(TileGolemDispatcher te,
                       double x, double y, double z,
                       float partialTicks,
                       int destroyStage,
                       float alpha) {

        if (te == null || te.getWorld() == null) return;

        float[] prevLight = RenderSafety.captureLightmap();
        try {
            super.render(te, x, y, z, partialTicks, destroyStage, alpha);
            if (!RenderSafety.isItemRender()) {
                renderEmissiveLayer(te, x, y, z, partialTicks);
            }
        } finally {
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderEmissiveLayer(TileGolemDispatcher te,
                                     double x, double y, double z,
                                     float partialTicks) {
        if (te == null || te.getWorld() == null) return;

        GeoModel model = this.getGeoModelProvider().getModel(
                this.getGeoModelProvider().getModelLocation(te)
        );

        float[] prevLight = RenderSafety.captureLightmap();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y + EMISSIVE_Y_OFFSET, z + 0.5);

            RenderSafety.setFullBrightLightmap();

            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );

            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_EMISSIVE);

            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }
}
