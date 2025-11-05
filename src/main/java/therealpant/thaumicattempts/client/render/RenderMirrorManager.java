// src/main/java/therealpant/thaumicattempts/client/render/RenderMirrorManager.java
package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

@SideOnly(Side.CLIENT)
public class RenderMirrorManager extends net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<TileMirrorManager> {

    private static final double RADIUS = 1.6;
    private static final float  BASE_Y = 1.35f;
    private static final float  Y_STEP = 0.48f;
    private static final float  BOB_AMPL = 0.06f;
    private static final float  BOB_SPEED = (float)(Math.PI * 2.0 / 60.0);
    private static final float  SPIN_BASE = 1.6f;
    private static final float  SCALE = 0.60f;
    private static final float  RING_SHIFT_YAW = 30f;

    private final ItemStack renderMirror = new ItemStack(thaumcraft.api.blocks.BlocksTC.mirror);

    private static Vec3d addXYZ(Vec3d v, double dx, double dy, double dz) {
        return new Vec3d(v.x + dx, v.y + dy, v.z + dz);
    }

    @Override
    public void render(TileMirrorManager te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        long t = te.getWorld().getTotalWorldTime();

        // === 1) АКТИВНЫЕ ЗЕРКАЛА (как раньше) ===
        java.util.List<TileMirrorManager.MirrorSlot> mirrors = te.getRenderMirrors();
        for (int i = 0; i < mirrors.size() && i < 24; i++) {
            TileMirrorManager.MirrorSlot m = mirrors.get(i);
            float base = m.slot * 60f;
            if (m.ring == 1 || m.ring == 3) base += RING_SHIFT_YAW;

            double ang = Math.toRadians(base);
            double px = Math.cos(ang) * RADIUS;
            double pz = Math.sin(ang) * RADIUS;

            float tt = (t + partialTicks);
            float phaseF = (m.phase & 0xFFFF) * 0.001f;
            float bob = MathHelper.sin((tt + phaseF) * BOB_SPEED) * BOB_AMPL;
            float py = BASE_Y + m.ring * Y_STEP + bob;

            float speedMul = 0.9f + ((m.phase & 255) / 255f) * 0.4f;
            float dir = (((m.phase >> 9) & 1) == 0) ? 1f : -1f;
            float spin = ((tt + phaseF * 60f) * SPIN_BASE * speedMul * dir) % 360f;

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, pz);
            GlStateManager.rotate(base + spin, 0, 1, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);

            BlockPos lp = new BlockPos(te.getPos().getX() + px, te.getPos().getY() + py, te.getPos().getZ() + pz);
            int light = te.getWorld().getCombinedLight(lp, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    (float)(light & 0xFFFF),
                    (float)((light >> 16) & 0xFFFF));

            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(renderMirror, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();

            GlStateManager.popMatrix();
        }

        // === 2) ПОДВЕШЕННЫЕ К ВЫБРОСУ ЗЕРКАЛА (призраки без «дёрганья») ===
        java.util.List<int[]> pend = te.getPendingEjectVisuals(); // {ring,slot,age,ttl}
        int ttl = te.getEjectHoverTicks();
        for (int[] v : pend) {
            int ring = v[0], slot = v[1], age = v[2];
            float p = MathHelper.clamp((float)age / Math.max(1, ttl), 0f, 1f);

            float base = slot * 60f;
            if (ring == 1 || ring == 3) base += RING_SHIFT_YAW;

            double ang = Math.toRadians(base);
            double px = Math.cos(ang) * RADIUS;
            double pz = Math.sin(ang) * RADIUS;

            // лёгкое «замирающее» покачивание и медленный спин
            float tt = (t + partialTicks);
            float bob = MathHelper.sin(tt * 0.5f) * (BOB_AMPL * 0.35f);
            float py = BASE_Y + ring * Y_STEP + bob;
            float spin = (tt * (SPIN_BASE * 0.25f)) % 360f;

            // плавный fade-out в последнюю треть времени
            float a = (p < 0.66f) ? 0.75f : MathHelper.clamp(0.75f * (1f - (p - 0.66f) / 0.34f), 0f, 0.75f);

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, pz);
            GlStateManager.rotate(base + spin, 0, 1, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);

            BlockPos lp = new BlockPos(te.getPos().getX() + px, te.getPos().getY() + py, te.getPos().getZ() + pz);
            int light = te.getWorld().getCombinedLight(lp, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    (float)(light & 0xFFFF),
                    (float)((light >> 16) & 0xFFFF));

            GlStateManager.color(1F, 1F, 1F, a);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(renderMirror, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1F, 1F, 1F, 1F);

            GlStateManager.popMatrix();
        }

        // === 3) ЛЕТЯЩИЕ ПРЕДМЕТЫ ПО ДУГЕ (как было) ===
        te.clientCullFlying();
        java.util.List<TileMirrorManager.FlyingItem> fly = te.getFlying();
        for (TileMirrorManager.FlyingItem f : fly) {
            if (f.stack.isEmpty()) continue;

            float base = f.slot * 60f;
            if (f.ring == 1 || f.ring == 3) base += RING_SHIFT_YAW;
            double ang = Math.toRadians(base);

            Vec3d P2 = new Vec3d(Math.cos(ang) * RADIUS,
                    BASE_Y + f.ring * Y_STEP,
                    Math.sin(ang) * RADIUS);

            Vec3d P0 = new Vec3d(0.0, BASE_Y + 0.15, 0.0);

            double midUp = 0.65 + ((f.seed & 31) / 31.0) * 0.30;
            Vec3d P1 = addXYZ(P0.add(P2).scale(0.5), 0, midUp, 0);

            float tt2 = (t + partialTicks) - f.start;
            float p = MathHelper.clamp(tt2 / (float)f.duration, 0f, 1f);
            float ease = (p < 0.5f) ? (2f * p * p) : (1f - (float)Math.pow(-2f * p + 2f, 2) / 2f);

            float cut = 0.90f + (((f.seed >> 17) & 15) / 15f) * 0.05f;
            if (ease >= cut) continue;
            float tail = 0.08f;
            float a = 1f;
            if (ease > cut - tail) a = MathHelper.clamp((cut - ease) / tail, 0f, 1f);

            double u = 1.0 - ease;
            Vec3d pos = P0.scale(u*u).add(P1.scale(2*u*ease)).add(P2.scale(ease*ease));

            float wob = (float)Math.sin((f.seed % 1000) * 0.013 + (t + partialTicks) * 0.25) * 6f;
            float spin = ((t + partialTicks) * (1.2f + ((f.seed & 255)/255f))) % 360f;

            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.x, pos.y, pos.z);
            GlStateManager.rotate(spin, 0, 1, 0);
            GlStateManager.rotate(wob, 0, 0, 1);
            GlStateManager.scale(0.50f, 0.50f, 0.50f);

            BlockPos lp = new BlockPos(te.getPos().getX() + pos.x, te.getPos().getY() + pos.y, te.getPos().getZ() + pos.z);
            int light = te.getWorld().getCombinedLight(lp, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                    (float)(light & 0xFFFF),
                    (float)((light >> 16) & 0xFFFF));

            GlStateManager.color(1F, 1F, 1F, a);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(f.stack, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1F, 1F, 1F, 1F);

            GlStateManager.popMatrix();
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
