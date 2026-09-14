package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.EnumParticleTypes;
import thaumcraft.api.items.ItemsTC;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.lwjgl.opengl.GL11;
import thaumcraft.client.fx.ParticleEngine;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.RiftWellModel;
import therealpant.thaumicattempts.integration.rift.RiftBarrierGeometry;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.world.tile.TileRiftWell;
import therealpant.thaumicattempts.world.block.RiftWellLiquidSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

public class RenderRiftWell extends GeoBlockRenderer<TileRiftWell> {
    private static final ResourceLocation EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftwell_e.png");
    private static final ResourceLocation PARADOXUM =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/asspects/paradoxs.png");
    private static final ResourceLocation FALLBACK_FLUID_TEXTURE =
            new ResourceLocation("thaumcraft", "blocks/animatedglow");
    private static final int[] STONE_BOB_PHASES = {120, 30, -1, -1, 60, -1, 90, -1};
    private static final double[][] STONE_ANCHORS = {
            {0.6336D, 28.3824D, 55.0778D},
            {-55.0778D, 28.3824D, 0.6336D},
            {-44.13885D, 35.29199D, 43.5D},
            {-43.5D, 35.29199D, -44.13885D},
            {-0.6336D, 28.3824D, -55.0778D},
            {44.13885D, 35.29199D, -43.5D},
            {55.0778D, 28.3824D, -0.6336D},
            {43.5D, 35.29199D, 44.13885D}
    };
    private static final float FLIGHT_TICKS = 60.0F;
    public static final int BARRIER_SIDES = RiftBarrierGeometry.SIDES;
    public static final double BARRIER_RADIUS = RiftBarrierGeometry.RADIUS;
    public static final double BARRIER_APOTHEM = RiftBarrierGeometry.APOTHEM;
    public static final double BARRIER_HEIGHT = RiftBarrierGeometry.HEIGHT;

    private final Map<TileRiftWell, List<ParadoxumFlight>> paradoxumFlights =
            new WeakHashMap<>();
    private final Map<TileRiftWell, long[]> nextFlightTicks = new WeakHashMap<>();
    private final Map<TileRiftWell, Long> lastProcessedTicks = new WeakHashMap<>();
    private final Map<TileRiftWell, Long> lastRitualTicks = new WeakHashMap<>();
    private final Map<TileRiftWell, WaveClock> barrierClocks = new WeakHashMap<>();
    private final Map<TileRiftWell, List<BarrierWave>> barrierWaves = new WeakHashMap<>();

    public RenderRiftWell() { super(new RiftWellModel()); }

    @Override
    public void render(TileRiftWell tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null) return;
        float[] previousLight = RenderSafety.captureLightmap();
        try {
            super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
            renderEmissive(tile, x, y, z, partialTicks);
        } finally {
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
        if (MinecraftForgeClient.getRenderPass() == 0) {
            renderProtectiveBarrier(tile, x, y, z, partialTicks);
            renderBarrierWaves(tile, x, y, z, partialTicks);
            renderVoidSurface(tile, x, y, z, partialTicks);
            renderOverloadSphere(tile, x, y, z, partialTicks);
            renderInsertedStones(tile, x, y, z, partialTicks);
            renderParadoxumFlights(tile, x, y, z, partialTicks);
            renderCleansingPearl(tile, x, y, z, partialTicks);
        }
    }

