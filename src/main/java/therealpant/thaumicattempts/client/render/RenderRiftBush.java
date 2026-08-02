package therealpant.thaumicattempts.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.block.BlockRiftBush;
import therealpant.thaumicattempts.world.tile.TileRiftBush;

@SideOnly(Side.CLIENT)
public class RenderRiftBush extends TileEntitySpecialRenderer<TileRiftBush> {

    private static final ResourceLocation BOTTOM_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/rift_bush_bottom_e.png");
    private static final ResourceLocation TOP_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/rift_bush_top_e.png");

    private static final double CROSS_MIN = 0.05D;
    private static final double CROSS_MAX = 0.95D;

    @Override
    public void render(TileRiftBush tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null || tile.getWorld() == null) return;

        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockRiftBush)) return;
        if (state.getValue(BlockRiftBush.HARVEST_STATE) != BlockRiftBush.HarvestState.READY) return;

        ResourceLocation texture = state.getValue(BlockRiftBush.HALF) == BlockRiftBush.BlockHalf.UPPER
                ? TOP_EMISSIVE
                : BOTTOM_EMISSIVE;

        float[] lightmap = RenderSafety.captureLightmap();
        RenderSafety.GlStateSnapshot glState = RenderSafety.captureGlState();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
            GlStateManager.scale(1.002D, 1.002D, 1.002D);
            GlStateManager.translate(-0.5D, -0.5D, -0.5D);

            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            bindTexture(texture);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            addCross(buffer);
            tessellator.draw();
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(lightmap);
            RenderSafety.restoreGlState(glState);
        }
    }

    private static void addCross(BufferBuilder buffer) {
        addQuad(buffer,
                CROSS_MIN, 0.0D, CROSS_MIN, 0.0D, 1.0D,
                CROSS_MIN, 1.0D, CROSS_MIN, 0.0D, 0.0D,
                CROSS_MAX, 1.0D, CROSS_MAX, 1.0D, 0.0D,
                CROSS_MAX, 0.0D, CROSS_MAX, 1.0D, 1.0D);
        addQuad(buffer,
                CROSS_MIN, 0.0D, CROSS_MAX, 0.0D, 1.0D,
                CROSS_MIN, 1.0D, CROSS_MAX, 0.0D, 0.0D,
                CROSS_MAX, 1.0D, CROSS_MIN, 1.0D, 0.0D,
                CROSS_MAX, 0.0D, CROSS_MIN, 1.0D, 1.0D);
    }

    private static void addQuad(BufferBuilder buffer,
                                double x1, double y1, double z1, double u1, double v1,
                                double x2, double y2, double z2, double u2, double v2,
                                double x3, double y3, double z3, double u3, double v3,
                                double x4, double y4, double z4, double u4, double v4) {
        buffer.pos(x1, y1, z1).tex(u1, v1).endVertex();
        buffer.pos(x2, y2, z2).tex(u2, v2).endVertex();
        buffer.pos(x3, y3, z3).tex(u3, v3).endVertex();
        buffer.pos(x4, y4, z4).tex(u4, v4).endVertex();
    }
}
