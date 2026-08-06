package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.other.FXVoidStream;
import thaumcraft.client.fx.particles.FXBlockRunes;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.fx.FXDirectInfusionItem;
import therealpant.thaumicattempts.client.model.RiftonomiconModel;
import therealpant.thaumicattempts.client.model.RiftonomiconCoreModel;
import therealpant.thaumicattempts.client.RiftonomiconNavigationClient;
import therealpant.thaumicattempts.items.ItemPortalRune;
import therealpant.thaumicattempts.world.block.BlockRiftonomicon;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

import java.util.Map;
import java.util.WeakHashMap;

@SideOnly(Side.CLIENT)
public class RenderRiftonomicon extends GeoBlockRenderer<TileRiftonomicon> {
    private final RiftonomiconCoreModel coreModel = new RiftonomiconCoreModel();
    private final RiftonomiconWispRenderer navigationStoneRenderer = new RiftonomiconWispRenderer();
    private final Map<TileRiftonomicon, long[]> alchemyParticleTicks = new WeakHashMap<>();
    private final Map<TileRiftonomicon, Integer> transmutationFxPulses = new WeakHashMap<>();
    private final Map<TileRiftonomicon, Long> creationRuneTicks = new WeakHashMap<>();
    private boolean renderingCore;
    private boolean renderingNavigationStone;
    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/riftonomicon_\u0435.png");
    private static final ResourceLocation RIFT_TEXTURE =
            new ResourceLocation("textures/entity/end_portal.png");

    private static final float EMISSIVE_Y_OFFSET = 0.01F;
    private static final double CORE_RENDER_Y_OFFSET = -0.5D;
    private static final double RIFT_Y_OFFSET = 14.0D / 16.0D;
    private static final double TRANSMUTATION_PORTAL_Y = -BlockRiftonomicon.CORE_Y_OFFSET + RIFT_Y_OFFSET;
    private static final double TRANSMUTATION_HIDDEN_Y = -BlockRiftonomicon.CORE_Y_OFFSET + 0.5D - 6.0D / 16.0D;
    private static final double RIFT_HALF_SIZE = 25.0D / 16.0D;
    private static final int RIFT_PARTICLE_COUNT = 120;
    // bone4 pivot is Y=16px; at portal t=15s its animation adds sin(15*120+74) px.
    private static final double ALCHEMY_PARTICLE_TARGET_Y =
            (16.0D + Math.sin(Math.toRadians(15.0D * 120.0D + 74.0D))) / 16.0D;
    private static final double PROTOCOL_SLOT_Y = 39.0D / 16.0D + 0.01D;
    private static final double PROTOCOL_SLOT_OFFSET = 32.0D / 16.0D;
    private static final float PROTOCOL_ITEM_SCALE = 0.72F;
    private static final float PROTOCOL_TABLE_YAW = -45.0F;
    private static final float PROTOCOL_SLOT_TILT = 0F;
    private static final float PROTOCOL_ICON_TO_TABLE = 45F;
    private static final float PROTOCOL_ITEM_ROLL = 0F;
    private static final double[][] PROTOCOL_SLOT_POSITIONS = {
            { PROTOCOL_SLOT_OFFSET, PROTOCOL_SLOT_Y, -PROTOCOL_SLOT_OFFSET },
            { -PROTOCOL_SLOT_OFFSET, PROTOCOL_SLOT_Y, -PROTOCOL_SLOT_OFFSET },
            { PROTOCOL_SLOT_OFFSET, PROTOCOL_SLOT_Y, PROTOCOL_SLOT_OFFSET },
            { -PROTOCOL_SLOT_OFFSET, PROTOCOL_SLOT_Y, PROTOCOL_SLOT_OFFSET }
    };
    private static final float[] PROTOCOL_SLOT_YAWS = {
            0.0F,
            90.0F,
            -90.0F,
            180.0F
    };
    private static final double[] PROTOCOL_SLOT_ANIMATION_PHASES = {
            120.0D,
            240.0D,
            120.0D,
            240.0D
    };
    private static final int[][] CREATION_BARRIER_RING = {
            { -1, -1 }, { 0, -1 }, { 1, -1 },
            { -1, 0 },              { 1, 0 },
            { -1, 1 },  { 0, 1 },  { 1, 1 }
    };