    private void renderEmissive(TileRiftWell tile, double x, double y, double z,
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

    private void renderInsertedStones(TileRiftWell tile, double x, double y, double z,
                                      float partialTicks) {
        int count = Math.min(TileRiftWell.MAX_INTERCEPTOR_STONES,
                tile.getInstalledStoneCount());
        if (count <= 0 || ModBlocksItems.INTERCEPTOR_STONE == null) return;
        double ticks = tile.getWorld() == null ? partialTicks
                : tile.getWorld().getTotalWorldTime() + partialTicks;
        ItemStack stone = new ItemStack(ModBlocksItems.INTERCEPTOR_STONE);

        for (int slot = 0; slot < count; slot++) {
            Vec3d anchor = getStoneAnchor(slot, ticks);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + anchor.x, y + anchor.y, z + anchor.z);
                GlStateManager.rotate((float) ((ticks * 2.0D + slot * 45.0D) % 360.0D),
                        0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.48D, 0.48D, 0.48D);
                RenderHelper.enableStandardItemLighting();
                Minecraft.getMinecraft().getRenderItem().renderItem(
                        stone, ItemCameraTransforms.TransformType.FIXED);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
                RenderSafety.resetGlState();
            }
        }
    }

    private Vec3d getStoneAnchor(int slot, double ticks) {
        int index = Math.max(0, Math.min(STONE_ANCHORS.length - 1, slot));
        double bob = STONE_BOB_PHASES[index] < 0 ? 0.0D
                : Math.sin(Math.toRadians(ticks * 6.0D + STONE_BOB_PHASES[index]));
        return new Vec3d(
                0.5D + STONE_ANCHORS[index][0] / 16.0D,
                (STONE_ANCHORS[index][1] + bob) / 16.0D + 0.5D,
                0.5D + STONE_ANCHORS[index][2] / 16.0D);
    }

    private void renderVoidSurface(TileRiftWell tile, double x, double y, double z,
                                   float partialTicks) {
        TextureAtlasSprite sprite = getLiquidDeathSprite();
        float alpha = 0.84F;
        float fill = tile.getLiquidDarkness();
        double ticks = tile.getWorld() == null ? partialTicks
                : tile.getWorld().getTotalWorldTime() + partialTicks;

        GlStateManager.pushMatrix();
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            for (AxisAlignedBB region : RiftWellLiquidSurface.REGIONS) {
                for (int gridX = (int) Math.floor(region.minX); gridX < region.maxX; gridX++) {
                    for (int gridZ = (int) Math.floor(region.minZ); gridZ < region.maxZ; gridZ++) {
                        double minX = Math.max(region.minX, gridX);
                        double maxX = Math.min(region.maxX, gridX + 1.0D);
                        double minZ = Math.max(region.minZ, gridZ);
                        double maxZ = Math.min(region.maxZ, gridZ + 1.0D);
                        // Clip both geometry and UVs: one texture repeat per block,
                        // including the narrow strips along the inside of the rim.
                        addSurfaceQuad(buffer, x + minX, x + maxX,
                                y + TileRiftWell.LIQUID_SURFACE_Y, z + minZ, z + maxZ,
                                sprite.getInterpolatedU((minX - gridX) * 16.0D),
                                sprite.getInterpolatedU((maxX - gridX) * 16.0D),
                                sprite.getInterpolatedV((minZ - gridZ) * 16.0D),
                                sprite.getInterpolatedV((maxZ - gridZ) * 16.0D),
                                minX, minZ, fill, ticks, alpha);
                    }
                }
            }
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private void addSurfaceQuad(BufferBuilder buffer, double minX, double maxX, double y,
                                double minZ, double maxZ, double minU, double maxU,
                                double minV, double maxV,
                                double localX, double localZ, float fill,
                                double ticks, float alpha) {
        addSurfaceVertex(buffer, minX, y, minZ, minU, minV,
                localX, localZ, fill, ticks, alpha);
        addSurfaceVertex(buffer, minX, y, maxZ, minU, maxV,
                localX, localZ + maxZ - minZ, fill, ticks, alpha);
        addSurfaceVertex(buffer, maxX, y, maxZ, maxU, maxV,
                localX + maxX - minX, localZ + maxZ - minZ, fill, ticks, alpha);
        addSurfaceVertex(buffer, maxX, y, minZ, maxU, minV,
                localX + maxX - minX, localZ, fill, ticks, alpha);
    }

    private void addSurfaceVertex(BufferBuilder buffer, double x, double y, double z,
                                  double u, double v, double localX, double localZ,
                                  float fill, double ticks, float alpha) {
        double wave = 0.5D
                + Math.sin(localX * 1.37D + ticks * 0.035D) * 0.22D
                + Math.sin(localZ * 1.71D - ticks * 0.028D) * 0.18D
                + Math.sin((localX + localZ) * 0.83D + ticks * 0.017D) * 0.10D;
        double variation = fill * 0.10D;
        float localFill = clamp01((float) (fill + (wave - 0.5D) * variation));
        float red = 0.12F * (1-localFill);
        float green = 0.25F * (1-localFill);
        float blue = 1-localFill;
        float shimmer = 0.88F + 0.12F * (float) wave;
        buffer.pos(x, y, z).tex(u, v)
                .color(red * shimmer, green * shimmer, blue * shimmer, alpha).endVertex();
    }

    private float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private void renderProtectiveBarrier(TileRiftWell tile, double x, double y, double z,
                                         float partialTicks) {
        if (tile.getActiveDrainCount() <= 0 || tile.getRiftSize() <= 0) return;
        double time = tile.getWorld() == null ? partialTicks
                : tile.getWorld().getTotalWorldTime() + partialTicks;
        float stress = barrierStress(tile);
        GlStateManager.pushMatrix();
        try {
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableAlpha();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(false);
            drawClimbingRunes(tile,x,y,z,time,stress);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private float barrierStress(TileRiftWell tile) {
        return clamp01((float)Math.max(tile.getTension()/100.0D,tile.getUnquenchedRatio()));
    }

    /** Stable face-local placement guarantees upward, fading runes on all twelve walls. */
    private void drawClimbingRunes(TileRiftWell tile,double x,double y,double z,double time,float stress) {
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(ParticleEngine.particleTexture);
        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder buffer=tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
        double phaseInstability=clamp01((float)Math.max(tile.getTension()/100.0D,
                tile.getReactionExcitation()*.55D));
        for (int face=0;face<BARRIER_SIDES;face++) {
            Vec3d normal=RiftBarrierGeometry.normal(face);
            Vec3d tangent=new Vec3d(-normal.z,0,normal.x);
            for (int column=0;column<5;column++) for (int row=0;row<7;row++) {
                double cycle=time/(230.0D+column*23)+row/7.0D+face*.137D+column*.17D;
                double phase=cycle-Math.floor(cycle);
                double jitter=phaseInstability*.07D*Math.sin(time*.19D+row*2.17D+face*1.31D+column);
                double across=(column-2)*.54D+.05D*Math.sin(time*.014D+row+face)+jitter;
                Vec3d point=new Vec3d(.5D+normal.x*(BARRIER_APOTHEM+.008D)+tangent.x*across,
                        .3D+phase*(BARRIER_HEIGHT-.6D),
                        .5D+normal.z*(BARRIER_APOTHEM+.008D)+tangent.z*across);
                double flicker=1.0D-phaseInstability*.22D
                        +phaseInstability*.22D*(.5D+.5D*Math.sin(time*.31D+row+face*.7D));
                float fade=(float)(Math.pow(Math.sin(Math.PI*phase),2)*(.40F+.25F*stress)*flicker);
                int glyph=Math.floorMod((int)(tile.getPos().toLong()+face*7+column*11+row*3+(long)Math.floor(cycle)),16);
                addRuneGlyph(buffer,x,y,z,point,tangent,new Vec3d(0,1,0),glyph,.13D,.38F,.57F,fade);
            }
        }
        tessellator.draw();
    }

    private void renderBarrierWaves(TileRiftWell tile,double x,double y,double z,float partialTicks) {
        if (tile.getWorld()==null || tile.getRiftSize()<=0) return;
        long tick=tile.getWorld().getTotalWorldTime();
        updateBarrierWaves(tile,tick);
        List<BarrierWave> waves=barrierWaves.get(tile);
        if (waves==null || waves.isEmpty()) return;
        double now=tick+partialTicks;
        float stress=barrierStress(tile);
        GlStateManager.pushMatrix();
        try {
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(false);
            for (BarrierWave wave:waves) {
                float progress=(float)((now-wave.startedAt)/wave.duration);
                if (progress<0 || progress>1) continue;
                if (progress<.36F) {
                    float aurora=clamp01(progress/.36F);
                    drawAuroraSweep(x,y,z,wave,aurora,.72F+.20F*stress);
                }
                if (progress>=.36F && progress<.70F) {
                    float travel=clamp01((progress-.36F)/.34F)*(float)RiftBarrierGeometry.FLIGHT_END;
                    drawEmissionArc(x,y,z,wave,travel,.58F+.22F*stress);
                }
                if (tile.getActiveDrainCount()>0) {
                    for (RiftBarrierGeometry.ArcContact contact:wave.contacts) {
                        double at=wave.duration*(.36D+.34D*contact.motion/RiftBarrierGeometry.FLIGHT_END);
                        float impact=(float)((now-wave.startedAt-at)/22.0D);
                        if (impact<0 || impact>1) continue;
                        Vec3d normal=RiftBarrierGeometry.normal(contact.face);
                        drawImpactFlash(x,y,z,contact.point,normal,.28F+.65F*impact,
                                (1-impact)*(.42F+.20F*stress));
                        drawImpactRunes(x,y,z,contact.point,normal,wave.startedAt+contact.face,impact);
                    }
                }
            }
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private void updateBarrierWaves(TileRiftWell tile,long tick) {
        List<BarrierWave> waves=barrierWaves.computeIfAbsent(tile,ignored->new ArrayList<>());
        waves.removeIf(wave->tick-wave.startedAt>wave.duration+1);
        WaveClock clock=barrierClocks.computeIfAbsent(tile,ignored->new WaveClock());
        int serial=tile.getEmissionSerial();
        if (!clock.initialized) {
            clock.lastSerial=serial;
            clock.initialized=true;
            return;
        }
        int delta=serial-clock.lastSerial;
        if (delta<0) {
            clock.lastSerial=serial;
            return;
        }
        int count=Math.min(16,delta);
        int firstSerial=serial-count+1;
        for (int i=0;i<count;i++) {
            int eventSerial=firstSerial+i;
            int arcs=eventSerial==serial ? Math.max(1,tile.getLastArcCount()) : 1;
            for (int arc=0;arc<arcs;arc++) {
                Random random=new Random(tile.getPos().toLong()^eventSerial*0x9E3779B97F4A7C15L
                        ^(arc+1L)*0xD1B54A32D192ED03L);
                List<Vec3d> aurora=tile.getVisualRiftSection(random,5+random.nextInt(5));
                // Do not consume the event until its actual rift geometry has arrived.
                if (aurora.size()<2) return;
                Vec3d start=aurora.get(aurora.size()-1);
                Vec3d target=tile.getEmissionVisualTarget(arc,random);
                if (target==null) return;
                waves.add(new BarrierWave(aurora,start,target,tick+i*2L+arc*2L,
                        68+random.nextInt(5),(float)(tile.getLastPulseStrength()/arcs)));
            }
            clock.lastSerial=eventSerial;
        }
        clock.lastSerial=serial;
    }

    private void drawAuroraSweep(double x,double y,double z,BarrierWave wave,
                                 float progress,float alpha) {
        if (wave.aurora.isEmpty()) return;
        double yaw=Math.toRadians(Minecraft.getMinecraft().getRenderManager().playerViewY);
        Vec3d right=new Vec3d(Math.cos(yaw),0,Math.sin(yaw));
        // Lift the curtain off the rift surface towards the camera, retaining depth testing.
        Vec3d camera=Minecraft.getMinecraft().getRenderViewEntity()==null ? Vec3d.ZERO
                : new Vec3d(-Math.sin(yaw),0,Math.cos(yaw)).scale(.18D);
        double pulseScale=Math.min(1.45D,.75D+wave.strength/18.0D);
        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder buffer=tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        int segments=(wave.aurora.size()-1)*6;
        for (int i=0;i<=segments;i++) {
            double along=i/(double)segments;
            double phase=progress*1.45D-along;
            if (phase<=0 || phase>=.50D) continue;
            float local=(float)Math.sin(Math.PI*phase/.50D);
            double node=along*(wave.aurora.size()-1);
            int index=Math.min(wave.aurora.size()-2,(int)node);
            Vec3d a=wave.aurora.get(index),b=wave.aurora.get(index+1);
            Vec3d point=a.add(b.subtract(a).scale(node-index)).add(camera);
            double sway=Math.sin(i*.64D-progress*9.0D)*.10D;
            double height=(1.35D+.65D*Math.sin(i*1.71D+progress*11.0D))*pulseScale;
            addAuroraQuad(buffer,x+point.x+right.x*sway,y+point.y-.32D,z+point.z+right.z*sway,
                    right,.15D,height,.18F,.28F,1.0F,alpha*local*.65F);
            addAuroraQuad(buffer,x+point.x+right.x*sway,y+point.y-.24D,z+point.z+right.z*sway,
                    right,.06D,height*.80D,.58F,.68F,1.0F,alpha*local);
        }
        tessellator.draw();
    }

    private void addAuroraQuad(BufferBuilder buffer,double x,double y,double z,Vec3d right,
                               double halfWidth,double height,float red,float green,float blue,float alpha) {
        // Two feathered halves, with a luminous centre and transparent sides and tips.
        for (int row=0;row<6;row++) {
            double v0=row/6.0D,v1=(row+1)/6.0D;
            for (int side=0;side<2;side++) {
                double u0=side-1,u1=side;
                addAuroraVertex(buffer,x,y,z,right,u0,v0,halfWidth,height,red,green,blue,alpha);
                addAuroraVertex(buffer,x,y,z,right,u1,v0,halfWidth,height,red,green,blue,alpha);
                addAuroraVertex(buffer,x,y,z,right,u1,v1,halfWidth,height,red,green,blue,alpha);
                addAuroraVertex(buffer,x,y,z,right,u0,v1,halfWidth,height,red,green,blue,alpha);
            }
        }
    }

    private void addAuroraVertex(BufferBuilder buffer,double x,double y,double z,Vec3d right,
                                 double u,double v,double width,double height,
                                 float red,float green,float blue,float alpha) {
        float fade=(float)((1-Math.abs(u))*Math.sin(v*Math.PI));
        double bend=Math.sin(v*Math.PI)*width*.7D;
        buffer.pos(x+right.x*(u*width+bend),y+v*height,z+right.z*(u*width+bend))
                .color(red,green,blue,alpha*fade).endVertex();
    }

    private void drawEmissionArc(double x,double y,double z,BarrierWave wave,
                                 float progress,float alpha) {
        RiftBarrierGeometry.Arc arc=new RiftBarrierGeometry.Arc(wave.start,wave.target,progress);
        Vec3d origin=arc.origin,forward=arc.forward,planeUp=arc.side;
        double radius=arc.radius,halfAngle=arc.halfAngle;
        float visibility=clamp01(progress*8.0F);
        // A faint filled sector connects the origin to the luminous advancing edge.
        drawArcBand(x,y,z,origin,forward,planeUp,radius*.5D,halfAngle,
                Math.max(.001D,radius*.5D),.18F,.28F,1.0F,alpha*.20F*visibility);
        drawArcBand(x,y,z,origin,forward,planeUp,radius,halfAngle,
                Math.max(.10D,radius*.28D),.18F,.28F,1.0F,alpha*.48F*visibility);
        drawArcBand(x,y,z,origin,forward,planeUp,radius,halfAngle,
                .18D,.58F,.68F,1.0F,alpha*visibility);
    }

    private void drawArcBand(double x,double y,double z,Vec3d origin,Vec3d forward,Vec3d up,
                             double radius,double halfAngle,double thickness,
                             float red,float green,float blue,float alpha) {
        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder buffer=tessellator.getBuffer();
        // Radial and angular alpha ramps feather both sides of the growing sector.
        buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        for (int row=0;row<8;row++) {
            double r0=-1.0D+row/4.0D,r1=-1.0D+(row+1)/4.0D;
            for (int i=0;i<40;i++) {
                double a0=-1.0D+i/20.0D,a1=-1.0D+(i+1)/20.0D;
                addSectorVertex(buffer,x,y,z,origin,forward,up,radius,thickness,halfAngle,a0,r0,red,green,blue,alpha);
                addSectorVertex(buffer,x,y,z,origin,forward,up,radius,thickness,halfAngle,a1,r0,red,green,blue,alpha);
                addSectorVertex(buffer,x,y,z,origin,forward,up,radius,thickness,halfAngle,a1,r1,red,green,blue,alpha);
                addSectorVertex(buffer,x,y,z,origin,forward,up,radius,thickness,halfAngle,a0,r1,red,green,blue,alpha);
            }
        }
        tessellator.draw();
    }

    private void addSectorVertex(BufferBuilder buffer,double x,double y,double z,Vec3d origin,
                                 Vec3d forward,Vec3d up,double baseRadius,double thickness,
                                 double halfAngle,double angular,double radialOffset,
                                 float red,float green,float blue,float alpha) {
        double angle=angular*halfAngle;
        Vec3d radial=forward.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
        double radius=Math.max(0,baseRadius+radialOffset*thickness);
        float fade=(float)(Math.pow(Math.cos(radialOffset*Math.PI*.5D),2)
                *Math.min(1,(1-Math.abs(angular))*6));
        Vec3d point=origin.add(radial.scale(radius));
        if (!RiftBarrierGeometry.contains(point,0)) {
            RiftBarrierGeometry.Contact hit=RiftBarrierGeometry.intersect(origin,point);
            if (hit!=null) point=hit.point;
            fade=0;
        }
        buffer.pos(x+point.x,y+point.y,z+point.z)
                .color(red,green,blue,alpha*fade).endVertex();
    }

    private Vec3d surfaceTangent(Vec3d normal) {
        return Math.abs(normal.y)>.9D ? new Vec3d(1,0,0) : new Vec3d(-normal.z,0,normal.x);
    }

    private void drawImpactFlash(double x,double y,double z,Vec3d point,Vec3d normal,float radius,float alpha) {
        Vec3d tangent=surfaceTangent(normal);
        Vec3d vertical=normal.crossProduct(tangent);
        Vec3d center=point.add(normal.scale(.012D));
        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder buffer=tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_FAN,DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(x+center.x,y+center.y,z+center.z).color(.42F,.54F,1.0F,alpha).endVertex();
        for (int i=0;i<=24;i++) {
            double angle=Math.PI*2.0D*i/24.0D;
            Vec3d edge=center.add(tangent.scale(Math.cos(angle)*radius))
                    .add(vertical.scale(Math.sin(angle)*radius));
            // Keep each flare on the finite contacted wall or roof polygon.
            RiftBarrierGeometry.Contact bound=RiftBarrierGeometry.intersect(point,edge.subtract(normal.scale(.012D)));
            if (!RiftBarrierGeometry.contains(edge,.03D) && bound!=null) edge=bound.point.add(normal.scale(.012D));
            buffer.pos(x+edge.x,y+edge.y,z+edge.z)
                    .color(.18F,.28F,1.0F,0).endVertex();
        }
        tessellator.draw();
    }

    /** Two expanding rings of the same atlas glyphs used by TC's FXBlockRunes. */
    private void drawImpactRunes(double x,double y,double z,Vec3d point,Vec3d normal,long seed,float progress) {
        Vec3d tangent=surfaceTangent(normal);
        Vec3d vertical=normal.crossProduct(tangent);
        Vec3d center=point.add(normal.scale(.022D));
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(ParticleEngine.particleTexture);
        try {
            Tessellator tessellator=Tessellator.getInstance();
            BufferBuilder buffer=tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
            for (int ring=0;ring<2;ring++) {
                float phase=clamp01((progress-ring*.09F)/(1-ring*.09F));
                float opacity=clamp01(phase*10)*(1-phase)*.9F;
                double radius=.18D+ring*.20D+phase*.36D;
                int count=ring==0 ? 6 : 8;
                for (int i=0;i<count;i++) {
                    double angle=Math.PI*2*i/count+ring*.23D+phase*(ring==0 ? .22D : -.18D);
                    Vec3d glyph=center.add(tangent.scale(Math.cos(angle)*radius))
                            .add(vertical.scale(Math.sin(angle)*radius));
                    if (!RiftBarrierGeometry.contains(glyph,.03D)) continue;
                    int index=Math.floorMod((int)(seed+i*7+ring*11),16);
                    double half=.10D+.025D*(1-phase);
                    float red=ring==0 ? .58F : .25F,green=ring==0 ? .68F : .40F;
                    addRuneGlyph(buffer,x,y,z,glyph,tangent,vertical,index,half,red,green,opacity);
                }
            }
            tessellator.draw();
        } finally {
            GlStateManager.disableTexture2D();
        }
    }

    private void addRuneGlyph(BufferBuilder buffer,double x,double y,double z,Vec3d center,
                              Vec3d right,Vec3d up,int index,double half,float red,float green,float alpha) {
        double u0=index/64.0D,u1=u0+1/64.0D,v0=6/64.0D,v1=v0+1/64.0D;
        Vec3d a=center.subtract(right.scale(half)).subtract(up.scale(half));
        Vec3d b=center.add(right.scale(half)).subtract(up.scale(half));
        Vec3d c=center.add(right.scale(half)).add(up.scale(half));
        Vec3d d=center.subtract(right.scale(half)).add(up.scale(half));
        buffer.pos(x+a.x,y+a.y,z+a.z).tex(u0,v1).color(red,green,1.0F,alpha).endVertex();
        buffer.pos(x+b.x,y+b.y,z+b.z).tex(u1,v1).color(red,green,1.0F,alpha).endVertex();
        buffer.pos(x+c.x,y+c.y,z+c.z).tex(u1,v0).color(red,green,1.0F,alpha).endVertex();
        buffer.pos(x+d.x,y+d.y,z+d.z).tex(u0,v0).color(red,green,1.0F,alpha).endVertex();
    }

    private void renderOverloadSphere(TileRiftWell tile, double x, double y, double z,
                                      float partialTicks) {
        float radius = tile.getOverloadRadius();
        if (radius <= 0.0F) return;
        double ticks = tile.getWorld() == null ? partialTicks
                : tile.getWorld().getTotalWorldTime() + partialTicks;
        radius *= 1.0F + 0.025F * (float) Math.sin(ticks * 0.15D);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D,
                    y + TileRiftWell.LIQUID_SURFACE_Y, z + 0.5D);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(false);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            int latitudeSegments = 12;
            int longitudeSegments = 24;
            for (int lat = 0; lat < latitudeSegments; lat++) {
                double a0 = -Math.PI / 2.0D + Math.PI * lat / latitudeSegments;
                double a1 = -Math.PI / 2.0D + Math.PI * (lat + 1) / latitudeSegments;
                for (int lon = 0; lon < longitudeSegments; lon++) {
                    double b0 = Math.PI * 2.0D * lon / longitudeSegments;
                    double b1 = Math.PI * 2.0D * (lon + 1) / longitudeSegments;
                    addSphereVertex(buffer, radius, a0, b0);
                    addSphereVertex(buffer, radius, a1, b0);
                    addSphereVertex(buffer, radius, a1, b1);
                    addSphereVertex(buffer, radius, a0, b1);
                }
            }
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private void addSphereVertex(BufferBuilder buffer, float radius,
                                 double latitude, double longitude) {
        double horizontal = Math.cos(latitude) * radius;
        buffer.pos(horizontal * Math.cos(longitude), Math.sin(latitude) * radius,
                        horizontal * Math.sin(longitude))
                .color(0.22F, 0.08F, 0.78F, 0.075F).endVertex();
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

    private void renderParadoxumFlights(TileRiftWell tile, double x, double y, double z,
                                        float partialTicks) {
        if (tile.getWorld() == null || !tile.getWorld().isRemote) return;
        long tick = tile.getWorld().getTotalWorldTime();
        Long last = lastProcessedTicks.get(tile);
        if (last == null || last != tick) {
            lastProcessedTicks.put(tile, tick);
            updateParadoxumFlights(tile, tick);
        }

        List<ParadoxumFlight> flights = paradoxumFlights.get(tile);
        if (flights == null || flights.isEmpty()) return;
        double now = tick + partialTicks;
        for (ParadoxumFlight flight : flights) {
            float progress = (float) ((now - flight.startedAt) / flight.duration);
            if (progress < 0.0F || progress > 1.0F) continue;
            double px = flight.straight
                    ? lerp(flight.start.x, flight.target.x, progress)
                    : cubicBezier(flight.start.x, flight.control1.x,
                    flight.control2.x, flight.target.x, progress);
            double py = flight.straight
                    ? lerp(flight.start.y, flight.target.y, progress)
                    : cubicBezier(flight.start.y, flight.control1.y,
                    flight.control2.y, flight.target.y, progress);
            double pz = flight.straight
                    ? lerp(flight.start.z, flight.target.z, progress)
                    : cubicBezier(flight.start.z, flight.control1.z,
                    flight.control2.z, flight.target.z, progress);
            float pulse = 0.82F + 0.18F * (float) Math.sin(progress * Math.PI);
            float opacity=flight.straight ? (1.0F-progress)*(1.0F-progress) : 1.0F;
            renderParadoxumBillboard(x + px, y + py, z + pz, pulse,opacity);
        }
    }

    private void updateParadoxumFlights(TileRiftWell tile, long tick) {
        List<ParadoxumFlight> flights = paradoxumFlights.computeIfAbsent(
                tile, ignored -> new ArrayList<>());
        flights.removeIf(flight -> tick - flight.startedAt > flight.duration + 1.0F);
        if (tile.isCleansing()) {
            if (tile.isCleansingPaused()) return;
            if (tick % 2 == 0) {
                Vec3d start = tile.getRandomLiquidPoint(tile.getWorld().rand);
                Vec3d end = new Vec3d(0.5D, 1.8D, 0.5D);
                flights.add(new ParadoxumFlight(start, start.add(0,0.9D,0),
                        end.add((start.x-0.5D)*0.2D,0.4D,(start.z-0.5D)*0.2D), end, tick, 30, false));
            }
            return;
        }
        int count = Math.min(TileRiftWell.MAX_INTERCEPTOR_STONES,
                tile.getInstalledStoneCount());
        if (count <= 0 || !tile.hasParticleFlow()) return;

        long[] next = nextFlightTicks.computeIfAbsent(tile,
                ignored -> new long[TileRiftWell.MAX_INTERCEPTOR_STONES]);
        for (int slot = 0; slot < count; slot++) {
            if (tick < next[slot]) continue;
            Vec3d start = tile.getRandomLiquidPoint(tile.getWorld().rand);
            Vec3d target = new Vec3d(start.x,
                    TileRiftWell.RIFT_CENTER_Y_OFFSET-.45D
                            +tile.getWorld().rand.nextDouble()*.65D,start.z);
            flights.add(createFlight(start, target, tick, tile));
            next[slot] = tick + 5L + tile.getWorld().rand.nextInt(4);
        }
    }

    private ParadoxumFlight createFlight(Vec3d start, Vec3d target, long tick,
                                         TileRiftWell tile) {
        return new ParadoxumFlight(start, start, target, target,
                tick, FLIGHT_TICKS + tile.getWorld().rand.nextInt(17), true);
    }

    private double cubicBezier(double start, double control1, double control2,
                               double end, float progress) {
        double inverse = 1.0D - progress;
        return inverse * inverse * inverse * start
                + 3.0D * inverse * inverse * progress * control1
                + 3.0D * inverse * progress * progress * control2
                + progress * progress * progress * end;
    }

    private double lerp(double from, double to, float amount) {
        return from + (to - from) * amount;
    }

    public static void renderParadoxumBillboard(double x, double y, double z, float scale) {
        renderParadoxumBillboard(x,y,z,scale,1.0F);
    }

    public static void renderParadoxumBillboard(double x, double y, double z,
                                                float scale,float opacity) {
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
            drawParticleQuad(0.11F * scale, 0.18F, 0.28F, 1.0F, 0.34F*opacity);
            drawParticleQuad(0.060F * scale, 0.58F, 0.68F, 1.0F, opacity);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private static void drawParticleQuad(float radius, float red, float green,
                                  float blue, float alpha) {
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

    private void renderCleansingPearl(TileRiftWell tile, double x, double y, double z, float partialTicks) {
        if (!tile.isCleansing() || tile.getWorld() == null) return;
        long tick = tile.getWorld().getTotalWorldTime();
        float progress = tile.getCleansingProgress();
        double scale = 0.65D * (1.0D - 0.9D * progress);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x+0.5D, y+1.8D, z+0.5D);
            GlStateManager.rotate((tick+partialTicks)*2.0F,0,1,0);
            GlStateManager.scale(scale,scale,scale);
            RenderSafety.setFullBrightLightmap();
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(tile.getCleansingPearl(), ItemCameraTransforms.TransformType.FIXED);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix(); RenderSafety.resetGlState();
        }
        if (tile.isCleansingPaused()) return;
        Long last = lastRitualTicks.get(tile);
        if (last != null && last == tick) return;
        lastRitualTicks.put(tile,tick);
        if (tick % 2 == 0) {
            for (int i=0;i<1+(int)(progress*3);i++) {
                double angle = tile.getWorld().rand.nextDouble()*Math.PI*2;
                tile.getWorld().spawnParticle(EnumParticleTypes.ITEM_CRACK,
                        tile.getPos().getX()+0.5D+Math.cos(angle)*scale*0.2D,
                        tile.getPos().getY()+1.8D, tile.getPos().getZ()+0.5D+Math.sin(angle)*scale*0.2D,
                        Math.cos(angle)*0.015D,0.18D+progress*0.1D,Math.sin(angle)*0.015D,
                        Item.getIdFromItem(ItemsTC.primordialPearl),0);
            }
        }
    }

    private static final class WaveClock {
        private int lastSerial;
        private boolean initialized;
    }

    private static final class BarrierWave {
        private final List<RiftBarrierGeometry.ArcContact> contacts;
        private final List<Vec3d> aurora;
        private final Vec3d start;
        private final Vec3d target;
        private final long startedAt;
        private final float duration;
        private final float strength;
        private BarrierWave(List<Vec3d> aurora,Vec3d start,Vec3d target,long startedAt,
                            float duration,float strength) {
            this.aurora=aurora; this.start=start; this.target=target; this.startedAt=startedAt;
            this.duration=duration; this.strength=strength;
            this.contacts=RiftBarrierGeometry.arcContacts(start,target);
        }
    }

    private static final class ParadoxumFlight {
        private final Vec3d start;
        private final Vec3d control1;
        private final Vec3d control2;
        private final Vec3d target;
        private final long startedAt;
        private final float duration;
        private final boolean straight;

        private ParadoxumFlight(Vec3d start, Vec3d control1, Vec3d control2,
                                Vec3d target, long startedAt, float duration,
                                boolean straight) {
            this.start = start;
            this.control1 = control1;
            this.control2 = control2;
            this.target = target;
            this.startedAt = startedAt;
            this.duration = duration;
            this.straight = straight;
        }
    }
}
