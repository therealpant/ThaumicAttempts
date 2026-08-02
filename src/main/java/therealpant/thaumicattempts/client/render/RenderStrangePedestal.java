package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.StrangePedestalModel;
import therealpant.thaumicattempts.world.tile.TileStrangePedestal;

public class RenderStrangePedestal extends GeoBlockRenderer<TileStrangePedestal> {
    private static final ResourceLocation EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/well_e.png");

    public RenderStrangePedestal() {
        super(new StrangePedestalModel());
    }

    @Override
    public void render(TileStrangePedestal tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null) return;
        float[] previousLight = RenderSafety.captureLightmap();
        try {
            super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
            GeoModel model = getGeoModelProvider().getModel(
                    getGeoModelProvider().getModelLocation(tile));
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + 0.5D, y + 0.0015D, z + 0.5D);
                RenderSafety.setFullBrightLightmap();
                GlStateManager.disableLighting();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
                Minecraft.getMinecraft().getTextureManager().bindTexture(EMISSIVE);
                render(model, tile, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
            } finally {
                GlStateManager.popMatrix();
            }
        } finally {
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
        renderHeldAmethyst(tile, x, y, z, partialTicks);
    }

    private void renderHeldAmethyst(TileStrangePedestal tile,
                                    double x, double y, double z,
                                    float partialTicks) {
        ItemStack held = tile.getHeldItem();
        if (held.isEmpty() || tile.getWorld() == null) return;

        Minecraft minecraft = Minecraft.getMinecraft();
        float ticks = minecraft.getRenderViewEntity().ticksExisted + partialTicks;
        GlStateManager.pushMatrix();
        try {
            // Centre of the air block directly above the one-block pedestal.
            GlStateManager.translate(x + 0.5D, y + 1.5D, z + 0.5D);
            GlStateManager.scale(0.5D, 0.5D, 0.5D);
            GlStateManager.rotate(ticks % 360.0F, 0.0F, 1.0F, 0.0F);
            // FIXED already keeps the sprite plane vertical. Rotate only
            // inside that plane: 45 degrees of texture correction plus the
            // requested 90-degree counter-clockwise turn.
            GlStateManager.rotate(-45.0F, 0.0F, 0.0F, 1.0F);

            ItemStack rendered = held.copy();
            rendered.setCount(1);
            RenderHelper.enableStandardItemLighting();
            minecraft.getRenderItem().renderItem(
                    rendered, ItemCameraTransforms.TransformType.FIXED);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }
}
