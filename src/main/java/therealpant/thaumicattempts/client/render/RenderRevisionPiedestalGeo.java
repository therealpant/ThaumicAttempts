package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
import therealpant.thaumicattempts.client.model.RevisionPiedestalModel;
import therealpant.thaumicattempts.golemnet.tile.TileRevisionPiedestal;

@SideOnly(Side.CLIENT)
public class RenderRevisionPiedestalGeo extends GeoBlockRenderer<TileRevisionPiedestal> {

    private static final ResourceLocation TEX_EMISSIVE_ON =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/revision_on.png");
    private static final ResourceLocation TEX_EMISSIVE_OFF =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/revision_off.png");

    private static final float EMISSIVE_Y_OFFSET = 0.00f;
    private static final double ITEM_Y_OFFSET = 5.0D / 16.0D;
    private static final double COUNTER_Y_OFFSET = 25.0D / 16.0D;

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

        float[] prevLight = RenderSafety.captureLightmap();
        try {
            super.render(te, x, y, z, partialTicks, destroyStage, alpha);

            if (RenderSafety.isItemRender()) {
                return;
            }

            renderEmissiveLayer(te, x, y, z, partialTicks);
            renderFloatingItem(te, x, y, z, partialTicks);
            renderCounter(te, x, y, z);
        } finally {
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderEmissiveLayer(TileRevisionPiedestal te,
                                     double x, double y, double z,
                                     float partialTicks) {
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

            Minecraft.getMinecraft().getTextureManager()
                    .bindTexture(te.isActive() ? TEX_EMISSIVE_ON : TEX_EMISSIVE_OFF);

            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderFloatingItem(TileRevisionPiedestal te, double x, double y, double z, float partialTicks) {
        ItemStack stack = te.getPedestalItem();
        if (stack == null || stack.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;

        float ticks = (float) mc.getRenderViewEntity().ticksExisted + partialTicks;

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float) x + 0.5F, (float) (y + ITEM_Y_OFFSET), (float) z + 0.5F);
            GL11.glScaled(1.15D, 1.15D, 1.15D);
            GL11.glRotatef(ticks % 360.0F, 0.0F, 1.0F, 0.0F);

            ItemStack renderStack = stack.copy();
            renderStack.setCount(1);

            EntityItem entityItem = new EntityItem(mc.world, 0.0D, 0.0D, 0.0D, renderStack);
            entityItem.hoverStart = 0.0F;

            RenderManager renderManager = mc.getRenderManager();
            renderManager.renderEntity(entityItem, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, false);
        } finally {
            GL11.glPopMatrix();
            RenderSafety.resetGlState();
        }
    }

    private void renderCounter(TileRevisionPiedestal te, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getRenderManager() == null) return;

        FontRenderer font = mc.fontRenderer;
        RenderManager rm = mc.getRenderManager();
        String text = te.isActive()
                ? te.getCounter() + "x" + te.getMultiplier()
                : Integer.toString(te.getCounter());

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + COUNTER_Y_OFFSET, z + 0.5D);
            GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-0.025F, -0.025F, 0.025F);

            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            int half = font.getStringWidth(text) / 2;
            font.drawStringWithShadow(text, -half, 0, 0xFFD37D);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }
}