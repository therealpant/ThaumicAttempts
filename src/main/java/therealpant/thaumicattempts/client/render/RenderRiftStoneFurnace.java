package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.particles.FXSlimyBubble;
import therealpant.thaumicattempts.client.model.RiftStoneFurnaceModel;
import therealpant.thaumicattempts.world.tile.TileRiftStoneFurnace;

@SideOnly(Side.CLIENT)
public class RenderRiftStoneFurnace extends GeoBlockRenderer<TileRiftStoneFurnace> {
    private static final ResourceLocation FALLBACK_FLUID_TEXTURE =
            new ResourceLocation("thaumcraft", "blocks/animatedglow");
    private static final double LIQUID_MIN = -2.0D / 16.0D;
    private static final double LIQUID_MAX = 18.0D / 16.0D;

    public RenderRiftStoneFurnace() {
        super(new RiftStoneFurnaceModel());
    }

    @Override
    public void render(TileRiftStoneFurnace te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);
        renderLiquidDeath(te, x, y, z);
    }

    private void renderLiquidDeath(TileRiftStoneFurnace te, double x, double y, double z) {
        if (te == null || te.getEssentiaAmountTotal() <= 0) {
            return;
        }

        float pixels = te.getFluidLevelPixels();
        double fluidY = y + 0.5D + pixels / 16.0D;
        TextureAtlasSprite sprite = getLiquidDeathSprite();

        GlStateManager.pushMatrix();
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            GlStateManager.color(0.28F, 0.0F, 0.55F, 0.88F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            addSurfaceVertex(buffer, x + LIQUID_MIN, fluidY, z + LIQUID_MIN, sprite.getMinU(), sprite.getMinV());
            addSurfaceVertex(buffer, x + LIQUID_MIN, fluidY, z + LIQUID_MAX, sprite.getMinU(), sprite.getMaxV());
            addSurfaceVertex(buffer, x + LIQUID_MAX, fluidY, z + LIQUID_MAX, sprite.getMaxU(), sprite.getMaxV());
            addSurfaceVertex(buffer, x + LIQUID_MAX, fluidY, z + LIQUID_MIN, sprite.getMaxU(), sprite.getMinV());
            addSurfaceVertex(buffer, x + LIQUID_MAX, fluidY, z + LIQUID_MIN, sprite.getMaxU(), sprite.getMinV());
            addSurfaceVertex(buffer, x + LIQUID_MAX, fluidY, z + LIQUID_MAX, sprite.getMaxU(), sprite.getMaxV());
            addSurfaceVertex(buffer, x + LIQUID_MIN, fluidY, z + LIQUID_MAX, sprite.getMinU(), sprite.getMaxV());
            addSurfaceVertex(buffer, x + LIQUID_MIN, fluidY, z + LIQUID_MIN, sprite.getMinU(), sprite.getMinV());
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }

        spawnBubble(te, fluidY);
    }

    private TextureAtlasSprite getLiquidDeathSprite() {
        Fluid fluid = FluidRegistry.getFluid("liquid_death");
        ResourceLocation still = fluid == null ? FALLBACK_FLUID_TEXTURE : fluid.getStill();
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getTextureExtry(still.toString());
        if (sprite == null) {
            sprite = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
        }
        return sprite;
    }

    private void addSurfaceVertex(BufferBuilder buffer, double x, double y, double z, double u, double v) {
        buffer.pos(x, y, z)
                .tex(u, v)
                .color(0.28F, 0.0F, 0.55F, 0.88F)
                .endVertex();
    }

    private void spawnBubble(TileRiftStoneFurnace te, double fluidY) {
        if (te.getWorld() == null || te.getWorld().rand.nextInt(6) != 0) {
            return;
        }

        double worldY = te.getPos().getY() + 0.5D + te.getFluidLevelPixels() / 16.0D;
        double bx = te.getPos().getX() + 0.2D + te.getWorld().rand.nextDouble() * 0.6D;
        double bz = te.getPos().getZ() + 0.2D + te.getWorld().rand.nextDouble() * 0.6D;
        Particle bubble = new FXSlimyBubble(te.getWorld(), bx, worldY + 0.01D, bz, 0.045F);
        bubble.setAlphaF(0.75F);
        bubble.setRBGColorF(0.42F + te.getWorld().rand.nextFloat() * 0.18F, 0.0F, 0.85F);
        ParticleEngine.addEffect(te.getWorld(), bubble);
    }
}