    public RenderRiftonomicon() {
        super(new RiftonomiconModel());
    }

    @Override
    public void render(TileRiftonomicon te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        double baseY = y - BlockRiftonomicon.CORE_Y_OFFSET;
        int formationStage = te.getFormationStage();
        float[] prevLight = RenderSafety.captureLightmap();
        try {
            te.setCoreAnimationPass(false);
            if (formationStage >= 1) {
                super.render(te, x, baseY, z, partialTicks, destroyStage, alpha);
                renderEmissiveLayer(te, x, baseY, z, partialTicks);
                renderRiftSurface(te, x, baseY, z, partialTicks);
            }
            if (formationStage >= 3) {
                renderCore(te, x, y + CORE_RENDER_Y_OFFSET, z, partialTicks);
                renderCoreEmissiveLayer(te, x, y + CORE_RENDER_Y_OFFSET, z, partialTicks);
                renderNavigationStones(te, x, baseY, z, partialTicks);
                renderAlchemyPearls(te, x, y, z, partialTicks);
                renderCreationSamples(te, x, y, z, partialTicks);
                renderCreationCore(te, x, y, z, partialTicks);
                renderTransmutationPrisms(te, x, y, z, partialTicks);
                renderTransmutationItem(te, x, y, z, partialTicks);
                renderTransmutationAbsorptionFx(te);
                renderProtocolSlots(te, x, baseY, z, partialTicks);
            }
        } finally {
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderTransmutationAbsorptionFx(TileRiftonomicon te) {
        if (te.getWorld() == null) return;
        int pulse = te.getTransmutationImpetusPulse();
        Integer previous = transmutationFxPulses.put(te, pulse);
        if (previous == null || previous == pulse || !te.isTransmutationActive()) return;
        if (Math.floorMod(pulse, 5) != 0) return;
        double[] start = worldPoint(te, TRANSMUTATION_STREAM_START);
        BlockPos base = te.getPos().down(BlockRiftonomicon.CORE_Y_OFFSET);
        double[] target = {
                base.getX() + 0.5D + (te.getWorld().rand.nextDouble() * 2.0D - 1.0D) * RIFT_HALF_SIZE,
                base.getY() + RIFT_Y_OFFSET + 0.02D,
                base.getZ() + 0.5D + (te.getWorld().rand.nextDouble() * 2.0D - 1.0D) * RIFT_HALF_SIZE
        };
        addVoidStream(te, start, target);
    }

    private void renderTransmutationItem(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        ItemStack stack = te.getTransmutationItem();
        if (!te.hasTransmutationProtocol() || stack.isEmpty()) return;
        GlStateManager.pushMatrix();
        try {
            double startY = -0.42D;
            double submergedY = TRANSMUTATION_HIDDEN_Y;
            double itemY = startY;
            if (te.isTransmutationActive()) {
                double t = Math.min(1.0D, te.getTransmutationProgress() / 0.08D);
                itemY = viscousPortalPosition(t, startY, submergedY);
                if (t >= 1.0D) return;
            } else if (te.getTransmutationReturnTicks() > 0) {
                double t = 1.0D - Math.min(1.0D,
                        (te.getTransmutationReturnTicks() - partialTicks) / te.getTransmutationMotionTicks());
                itemY = viscousPortalPosition(slowFinalApproach(t), submergedY, startY);
            }
            GlStateManager.translate(x + 0.5D, y + itemY, z + 0.5D);
            GlStateManager.rotate((te.getWorld().getTotalWorldTime() + partialTicks) * 2.0F, 0, 1, 0);
            GlStateManager.scale(0.65F, 0.65F, 0.65F);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    private static final double[] TRANSMUTATION_STREAM_START = {0.0D, 1.0D, 0.0D};
    private static final double[][] TRANSMUTATION_PRISM_POSITIONS = {
            {-17.5D / 16.0D, 20.5D / 16.0D, -0.5D / 16.0D},
            {0.5D / 16.0D, 20.0D / 16.0D, -17.5D / 16.0D},
            {0.25D / 16.0D, 19.5D / 16.0D, 18.5D / 16.0D},
            {17.5D / 16.0D, 18.5D / 16.0D, -0.25D / 16.0D}
    };
    private static final float[] PRISM_THRESHOLDS = {0.20F, 0.50F, 0.80F, 0.90F};

    private void renderTransmutationPrisms(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        if (!te.hasTransmutationProtocol()) return;
        RenderItem renderer = Minecraft.getMinecraft().getRenderItem();
        float progress = te.getTransmutationProgress();
        for (int slot = 0; slot < TRANSMUTATION_PRISM_POSITIONS.length; slot++) {
            ItemStack prism = te.getTransmutationPrism(slot);
            if (prism.isEmpty()) continue;
            double[] p = TRANSMUTATION_PRISM_POSITIONS[slot];
            double renderY = p[1] - 14.0D / 16.0D;
            if (te.isTransmutationActive() && progress >= PRISM_THRESHOLDS[slot]) {
                double t = Math.min(1.0D, (progress - PRISM_THRESHOLDS[slot]) / 0.08D);
                if (t >= 1.0D) continue;
                renderY = viscousPortalPosition(t, renderY, TRANSMUTATION_HIDDEN_Y);
            }
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + 0.5D + p[0], y + renderY, z + 0.5D + p[2]);
                GlStateManager.rotate((te.getWorld().getTotalWorldTime() + partialTicks) * 2.0F, 0, 1, 0);
                GlStateManager.scale(0.55F, 0.55F, 0.55F);
                RenderHelper.enableStandardItemLighting();
                renderer.renderItem(prism, ItemCameraTransforms.TransformType.FIXED);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
            }
        }
    }

    /** Constant speed away from the rift, with a smooth viscous slowdown while crossing its plane. */
    private static double viscousPortalPosition(double t, double fromY, double toY) {
        t = Math.max(0.0D, Math.min(1.0D, t));
        final int samples = 64;
        double[] cumulative = new double[samples + 1];
        double previousY = fromY;
        double previousResistance = portalResistance(previousY);
        for (int i = 1; i <= samples; i++) {
            double y = fromY + (toY - fromY) * i / samples;
            double resistance = portalResistance(y);
            cumulative[i] = cumulative[i - 1]
                    + Math.abs(y - previousY) * (previousResistance + resistance) * 0.5D;
            previousY = y;
            previousResistance = resistance;
        }
        double target = cumulative[samples] * t;
        int index = 1;
        while (index < samples && cumulative[index] < target) index++;
        double span = cumulative[index] - cumulative[index - 1];
        double local = span <= 0.0D ? 0.0D : (target - cumulative[index - 1]) / span;
        double path = (index - 1 + local) / samples;
        return fromY + (toY - fromY) * path;
    }

    private static double portalResistance(double y) {
        double distance = Math.abs(y - TRANSMUTATION_PORTAL_Y) / (5.0D / 16.0D);
        return 1.0D + 5.0D * Math.exp(-distance * distance * distance * distance);
    }

    /** Keeps normal travel speed, then continuously brakes to zero over the final quarter. */
    private static double slowFinalApproach(double t) {
        t = Math.max(0.0D, Math.min(1.0D, t));
        final double brakingStart = 0.75D;
        final double speed = 2.0D / (1.0D + brakingStart);
        if (t <= brakingStart) return speed * t;
        double duration = 1.0D - brakingStart;
        double x = (t - brakingStart) / duration;
        double positionAtBraking = speed * brakingStart;
        return positionAtBraking + speed * duration * (x - 0.5D * x * x);
    }

    private static double[] worldPoint(TileRiftonomicon te, double[] local) {
        return new double[]{te.getPos().getX() + 0.5D + local[0], te.getPos().getY() + local[1],
                te.getPos().getZ() + 0.5D + local[2]};
    }

    private static void addVoidStream(TileRiftonomicon te, double[] from, double[] to) {
        FXVoidStream stream = new FXVoidStream(te.getWorld(), from[0], from[1], from[2], to[0], to[1], to[2],
                te.getWorld().rand.nextInt(), 0.18F);
        ParticleEngine.addEffect(te.getWorld(), stream);
    }

    private void renderAlchemyPearls(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        if (!te.hasAlchemicalProtocol()) return;
        RenderItem renderer = Minecraft.getMinecraft().getRenderItem();
        double[][] offsets = RiftonomiconNavigationClient.ALCHEMY_PEARL_OFFSETS;
        for (int slot = 0; slot < offsets.length; slot++) {
            ItemStack pearl = te.getAlchemyPearl(slot);
            if (pearl.isEmpty()) continue;
            double[] off = offsets[slot];
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + 0.5D + off[0], y + off[1], z + 0.5D + off[2]);
                GlStateManager.rotate((te.getWorld().getTotalWorldTime() + partialTicks) * 2.0F,
                        0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.55F, 0.55F, 0.55F);
                RenderHelper.enableStandardItemLighting();
                renderer.renderItem(pearl, ItemCameraTransforms.TransformType.FIXED);
            } finally {
                GlStateManager.popMatrix();
                RenderHelper.disableStandardItemLighting();
            }

            if (markAlchemyParticleTick(te, slot)) {
                spawnAlchemyItemParticles(te, off, pearl);
            }
        }
    }

