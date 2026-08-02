package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.block.BlockEldritchConstarction;
import therealpant.thaumicattempts.world.tile.TileEldritchConstruction;

public class RenderEldritchConstruction
        extends TileEntitySpecialRenderer<TileEldritchConstruction> {
    private static final ResourceLocation[] EMISSIVE_TEXTURES = {
            null,
            texture("rift_stone_block_e1.png"),
            texture("rift_stone_block_e2.png"),
            texture("rift_stone_block_e3.png")
    };
    private static final double MIN = -0.001D;
    private static final double MAX = 1.001D;

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/" + name);
    }

    @Override
    public void render(TileEldritchConstruction tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null || tile.getWorld() == null) return;
        if (!(tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof BlockEldritchConstarction)) return;

        BlockEldritchConstarction.Variant variant = tile.getWorld()
                .getBlockState(tile.getPos()).getValue(BlockEldritchConstarction.VARIANT);
        ResourceLocation texture = EMISSIVE_TEXTURES[variant.getMeta()];
        if (texture == null) return;

        float[] previousLight = RenderSafety.captureLightmap();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            drawCube(MIN, MAX);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }

    static void drawCube(double min, double max) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        // North and south.
        vertex(buffer, min, min, min, 1, 1); vertex(buffer, min, max, min, 1, 0);
        vertex(buffer, max, max, min, 0, 0); vertex(buffer, max, min, min, 0, 1);
        vertex(buffer, max, min, max, 1, 1); vertex(buffer, max, max, max, 1, 0);
        vertex(buffer, min, max, max, 0, 0); vertex(buffer, min, min, max, 0, 1);

        // West and east.
        vertex(buffer, min, min, max, 1, 1); vertex(buffer, min, max, max, 1, 0);
        vertex(buffer, min, max, min, 0, 0); vertex(buffer, min, min, min, 0, 1);
        vertex(buffer, max, min, min, 1, 1); vertex(buffer, max, max, min, 1, 0);
        vertex(buffer, max, max, max, 0, 0); vertex(buffer, max, min, max, 0, 1);

        // Up and down.
        vertex(buffer, min, max, min, 0, 0); vertex(buffer, min, max, max, 0, 1);
        vertex(buffer, max, max, max, 1, 1); vertex(buffer, max, max, min, 1, 0);
        vertex(buffer, min, min, max, 0, 0); vertex(buffer, min, min, min, 0, 1);
        vertex(buffer, max, min, min, 1, 1); vertex(buffer, max, min, max, 1, 0);

        tessellator.draw();
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z,
                               double u, double v) {
        buffer.pos(x, y, z).tex(u, v).endVertex();
    }
}
