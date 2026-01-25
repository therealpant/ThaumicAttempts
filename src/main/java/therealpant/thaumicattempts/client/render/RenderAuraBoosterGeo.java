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
import therealpant.thaumicattempts.client.model.AuraBoosterModel;
import therealpant.thaumicattempts.world.tile.TileAuraBooster;

@SideOnly(Side.CLIENT)
public class RenderAuraBoosterGeo extends GeoBlockRenderer<TileAuraBooster> {

    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/aura_buffer_e.png");

    // чтобы эмиссив не "дрался" с глубиной, но и не улетал
    private static final float EMISSIVE_Y_OFFSET = 0.0015f;

    public RenderAuraBoosterGeo() {
        super(new AuraBoosterModel());
    }

    @Override
    public void render(TileAuraBooster te,
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

            // 1) обычный Geo рендер
            super.render(te, x, y, z, partialTicks, destroyStage, alpha);

            // 2) эмиссив (поверх)
            renderEmissiveLayer(te, x, y, z, partialTicks);

            // 3) жемчужина (1-в-1 как пьедестал)
            renderPearlLikePedestal(te, x,  y- 0.3, z, partialTicks);

        } finally {
            if (pushedAttrib) GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
        }
    }

    private void renderEmissiveLayer(TileAuraBooster te, double x, double y, double z, float partialTicks) {
        GeoModel model = this.getGeoModelProvider().getModel(
                this.getGeoModelProvider().getModelLocation(te)
        );

        float prevX = OpenGlHelper.lastBrightnessX;
        float prevY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        try {
            // ВАЖНО: GeoBlockRenderer уже рендерит модель в мировых координатах по (x,y,z).
            // Мы делаем такой же "мировой" translate, как и для предмета/пьедестала.
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

            // render(...) у GeoBlockRenderer рисует модель вокруг (0,0,0) текущей матрицы
            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);

            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }

    private void renderPearlLikePedestal(TileAuraBooster te, double x, double y, double z, float partialTicks) {
        ItemStack stack = te.getPearlStack();
        if (stack == null || stack.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getRenderViewEntity() == null) return;

        float ticks = (float) mc.getRenderViewEntity().ticksExisted + partialTicks;

        GL11.glPushMatrix();
        try {
            // 1-в-1 как TilePedestalRenderer
            GL11.glTranslatef((float) x + 0.5F, (float) y + 0.75F, (float) z + 0.5F);
            GL11.glScaled(1.25D, 1.25D, 1.25D);
            GL11.glRotatef(ticks % 360.0F, 0.0F, 1.0F, 0.0F);

            ItemStack is = stack.copy();
            is.setCount(1);

            EntityItem entityitem = new EntityItem(mc.world, 0.0D, 0.0D, 0.0D, is);
            entityitem.hoverStart = 0.0F;

            RenderManager rendermanager = mc.getRenderManager();
            rendermanager.renderEntity(entityitem, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, false);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