    private void renderCreationSamples(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        if (!te.hasCreationProtocol()) return;
        RenderItem renderer = Minecraft.getMinecraft().getRenderItem();
        double[][] offsets = RiftonomiconNavigationClient.ALCHEMY_PEARL_OFFSETS;
        int limit = te.getCreationStationLimit();
        for (int slot = 0; slot < limit; slot++) {
            ItemStack sample = te.getCreationSample(slot);
            if (sample.isEmpty()) continue;
            double[] off = offsets[slot];
            GlStateManager.pushMatrix();
            try {
                double bob = te.isCreationResearchActive(slot)
                        ? Math.sin((te.getWorld().getTotalWorldTime() + partialTicks) * 0.18D + slot) * 0.06D : 0.0D;
                GlStateManager.translate(x + 0.5D + off[0],
                        y + off[1] - 1.0D - RiftonomiconNavigationClient.CREATION_SLOT_LOWERING + bob,
                        z + 0.5D + off[2]);
                float speed = te.isCreationResearchActive(slot) ? 6.0F : 2.0F;
                GlStateManager.rotate((te.getWorld().getTotalWorldTime() + partialTicks) * speed,
                        0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.55F, 0.55F, 0.55F);
                RenderHelper.enableStandardItemLighting();
                renderer.renderItem(sample, ItemCameraTransforms.TransformType.FIXED);
            } finally {
                GlStateManager.popMatrix();
                RenderHelper.disableStandardItemLighting();
            }
        }
    }

