package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.AnomalousMonolithModel;
import therealpant.thaumicattempts.world.tile.TileAnomalousMonolith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class RenderAnomalousMonolith extends GeoBlockRenderer<TileAnomalousMonolith> {
    private static final ResourceLocation EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/well_e.png");
    private static final ResourceLocation PORTAL =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/eldritch_portal_round.png");
    private static final ResourceLocation PARADOXUM =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/asspects/paradoxs.png");
    private static final int PORTAL_FRAMES = 16;
    private static final double PORTAL_CENTER_Y = 9.5D / 16.0D;
    private static final float PORTAL_HALF_WIDTH = 0.52F;
    private static final float PORTAL_HALF_HEIGHT = 0.52F;
    private static final double MODEL_MIN_Y = -20.0D / 16.0D;
    private static final double MODEL_MAX_Y = 61.0D / 16.0D;
    private static final double SPAWN_MIN_Y = MODEL_MIN_Y - 2.0D;
    private static final double SPAWN_EDGE_DEPTH = 2.0D;
    private static final double APPROACH_RADIUS = 0.85D;
    private static final float CURVED_APPROACH_END = 0.72F;
    private static final float PARADOXUM_FLIGHT_TICKS = 80.0F;

    private final Map<TileAnomalousMonolith, List<ParadoxumFlight>> paradoxumFlights =
            new WeakHashMap<>();
    private final Map<TileAnomalousMonolith, Double> nextParadoxumTicks =
            new WeakHashMap<>();
    private final Map<TileAnomalousMonolith, Long> lastProcessedTicks =
            new WeakHashMap<>();

    public RenderAnomalousMonolith() {
        super(new AnomalousMonolithModel());
    }

    @Override
    public void render(TileAnomalousMonolith tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null) return;
        float[] previousLight = RenderSafety.captureLightmap();
        try {
            super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
            renderEmissive(tile, x, y, z, partialTicks);
            renderPortal(tile, x, y, z, partialTicks);
            if (MinecraftForgeClient.getRenderPass() == 0) {
                renderParadoxumFlight(tile, x, y, z, partialTicks);
            }
        } finally {
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderEmissive(TileAnomalousMonolith tile, double x, double y, double z,
                                float partialTicks) {
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
    }

    private void renderPortal(TileAnomalousMonolith tile, double x, double y, double z,
                              float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getRenderViewEntity() == null) return;

        double animationTicks = tile.getWorld() == null
                ? minecraft.getRenderViewEntity().ticksExisted + partialTicks
                : tile.getWorld().getTotalWorldTime() + partialTicks;
        int frame = ((int) (animationTicks / 2.0D)) % PORTAL_FRAMES;
        float minU = frame / (float) PORTAL_FRAMES;
        float maxU = (frame + 1) / (float) PORTAL_FRAMES;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + PORTAL_CENTER_Y, z + 0.5D);
            GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(minecraft.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            minecraft.getTextureManager().bindTexture(PORTAL);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(-PORTAL_HALF_WIDTH, -PORTAL_HALF_HEIGHT, 0.0D).tex(minU, 1.0D).endVertex();
            buffer.pos(PORTAL_HALF_WIDTH, -PORTAL_HALF_HEIGHT, 0.0D).tex(maxU, 1.0D).endVertex();
            buffer.pos(PORTAL_HALF_WIDTH, PORTAL_HALF_HEIGHT, 0.0D).tex(maxU, 0.0D).endVertex();
            buffer.pos(-PORTAL_HALF_WIDTH, PORTAL_HALF_HEIGHT, 0.0D).tex(minU, 0.0D).endVertex();
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }
    }

    private void renderParadoxumFlight(TileAnomalousMonolith tile,
                                       double x, double y, double z,
                                       float partialTicks) {
        if (tile.getWorld() == null || !tile.getWorld().isRemote) return;

        long now = tile.getWorld().getTotalWorldTime();
        Long lastProcessed = lastProcessedTicks.get(tile);
        if (lastProcessed == null || lastProcessed != now) {
            lastProcessedTicks.put(tile, now);
            updateParadoxumFlight(tile, now);
        }

        List<ParadoxumFlight> flights = paradoxumFlights.get(tile);
        if (flights == null || flights.isEmpty()) return;

        for (ParadoxumFlight flight : flights) {
            float progress = Math.min(1.0F,
                    (now + partialTicks - flight.startedAt) / PARADOXUM_FLIGHT_TICKS);
            if (progress < 0.0F) continue;

            double localX;
            double localY;
            double localZ;
            if (progress < CURVED_APPROACH_END) {
                float curveT = progress / CURVED_APPROACH_END;
                localX = cubicBezier(
                        flight.startX, flight.control1X, flight.control2X,
                        flight.approachX, curveT);
                localY = cubicBezier(
                        flight.startY, flight.startY, PORTAL_CENTER_Y,
                        PORTAL_CENTER_Y, curveT);
                localZ = cubicBezier(
                        flight.startZ, flight.control1Z, flight.control2Z,
                        flight.approachZ, curveT);
            } else {
                // The final segment lies at portal height and points radially
                // towards the vertical axis, so it cannot pass through either
                // solid half of the monolith.
                float entryT = (progress - CURVED_APPROACH_END)
                        / (1.0F - CURVED_APPROACH_END);
                localX = flight.approachX + (0.5D - flight.approachX) * entryT;
                localY = PORTAL_CENTER_Y;
                localZ = flight.approachZ + (0.5D - flight.approachZ) * entryT;
            }
            float scale = 1.0F - 0.45F * progress;

            renderParadoxumBillboard(
                    x + localX, y + localY, z + localZ, scale);
        }
    }

    private void updateParadoxumFlight(TileAnomalousMonolith tile, long now) {
        List<ParadoxumFlight> flights = paradoxumFlights.computeIfAbsent(
                tile, unused -> new ArrayList<>());
        // Render the exact arrival position for one tick, then remove it.
        flights.removeIf(flight -> now - flight.startedAt > PARADOXUM_FLIGHT_TICKS);

        Double next = nextParadoxumTicks.get(tile);
        if (next != null && now < next) return;

        int cellX;
        int cellZ;
        // A 5x5 column around the full model, minus its central 1x1 column.
        do {
            cellX = tile.getWorld().rand.nextInt(5) - 2;
            cellZ = tile.getWorld().rand.nextInt(5) - 2;
        } while (cellX == 0 && cellZ == 0);

        double startX = 0.5D + cellX;
        // Exclude the central 5x5x5 volume occupied by the model. Spawn only
        // in the two-block lower or upper edge slabs of the 5x5 column.
        double startY = tile.getWorld().rand.nextBoolean()
                ? SPAWN_MIN_Y
                    + tile.getWorld().rand.nextDouble() * SPAWN_EDGE_DEPTH
                : MODEL_MAX_Y
                    + tile.getWorld().rand.nextDouble() * SPAWN_EDGE_DEPTH;
        double startZ = 0.5D + cellZ;
        double radialLength = Math.sqrt(cellX * cellX + cellZ * cellZ);
        double radialX = cellX / radialLength;
        double radialZ = cellZ / radialLength;
        double tangentX = -radialZ;
        double tangentZ = radialX;
        double curveSide = tile.getWorld().rand.nextBoolean() ? 1.0D : -1.0D;
        double bend = (0.55D + radialLength * 0.12D) * curveSide;
        double approachX = 0.5D + radialX * APPROACH_RADIUS;
        double approachZ = 0.5D + radialZ * APPROACH_RADIUS;

        flights.add(new ParadoxumFlight(
                startX, startY, startZ,
                startX + tangentX * bend,
                startZ + tangentZ * bend,
                approachX + radialX * 0.55D,
                approachZ + radialZ * 0.55D,
                approachX, approachZ,
                now));
        double interval = 20.0D / tile.getParticlesPerSecond();
        nextParadoxumTicks.put(tile, (next == null ? now : next) + interval);
    }

    private static double cubicBezier(double start, double control1,
                                      double control2, double end, float t) {
        double inverse = 1.0D - t;
        return inverse * inverse * inverse * start
                + 3.0D * inverse * inverse * t * control1
                + 3.0D * inverse * t * t * control2
                + t * t * t * end;
    }

    private void renderParadoxumBillboard(double x, double y, double z, float scale) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getRenderViewEntity() == null) return;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-minecraft.getRenderManager().playerViewY,
                    0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(minecraft.getRenderManager().playerViewX,
                    1.0F, 0.0F, 0.0F);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            minecraft.getTextureManager().bindTexture(PARADOXUM);

            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            drawParadoxumQuad(0.17F * scale, 0.18F, 0.28F, 1.0F, 0.35F);
            drawParadoxumQuad(0.095F * scale, 0.58F, 0.68F, 1.0F, 1.0F);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }
    }

    private void drawParadoxumQuad(float radius,
                                   float red, float green, float blue, float alpha) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(-radius, -radius, 0.0D).tex(0.0D, 1.0D)
                .color(red, green, blue, alpha).endVertex();
        buffer.pos(radius, -radius, 0.0D).tex(1.0D, 1.0D)
                .color(red, green, blue, alpha).endVertex();
        buffer.pos(radius, radius, 0.0D).tex(1.0D, 0.0D)
                .color(red, green, blue, alpha).endVertex();
        buffer.pos(-radius, radius, 0.0D).tex(0.0D, 0.0D)
                .color(red, green, blue, alpha).endVertex();
        tessellator.draw();
    }

    private static final class ParadoxumFlight {
        private final double startX;
        private final double startY;
        private final double startZ;
        private final double control1X;
        private final double control1Z;
        private final double control2X;
        private final double control2Z;
        private final double approachX;
        private final double approachZ;
        private final long startedAt;

        private ParadoxumFlight(double startX, double startY, double startZ,
                                double control1X, double control1Z,
                                double control2X, double control2Z,
                                double approachX, double approachZ,
                                long startedAt) {
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.control1X = control1X;
            this.control1Z = control1Z;
            this.control2X = control2X;
            this.control2Z = control2Z;
            this.approachX = approachX;
            this.approachZ = approachZ;
            this.startedAt = startedAt;
        }
    }
}
