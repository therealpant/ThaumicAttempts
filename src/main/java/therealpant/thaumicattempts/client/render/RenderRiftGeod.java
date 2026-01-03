package therealpant.thaumicattempts.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.RiftGeodModel;
import therealpant.thaumicattempts.world.block.BlockRiftGeod;
import therealpant.thaumicattempts.world.tile.TileRiftGeod;

@SideOnly(Side.CLIENT)
public class RenderRiftGeod extends GeoBlockRenderer<TileRiftGeod> {

    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/rift_geod_e.png");
    private static final float EMISSIVE_Y_OFFSET = 0.01f;

    public RenderRiftGeod() {
        super(new RiftGeodModel());
    }

    private EnumFacing getFacing(TileRiftGeod te) {
        if (te == null || te.getWorld() == null) return EnumFacing.UP;
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (state.getBlock() instanceof BlockRiftGeod) {
            return state.getValue(BlockRiftGeod.FACING);
        }
        return EnumFacing.UP;
    }

    private void applyFacingTransform(EnumFacing facing) {
        switch (facing) {
            case UP:
                break;
            case DOWN:
                GlStateManager.rotate(180f, 1f, 0f, 0f);
                break;
            case NORTH:
                GlStateManager.rotate(-90f, 1f, 0f, 0f);
                break;
            case SOUTH:
                GlStateManager.rotate(90f, 1f, 0f, 0f);
                break;
            case EAST:
                GlStateManager.rotate(-90f, 0f, 0f, 1f);
                break;
            case WEST:
                GlStateManager.rotate(90f, 0f, 0f, 1f);
                break;
        }
    }

    @Override
    public void rotateBlock(EnumFacing facing) {
        GlStateManager.translate(0.0F, 0.5F, 0.0F);
        applyFacingTransform(facing);
        GlStateManager.translate(0.0F, -0.5F, 0.0F);
    }

    private void renderEmissiveLayer(TileRiftGeod te,
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

            EnumFacing facing = getFacing(te);
            rotateBlock(facing);

            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            GlStateManager.color(1F, 1F, 1F, 1F);

            Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_EMISSIVE);

            this.render(model, te, partialTicks, 1f, 1f, 1f, 1f);

            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void render(TileRiftGeod te,
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
            if (pushedAttrib) {
                GL11.glPopAttrib();
            }
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
        }
    }
}