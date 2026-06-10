package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
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

        float[] prevLight = RenderSafety.captureLightmap();

        try {
            super.render(te, x, y, z, partialTicks, destroyStage, alpha);

            renderEmissiveLayer(te, x, y, z, partialTicks);
            if (RenderSafety.isItemRender()) {
                return;
            }
            renderPearlLikePedestal(te, x, y - 0.3, z, partialTicks);
        } finally {
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderEmissiveLayer(TileAuraBooster te, double x, double y, double z, float partialTicks) {
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

    private void renderPearlLikePedestal(TileAuraBooster te, double x, double y, double z, float partialTicks) {
        ItemStack stack = te.getPearlStack();
        if (stack == null || stack.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;

        float ticks = (float) mc.getRenderViewEntity().ticksExisted + partialTicks;

        GL11.glPushMatrix();
        try {
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
            RenderSafety.resetGlState();
        }
    }
}
