package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.client.model.AnomalyWellModel;
import therealpant.thaumicattempts.client.particle.ParticleLiquidDeathDrip;
import therealpant.thaumicattempts.world.tile.TileAnomalyWell;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class RenderAnomalyWell extends GeoBlockRenderer<TileAnomalyWell> {
    private static final ResourceLocation EMISSIVE =
            new ResourceLocation("thaumicattempts", "textures/blocks/well_e.png");
    private static final ResourceLocation FALLBACK_FLUID_TEXTURE =
            new ResourceLocation("thaumcraft", "blocks/animatedglow");
    private static final double LIQUID_MIN = -19.5D / 16.0D;
    private static final double LIQUID_MAX = 19.5D / 16.0D;
    private static final double BONE_4_HALF_WIDTH = 2.5D / 16.0D;
    private static final double BONE_4_DRIP_MIN = 47.0D / 16.0D;
    private static final double BONE_4_DRIP_RANGE = 9.0D / 16.0D;
    private static final double FACE_OFFSET = 0.008D;
    private static final Map<World, Map<Long, Long>> NEXT_DRIP_TICKS = new WeakHashMap<>();

    public RenderAnomalyWell() {
        super(new AnomalyWellModel());
    }

    @Override
    public void render(TileAnomalyWell tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
        renderEmissive(tile, x, y, z, partialTicks);
        renderLiquidDeath(tile, x, y, z);
        if (MinecraftForgeClient.getRenderPass() == 0) {
            spawnLiquidDeathDrip(tile);
        }
    }

    private void renderEmissive(TileAnomalyWell tile, double x, double y, double z,
                                float partialTicks) {
        if (tile == null) return;
        GeoModel model = getGeoModelProvider().getModel(
                getGeoModelProvider().getModelLocation(tile));
        float[] previousLight = RenderSafety.captureLightmap();
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
            bindTexture(EMISSIVE);
            this.render(model, tile, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderLiquidDeath(TileAnomalyWell tile, double x, double y, double z) {
        if (tile == null || tile.getCharges() <= 0) return;

        double fluidY = y + tile.getFluidLevelPixels() / 16.0D + 0.002D;
        double minX = x + 0.5D + LIQUID_MIN;
        double maxX = x + 0.5D + LIQUID_MAX;
        double minZ = z + 0.5D + LIQUID_MIN;
        double maxZ = z + 0.5D + LIQUID_MAX;
        TextureAtlasSprite sprite = getLiquidDeathSprite();

        GlStateManager.pushMatrix();
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            GlStateManager.color(0.28F, 0.0F, 0.55F, 0.88F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            addSurfaceVertex(buffer, minX, fluidY, minZ, sprite.getMinU(), sprite.getMinV());
            addSurfaceVertex(buffer, minX, fluidY, maxZ, sprite.getMinU(), sprite.getMaxV());
            addSurfaceVertex(buffer, maxX, fluidY, maxZ, sprite.getMaxU(), sprite.getMaxV());
            addSurfaceVertex(buffer, maxX, fluidY, minZ, sprite.getMaxU(), sprite.getMinV());
            addSurfaceVertex(buffer, maxX, fluidY, minZ, sprite.getMaxU(), sprite.getMinV());
            addSurfaceVertex(buffer, maxX, fluidY, maxZ, sprite.getMaxU(), sprite.getMaxV());
            addSurfaceVertex(buffer, minX, fluidY, maxZ, sprite.getMinU(), sprite.getMaxV());
            addSurfaceVertex(buffer, minX, fluidY, minZ, sprite.getMinU(), sprite.getMinV());
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private void spawnLiquidDeathDrip(TileAnomalyWell tile) {
        if (tile == null || tile.getWorld() == null || !tile.getWorld().isRemote
                || tile.getCharges() <= 0) {
            return;
        }

        long tick = tile.getWorld().getTotalWorldTime();
        long positionKey = tile.getPos().toLong();
        Map<Long, Long> worldSchedule =
                NEXT_DRIP_TICKS.computeIfAbsent(tile.getWorld(), world -> new HashMap<>());
        Long next = worldSchedule.get(positionKey);
        if (next != null && tick < next) {
            return;
        }
        worldSchedule.put(positionKey, tick + 30L + tile.getWorld().rand.nextInt(21));

        double centerX = tile.getPos().getX() + 0.5D;
        double centerZ = tile.getPos().getZ() + 0.5D;
        double x = centerX;
        double z = centerZ;
        double alongFace = (tile.getWorld().rand.nextDouble() - 0.5D) * 0.20D;
        switch (tile.getWorld().rand.nextInt(4)) {
            case 0:
                x += BONE_4_HALF_WIDTH + FACE_OFFSET;
                z += alongFace;
                break;
            case 1:
                x -= BONE_4_HALF_WIDTH + FACE_OFFSET;
                z += alongFace;
                break;
            case 2:
                z += BONE_4_HALF_WIDTH + FACE_OFFSET;
                x += alongFace;
                break;
            default:
                z -= BONE_4_HALF_WIDTH + FACE_OFFSET;
                x += alongFace;
                break;
        }
        double y = tile.getPos().getY() + 0.01D
                + BONE_4_DRIP_MIN + tile.getWorld().rand.nextDouble() * BONE_4_DRIP_RANGE;
        double targetY = tile.getPos().getY()
                + tile.getFluidLevelPixels() / 16.0D + 0.015D;
        Minecraft.getMinecraft().effectRenderer.addEffect(
                new ParticleLiquidDeathDrip(
                        tile.getWorld(), x, y, z, targetY, getLiquidDeathSprite()));
    }

    private TextureAtlasSprite getLiquidDeathSprite() {
        Fluid fluid = FluidRegistry.getFluid("liquid_death");
        ResourceLocation still = fluid == null ? FALLBACK_FLUID_TEXTURE : fluid.getStill();
        TextureAtlasSprite sprite =
                Minecraft.getMinecraft().getTextureMapBlocks().getTextureExtry(still.toString());
        return sprite == null
                ? Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite()
                : sprite;
    }

    private void addSurfaceVertex(BufferBuilder buffer, double x, double y, double z,
                                  double u, double v) {
        buffer.pos(x, y, z)
                .tex(u, v)
                .color(0.28F, 0.0F, 0.55F, 0.88F)
                .endVertex();
    }
}