    private void renderCreationCore(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        if (!te.hasCreationProtocol()) return;
        ItemStack core = te.getCreationCoreItem();
        if (core.isEmpty()) return;
        GlStateManager.pushMatrix();
        try {
            double motion = getCreationCoreMotion(te, partialTicks);
            GlStateManager.translate(x + 0.5D, y - 0.42D + motion, z + 0.5D);
            GlStateManager.rotate((te.getWorld().getTotalWorldTime() + partialTicks) * 2.0F, 0, 1, 0);
            GlStateManager.scale(0.65F, 0.65F, 0.65F);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(core, ItemCameraTransforms.TransformType.FIXED);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
        spawnCreationRunes(te);
    }

    private double getCreationCoreMotion(TileRiftonomicon te, float partialTicks) {
        int ticks = te.getCreationCompletionTicks();
        if (ticks <= 0) return 0.0D;
        double time = Math.min(TileRiftonomicon.CREATION_COMPLETION_TOTAL_TICKS, ticks + partialTicks);
        double midpoint = TileRiftonomicon.CREATION_COMPLETION_TOTAL_TICKS / 2.0D;
        double phase;
        if (time <= midpoint) {
            phase = time / midpoint;
        } else {
            phase = 1.0D - (time - midpoint)
                    / (TileRiftonomicon.CREATION_COMPLETION_TOTAL_TICKS - midpoint);
        }
        phase = MathHelper.clamp(phase, 0.0D, 1.0D);
        double smooth = phase * phase * (3.0D - 2.0D * phase);
        return -2.15D * smooth;
    }

    private void spawnCreationRunes(TileRiftonomicon te) {
        if (!te.isCreationVisualActive() || te.getWorld() == null) return;
        long tick = te.getWorld().getTotalWorldTime();
        Long previous = creationRuneTicks.get(te);
        if (previous != null && previous == tick) return;
        creationRuneTicks.put(te, tick);

        int[] ringOffset = CREATION_BARRIER_RING[te.getWorld().rand.nextInt(CREATION_BARRIER_RING.length)];
        int layer = 1 + te.getWorld().rand.nextInt(2);
        BlockPos base = te.getPos().down(BlockRiftonomicon.CORE_Y_OFFSET);
        float red = 0.6F + te.getWorld().rand.nextFloat() * 0.4F;
        float blue = 0.3F + te.getWorld().rand.nextFloat() * 0.7F;
        FXBlockRunes rune = new FXBlockRunes(te.getWorld(),
                base.getX() + ringOffset[0] + 0.5D,
                base.getY() + layer + 0.5D,
                base.getZ() + ringOffset[1] + 0.5D,
                red, 0.0F, blue, 20);
        rune.setGravity(0.0F);
        ParticleEngine.addEffect(te.getWorld(), rune);
    }

    private boolean markAlchemyParticleTick(TileRiftonomicon te, int slot) {
        long tick = te.getWorld().getTotalWorldTime();
        long[] lastTicks = alchemyParticleTicks.get(te);
        if (lastTicks == null) {
            lastTicks = new long[TileRiftonomicon.ALCHEMY_PEARL_SLOT_COUNT];
            java.util.Arrays.fill(lastTicks, Long.MIN_VALUE);
            alchemyParticleTicks.put(te, lastTicks);
        }
        if (lastTicks[slot] == tick) return false;
        lastTicks[slot] = tick;
        return true;
    }

    private void spawnAlchemyItemParticles(TileRiftonomicon te, double[] off, ItemStack pearl) {
        double sx = te.getPos().getX() + 0.5D + off[0];
        double sy = te.getPos().getY() + off[1];
        double sz = te.getPos().getZ() + 0.5D + off[2];
        double tx = te.getPos().getX() + 0.5D;
        double ty = te.getPos().getY() + ALCHEMY_PARTICLE_TARGET_Y;
        double tz = te.getPos().getZ() + 0.5D;
        // TC6 creates four fragments per tick. We retain its original fragment
        // size and texture, but create only two instances (half intensity).
        for (int i = 0; i < 0.5; i++) {
            double jitterX = (te.getWorld().rand.nextDouble() - 0.5D) * 0.2D;
            double jitterY = (te.getWorld().rand.nextDouble() - 0.5D) * 0.2D;
            double jitterZ = (te.getWorld().rand.nextDouble() - 0.5D) * 0.2D;
            Minecraft.getMinecraft().effectRenderer.addEffect(new FXDirectInfusionItem(
                    te.getWorld(), sx + jitterX, sy + jitterY, sz + jitterZ,
                    tx, ty, tz, pearl, te.getPos().up()
            ));
        }
    }

    @Override
    public AnimatedGeoModel<TileRiftonomicon> getGeoModelProvider() {
        if (renderingNavigationStone) return navigationStoneRenderer.getModelProvider();
        return renderingCore ? coreModel : super.getGeoModelProvider();
    }

    private void renderCoreEmissiveLayer(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        renderingCore = true;
        te.setCoreAnimationPass(true);
        float[] previousLight = RenderSafety.captureLightmap();
        GlStateManager.pushMatrix();
        try {
            GeoModel model = coreModel.getModel(coreModel.getModelLocation(te));
            GlStateManager.translate(x + 0.5D, y + EMISSIVE_Y_OFFSET, z + 0.5D);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            bindTexture(TEX_EMISSIVE);
            this.render(model, te, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
            te.setCoreAnimationPass(false);
            renderingCore = false;
        }
    }

    private void renderNavigationStones(TileRiftonomicon te, double x, double baseY, double z, float partialTicks) {
        if (!te.hasNavigationProtocol()) return;

        renderingNavigationStone = true;
        te.setNavigationStoneAnimationPass(true);
        try {
            BlockPos basePos = te.getPos().down(BlockRiftonomicon.CORE_Y_OFFSET);
            int limit = te.getNavigationRuneSlotLimit();
            for (int slot = 0; slot < limit; slot++) {
                ItemPortalRune.PortalPoint target = te.getNavigationRuneTarget(slot);
                if (target == null) continue;

                int animationId = getUniqueID(te) * 31 + slot + 0x4E4156;
                int phaseTicks = getNavigationStonePhase(te, slot);
                navigationStoneRenderer.prepareAnimation(te, animationId, phaseTicks);

                double[] offset = RiftonomiconNavigationClient.getStoneOffset(slot);
                float yaw = RiftonomiconNavigationClient.getStoneYaw(offset);
                BlockPos lightPos = new BlockPos(
                        basePos.getX() + 0.5D + offset[0],
                        basePos.getY() + offset[1],
                        basePos.getZ() + 0.5D + offset[2]
                );
                int light = te.getWorld().getCombinedLight(
                        lightPos, RiftonomiconNavigationClient.WISP_MINIMUM_BLOCK_LIGHT
                );
                double stoneX = x + 0.5D + offset[0];
                double stoneY = baseY + offset[1];
                double stoneZ = z + 0.5D + offset[2];

                navigationStoneRenderer.render(te, stoneX, stoneY, stoneZ, yaw,
                        light, partialTicks, false, target, TEX_EMISSIVE);
                navigationStoneRenderer.render(te, stoneX, stoneY, stoneZ, yaw,
                        light, partialTicks, true, target, TEX_EMISSIVE);
            }
        } finally {
            te.setNavigationStoneAnimationPass(false);
            renderingNavigationStone = false;
        }
    }

    private int getNavigationStonePhase(TileRiftonomicon te, int slot) {
        long value = te.getPos().toLong() ^ (slot * 0x9E3779B97F4A7C15L);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int) Math.floorMod(value, 60L);
    }

    private void renderCore(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        renderingCore = true;
        te.setCoreAnimationPass(true);
        GlStateManager.pushMatrix();
        try {
            GeoModel model = coreModel.getModel(coreModel.getModelLocation(te));
            coreModel.setLivingAnimations(te, getUniqueID(te));

            int light = te.getWorld().getCombinedLight(te.getPos(), 0);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    light % 65536, light / 65536);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

            GlStateManager.translate(x + 0.5D, y + 0.01D, z + 0.5D);
            bindTexture(coreModel.getTextureLocation(te));
            this.render(model, te, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
            te.setCoreAnimationPass(false);
            renderingCore = false;
        }
    }

    private void renderEmissiveLayer(TileRiftonomicon te,
                                     double x, double y, double z,
                                     float partialTicks) {
        if (te == null || te.getWorld() == null) return;

        GeoModel model = this.getGeoModelProvider().getModel(
                this.getGeoModelProvider().getModelLocation(te)
        );

        float[] prevLight = RenderSafety.captureLightmap();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + EMISSIVE_Y_OFFSET, z + 0.5D);

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

    private void renderProtocolSlots(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        if (te == null || te.getWorld() == null) return;

        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (int slot = 0; slot < TileRiftonomicon.PROTOCOL_SLOT_COUNT; slot++) {
            ItemStack stack = te.getProtocolInSlot(slot);
            if (stack.isEmpty()) continue;

            double[] position = PROTOCOL_SLOT_POSITIONS[slot];
            double animationY = getProtocolSlotAnimationY(te, partialTicks, slot);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + 0.5D + position[0], y + position[1] + animationY, z + 0.5D + position[2]);
                GlStateManager.rotate(PROTOCOL_SLOT_YAWS[slot], 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(PROTOCOL_TABLE_YAW, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(PROTOCOL_SLOT_TILT, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(PROTOCOL_ICON_TO_TABLE, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(PROTOCOL_ITEM_ROLL, 0.0F, 0.0F, 1.0F);
                GlStateManager.scale(PROTOCOL_ITEM_SCALE, PROTOCOL_ITEM_SCALE, PROTOCOL_ITEM_SCALE);
                renderItem.renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
            } finally {
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.disableRescaleNormal();
    }

    private double getProtocolSlotAnimationY(TileRiftonomicon te, float partialTicks, int slot) {
        double animTime = (te.getWorld().getTotalWorldTime() + partialTicks) / 20.0D;
        double phase = PROTOCOL_SLOT_ANIMATION_PHASES[slot];
        return Math.sin(Math.toRadians(animTime * 120.0D + phase)) / 16.0D;
    }

    private void renderRiftSurface(TileRiftonomicon te, double x, double y, double z, float partialTicks) {
        if (te == null || te.getWorld() == null) return;

        double minX = x + 0.5D - RIFT_HALF_SIZE;
        double maxX = x + 0.5D + RIFT_HALF_SIZE;
        double minZ = z + 0.5D - RIFT_HALF_SIZE;
        double maxZ = z + 0.5D + RIFT_HALF_SIZE;
        double surfaceY = y + RIFT_Y_OFFSET;
        double time = te.getWorld().getTotalWorldTime() + partialTicks;

        float[] prevLight = RenderSafety.captureLightmap();
        GlStateManager.pushMatrix();
        try {
            RenderSafety.setFullBrightLightmap();

            GlStateManager.enableBlend();
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableCull();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            renderRiftVoid(minX, maxX, minZ, maxZ, surfaceY - 0.001D);

            bindTexture(RIFT_TEXTURE);
            GlStateManager.enableTexture2D();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE
            );

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, UtilsFX.VERTEXFORMAT_POS_TEX_CO_LM_NO);
            renderRiftTextureLayer(buffer, minX, maxX, minZ, maxZ, surfaceY, time * 0.004D,
                    -time * 0.003D, 1.0F, 0.10F, 0.92F, 0.95F, 0.34F);
            renderRiftTextureLayer(buffer, minX, maxX, minZ, maxZ, surfaceY + 0.002D, -time * 0.002D,
                    time * 0.005D, 1.65F, 0.48F, 0.22F, 0.90F, 0.22F);
            renderRiftTextureLayer(buffer, minX, maxX, minZ, maxZ, surfaceY + 0.004D, time * 0.006D,
                    time * 0.001D, 2.35F, 0.06F, 0.34F, 1.00F, 0.18F);
            tessellator.draw();

            renderRiftParticles(minX, maxX, minZ, maxZ, surfaceY + 0.010D, time);
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderRiftVoid(double minX, double maxX, double minZ, double maxZ, double y) {
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        addColorVertex(buffer, minX, y, minZ, 0.0F, 0.0F, 0.0F, 0.96F);
        addColorVertex(buffer, minX, y, maxZ, 0.0F, 0.0F, 0.0F, 0.96F);
        addColorVertex(buffer, maxX, y, maxZ, 0.0F, 0.0F, 0.0F, 0.96F);
        addColorVertex(buffer, maxX, y, minZ, 0.0F, 0.0F, 0.0F, 0.96F);
        addColorVertex(buffer, maxX, y, minZ, 0.0F, 0.0F, 0.0F, 0.96F);
        addColorVertex(buffer, maxX, y, maxZ, 0.0F, 0.0F, 0.0F, 0.96F);
        addColorVertex(buffer, minX, y, maxZ, 0.0F, 0.0F, 0.0F, 0.96F);
        addColorVertex(buffer, minX, y, minZ, 0.0F, 0.0F, 0.0F, 0.96F);
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private void renderRiftTextureLayer(BufferBuilder buffer,
                                        double minX, double maxX,
                                        double minZ, double maxZ,
                                        double y, double uOffset, double vOffset,
                                        float scale, float r, float g, float b, float a) {
        float u1 = (float) uOffset;
        float v1 = (float) vOffset;
        float u2 = u1 + scale;
        float v2 = v1 + scale;

        addRiftVertex(buffer, minX, y, minZ, u1, v1, r, g, b, a, 1.0F);
        addRiftVertex(buffer, minX, y, maxZ, u1, v2, r, g, b, a, 1.0F);
        addRiftVertex(buffer, maxX, y, maxZ, u2, v2, r, g, b, a, 1.0F);
        addRiftVertex(buffer, maxX, y, minZ, u2, v1, r, g, b, a, 1.0F);
        addRiftVertex(buffer, maxX, y, minZ, u2, v1, r, g, b, a, -1.0F);
        addRiftVertex(buffer, maxX, y, maxZ, u2, v2, r, g, b, a, -1.0F);
        addRiftVertex(buffer, minX, y, maxZ, u1, v2, r, g, b, a, -1.0F);
        addRiftVertex(buffer, minX, y, minZ, u1, v1, r, g, b, a, -1.0F);
    }

    private void renderRiftParticles(double minX, double maxX, double minZ, double maxZ, double y, double time) {
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE
        );

        double width = maxX - minX;
        double depth = maxZ - minZ;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < RIFT_PARTICLE_COUNT; i++) {
            double speedX = (noise(i, 17) - 0.5D) * 0.006D;
            double speedZ = (noise(i, 29) - 0.5D) * 0.006D;
            double px = minX + wrapUnit(noise(i, 3) + time * speedX) * width;
            double pz = minZ + wrapUnit(noise(i, 5) + time * speedZ) * depth;
            double size = 0.018D + noise(i, 7) * 0.045D;
            if (noise(i, 11) > 0.82D) size *= 1.8D;

            float pulse = 0.55F + 0.45F * (float) Math.sin(time * (0.08D + noise(i, 13) * 0.08D) + noise(i, 19) * Math.PI * 2.0D);
            float alpha = (0.16F + pulse * 0.34F) * (float) (0.65D + noise(i, 23) * 0.55D);
            float[] color = particleColor(i);
            addParticleQuad(buffer, px, y + (i % 5) * 0.002D, pz, size, color[0], color[1], color[2], alpha);
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private void addRiftVertex(BufferBuilder buffer, double x, double y, double z,
                               float u, float v, float r, float g, float b, float a,
                               float normalY) {
        buffer.pos(x, y, z)
                .tex(u, v)
                .color(r, g, b, a)
                .lightmap(0, 220)
                .normal(0.0F, normalY, 0.0F)
                .endVertex();
    }

    private void addParticleQuad(BufferBuilder buffer, double x, double y, double z, double size,
                                 float r, float g, float b, float a) {
        double minX = x - size;
        double maxX = x + size;
        double minZ = z - size;
        double maxZ = z + size;
        addColorVertex(buffer, minX, y, minZ, r, g, b, a);
        addColorVertex(buffer, minX, y, maxZ, r, g, b, a);
        addColorVertex(buffer, maxX, y, maxZ, r, g, b, a);
        addColorVertex(buffer, maxX, y, minZ, r, g, b, a);
    }

    private void addColorVertex(BufferBuilder buffer, double x, double y, double z,
                                float r, float g, float b, float a) {
        buffer.pos(x, y, z)
                .color(r, g, b, a)
                .endVertex();
    }

    private float[] particleColor(int index) {
        switch (index % 6) {
            case 0:
                return new float[]{0.10F, 0.95F, 0.95F};
            case 1:
                return new float[]{0.12F, 0.65F, 1.00F};
            case 2:
                return new float[]{0.38F, 0.95F, 0.65F};
            case 3:
                return new float[]{0.55F, 0.32F, 0.95F};
            case 4:
                return new float[]{0.20F, 0.85F, 0.72F};
            default:
                return new float[]{0.58F, 0.72F, 0.82F};
        }
    }

    private double noise(int index, int salt) {
        long value = index * 73428767L + salt * 912931L + 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value = value ^ (value >>> 31);
        return (double) (value & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private double wrapUnit(double value) {
        return value - Math.floor(value);
    }
}
