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
import therealpant.thaumicattempts.client.model.DispatcherModel;
import therealpant.thaumicattempts.golemnet.tile.TileGolemDispatcher;

@SideOnly(Side.CLIENT)
public class DispatcherRenderer extends GeoBlockRenderer<TileGolemDispatcher> {

    // эмисс-слой (только подсвечиваемые пиксели)
    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/dispatcher_e.png");

    // если нужно чуть приподнять эмисс над моделью – правь это значение
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

        // 1) базовая гео-модель с обычной текстурой
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);

        // 2) эмисс-проход поверх неё
        renderEmissiveLayer(te, x, y, z, partialTicks);
    }

    /**
     * Второй проход: та же Geo-модель, но с эмисс-текстурой и фуллбрайтом.
     */
    private void renderEmissiveLayer(TileGolemDispatcher te,
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

            // Фуллбрайт
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

            GlStateManager.disableLighting();

            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_EMISSIVE);

            // Рендерим как непрозрачный фуллбрайт слой
            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);

            // Возвращаем настройки OpenGL
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();

        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }
}
