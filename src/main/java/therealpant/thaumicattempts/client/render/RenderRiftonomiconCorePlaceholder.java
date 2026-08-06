package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.RiftonomiconCorePlaceholderModel;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconCorePlaceholder;

public class RenderRiftonomiconCorePlaceholder extends GeoBlockRenderer<TileRiftonomiconCorePlaceholder> {
    private static final ResourceLocation EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftonomicon_\u0435.png");

    public RenderRiftonomiconCorePlaceholder() {
        super(new RiftonomiconCorePlaceholderModel());
    }

    @Override
    public void render(TileRiftonomiconCorePlaceholder tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null) return;
        float[] previousLight = RenderSafety.captureLightmap();
        try {
            super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
            renderEmissive(tile, x, y, z, partialTicks);
        } finally {
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderEmissive(TileRiftonomiconCorePlaceholder tile, double x, double y, double z,
                                float partialTicks) {
        GeoModel model = getGeoModelProvider().getModel(getGeoModelProvider().getModelLocation(tile));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + 0.01D, z + 0.5D);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            Minecraft.getMinecraft().getTextureManager().bindTexture(EMISSIVE);
            this.render(model, tile, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
