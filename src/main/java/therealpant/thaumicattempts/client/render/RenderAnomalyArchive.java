package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.AnomalyArchiveModel;
import therealpant.thaumicattempts.world.tile.TileAnomalyArchive;

@SideOnly(Side.CLIENT)
public class RenderAnomalyArchive extends GeoBlockRenderer<TileAnomalyArchive> {

    private static final ResourceLocation EMISSIVE_TEXTURE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/archi_e.png");
    public RenderAnomalyArchive() {
        super(new AnomalyArchiveModel());
    }

    @Override
    public void render(TileAnomalyArchive tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null || tile.getWorld() == null) return;
        float[] previousLight = RenderSafety.captureLightmap();
        try {
            super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
            renderEmissiveLayer(tile, x, y, z, partialTicks);
        } finally {
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderEmissiveLayer(TileAnomalyArchive tile,
                                     double x, double y, double z,
                                     float partialTicks) {
        GeoModel model = getGeoModelProvider().getModel(
                getGeoModelProvider().getModelLocation(tile));
        float[] previousLight = RenderSafety.captureLightmap();

        GlStateManager.pushMatrix();
        try {
            // GeoBlockRenderer applies this extra Y offset before drawing the
            // regular pass. The emissive pass must use the exact same matrix.
            GlStateManager.translate(x + 0.5D, y + 0.01D, z + 0.5D);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            Minecraft.getMinecraft().getTextureManager().bindTexture(EMISSIVE_TEXTURE);
            render(model, tile, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }
}
