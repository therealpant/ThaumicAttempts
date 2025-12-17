// src/main/java/therealpant/thaumicattempts/client/render/RenderMirrorManagerGeo.java
package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import thaumcraft.api.blocks.BlocksTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.MirrorManagerModel;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;

import java.util.List;

@SideOnly(Side.CLIENT)
public class RenderMirrorManagerGeo extends GeoBlockRenderer<TileMirrorManager> {

    // Эмиссивная текстура: в ней оставлены только "светящиеся" пиксели,
    // все остальные прозрачные. Координаты пикселей совпадают с базовой
    // текстурой модели (смещение 0:28 ты уже учёл в самом PNG).
    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/mirror_manager_e.png");

    private static final float EMISSIVE_Y_OFFSET = 0.01f; // подбери значение сам

    private static final double RADIUS = 2.1;
    private static final float  BASE_Y = 1.35f;
    private static final float  Y_STEP = 0.64f;
    private static final float  BOB_AMPL = 0.06f;
    private static final float  BOB_SPEED = (float)(Math.PI * 2.0 / 60.0);
    private static final float  SPIN_BASE = 1.6f;
    private static final float  SCALE = 0.60f;
    private static final float  RING_SHIFT_YAW = 30f;
    private static final float  FOCUS_BLEND_SPEED = 0.25f;
    private static final float  FOCUS_LERP_SPEED = 18f;
    private static final float  SPIN_LERP_SPEED = 90f;
    private static final float  DELIVERY_SPIN_ACCEL = 1.5f;

    private final ItemStack renderMirror = new ItemStack(BlocksTC.mirror);

    public RenderMirrorManagerGeo() {
        super(new MirrorManagerModel());
    }

    private static Vec3d addXYZ(Vec3d v, double dx, double dy, double dz) {
        return new Vec3d(v.x + dx, v.y + dy, v.z + dz);
    }

    private static float computeZeroYaw(double px, double pz) {
        double nx = -px;
        double nz = -pz;
        return (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(nx, nz)));
    }

    /**
     * Второй проход под эмисс: та же Geo-модель, но с TEX_EMISSIVE
     * и фуллбрайтом. ВАЖНО: переводим матрицу в (x+0.5, y, z+0.5),
     * иначе модель рисуется в (0,0,0) — там, где сейчас игрок.
     */
    private void renderEmissiveLayer(TileMirrorManager te,
                                     double x, double y, double z,
                                     float partialTicks) {
        if (te == null || te.getWorld() == null) return;

        GeoModel model = this.getGeoModelProvider().getModel(
                this.getGeoModelProvider().getModelLocation(te)
        );

        float prevX = OpenGlHelper.lastBrightnessX;
        float prevY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        try {
            // та же позиция, что и базовая модель, но чуть выше по Y
            GlStateManager.translate(x + 0.5, y + EMISSIVE_Y_OFFSET, z +0.5);

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
            GlStateManager.enableLighting();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevX, prevY);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void render(TileMirrorManager te,
                       double x, double y, double z,
                       float partialTicks,
                       int destroyStage,
                       float alpha) {

        if (te == null || te.getWorld() == null) return;

        // 1) базовая гео-модель с обычным освещением
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);

        // 2) эмисс-проход: те же вершины, но TEX_EMISSIVE и фуллбрайт
        renderEmissiveLayer(te, x, y, z, partialTicks);

        // 3) дальше — твой старый код (зеркала, летающие предметы)
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

        // === 1) АКТИВНЫЕ ЗЕРКАЛА ===
        List<TileMirrorManager.MirrorSlot> mirrors = te.getRenderMirrors();
        for (int i = 0; i < mirrors.size() && i < 24; i++) {
            TileMirrorManager.MirrorSlot m = mirrors.get(i);
            float base = m.slot * 60f;
            if (m.ring == 1 || m.ring == 3) base += RING_SHIFT_YAW;

            double ang = Math.toRadians(base);
            double px = Math.cos(ang) * RADIUS;
            double pz = Math.sin(ang) * RADIUS;

            float time = (t + partialTicks);
            float phaseF = (m.phase & 0xFFFF) * 0.001f;

            float deltaTime = Float.isNaN(m.lastRenderTime) ? 0f : (time - m.lastRenderTime);
            m.lastRenderTime = time;
            float dt = (deltaTime > 0f) ? deltaTime : partialTicks;

            boolean delivering = (m.focusUntil > (t + partialTicks));
            float targetFocus = delivering ? 1f : 0f;
            float focusStep = FOCUS_BLEND_SPEED * partialTicks;
            if (m.renderFocus < targetFocus) {
                m.renderFocus = Math.min(targetFocus, m.renderFocus + focusStep);
            } else if (m.renderFocus > targetFocus) {
                m.renderFocus = Math.max(targetFocus, m.renderFocus - focusStep);
            }

            if (m.renderDelivering != delivering) {
                if (!delivering) {
                    m.idleSpin = 0f;
                }
                m.lastRenderTime = time;
                m.renderDelivering = delivering;
            }

            float bob = MathHelper.sin((time + phaseF) * BOB_SPEED) * BOB_AMPL;
            float py = BASE_Y + m.ring * Y_STEP + bob;

            float speedMul = 0.9f + ((m.phase & 255) / 255f) * 0.4f;
            float dir = (((m.phase >> 9) & 1) == 0) ? 1f : -1f;
            if (!delivering) {
                float spinStep = SPIN_BASE * speedMul * dir;
                m.idleSpin = (m.idleSpin + spinStep * dt) % 360f;
            }

            float zeroYaw = computeZeroYaw(px, pz);
            float targetYaw = MathHelper.wrapDegrees(zeroYaw + m.idleSpin * (1f - m.renderFocus));
            float yaw = m.renderYaw;
            if (Float.isNaN(yaw)) yaw = targetYaw;

            float frameDt = dt > 0f ? dt : partialTicks;
            float settleSpeed = Math.max(Math.abs(m.lastSpinStep) * DELIVERY_SPIN_ACCEL, SPIN_BASE * DELIVERY_SPIN_ACCEL);
            float lerpSpeed = delivering
                    ? Math.max(settleSpeed, FOCUS_LERP_SPEED)
                    : (float) MathHelper.clampedLerp(SPIN_LERP_SPEED, FOCUS_LERP_SPEED, m.renderFocus);
            float step = lerpSpeed * (delivering ? frameDt : partialTicks);
            yaw = approachDegrees(yaw, targetYaw, step);
            m.renderYaw = yaw;

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, pz);
            GlStateManager.rotate(yaw, 0, 1, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);

            BlockPos lp = new BlockPos(te.getPos().getX() + px, te.getPos().getY() + py, te.getPos().getZ() + pz);
            int light = te.getWorld().getCombinedLight(lp, 0);
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit,
                    (float)(light & 0xFFFF),
                    (float)((light >> 16) & 0xFFFF)
            );

            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem()
                    .renderItem(renderMirror, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();

            GlStateManager.popMatrix();
        }

        // === 2) ПОДВЕШЕННЫЕ К ВЫБРОСУ ЗЕРКАЛА ===
        List<int[]> pend = te.getPendingEjectVisuals(); // {ring,slot,age,ttl}
        int ttl = te.getEjectHoverTicks();
        for (int[] v : pend) {
            int ring = v[0], slot = v[1], age = v[2];
            float p = MathHelper.clamp((float)age / Math.max(1, ttl), 0f, 1f);

            float base = slot * 60f;
            if (ring == 1 || ring == 3) base += RING_SHIFT_YAW;

            double ang = Math.toRadians(base);
            double px = Math.cos(ang) * RADIUS;
            double pz = Math.sin(ang) * RADIUS;

            float tt = (t + partialTicks);
            float bob = MathHelper.sin(tt * 0.5f) * (BOB_AMPL * 0.35f);
            float py = BASE_Y + ring * Y_STEP + bob;
            float spin = (tt * (SPIN_BASE * 0.25f)) % 360f;

            float a = (p < 0.66f)
                    ? 0.75f
                    : MathHelper.clamp(0.75f * (1f - (p - 0.66f) / 0.34f), 0f, 0.75f);

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, pz);
            GlStateManager.rotate(base + spin, 0, 1, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);

            BlockPos lp = new BlockPos(te.getPos().getX() + px, te.getPos().getY() + py, te.getPos().getZ() + pz);
            int light = te.getWorld().getCombinedLight(lp, 0);
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit,
                    (float)(light & 0xFFFF),
                    (float)((light >> 16) & 0xFFFF)
            );

            GlStateManager.color(1F, 1F, 1F, a);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem()
                    .renderItem(renderMirror, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1F, 1F, 1F, 1F);

            GlStateManager.popMatrix();
        }

        // === 3) ЛЕТЯЩИЕ ПРЕДМЕТЫ ===
        te.clientCullFlying();
        List<TileMirrorManager.FlyingItem> fly = te.getFlying();
        for (TileMirrorManager.FlyingItem f : fly) {
            if (f.stack.isEmpty()) continue;

            float base = f.slot * 60f;
            if (f.ring == 1 || f.ring == 3) base += RING_SHIFT_YAW;
            double ang = Math.toRadians(base);

            // Конечная точка у зеркала
            Vec3d P2 = new Vec3d(
                    Math.cos(ang) * RADIUS,
                    BASE_Y + f.ring * Y_STEP,
                    Math.sin(ang) * RADIUS
            );

            // Стартовая точка над менеджером
            Vec3d P0 = new Vec3d(0.0, BASE_Y + 0.15, 0.0);

            // Горизонтальный радиальный вектор от центра к зеркалу
            // Промежуточная точка на плоскости старта в сторону зеркала
            Vec3d dir = new Vec3d(P2.x - P0.x, 0.0, P2.z - P0.z);
            if (dir.lengthSquared() < 1.0e-6) {
                dir = new Vec3d(0, 0, 1);
            } else {
                dir = dir.normalize();
            }
            Vec3d P1 = P0.add(dir.scale(0.72));
            // Вычисляем контрольную точку квадратичной Безье, чтобы кривая проходила через P1 при t=0.5
            Vec3d control = P1.scale(2.0)
                    .subtract(P0.scale(0.5))
                    .subtract(P2.scale(0.5));

            float tt2 = (t + partialTicks) - f.start;
            float p = MathHelper.clamp(tt2 / (float) f.duration, 0f, 1f);

            // плавный ease-in-out
            float ease = (p < 0.5f)
                    ? (2f * p * p)
                    : (1f - (float) Math.pow(-2f * p + 2f, 2) / 2f);

            // обрезаем хвост (чтоб не влетал внутрь зеркала)
            float cut = 0.88f;
            if (ease >= cut) continue;
            float tail = 0.06f;
            float a = 1f;
            if (ease > cut - tail) a = MathHelper.clamp((cut - ease) / tail, 0f, 1f);

            double tSeg = ease;
            double it = 1.0 - tSeg;
            Vec3d pos = P0.scale(it * it)
                    .add(control.scale(2 * it * tSeg))
                    .add(P2.scale(tSeg * tSeg));

            float wob = (float) Math.sin((f.seed % 1000) * 0.013 + (t + partialTicks) * 0.25) * 6f;
            float spin = ((t + partialTicks) * (1.2f + ((f.seed & 255) / 255f))) % 360f;

            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.x, pos.y, pos.z);
            GlStateManager.rotate(spin, 0, 1, 0);
            GlStateManager.rotate(wob, 0, 0, 1);
            GlStateManager.scale(0.50f, 0.50f, 0.50f);

            BlockPos lp = new BlockPos(
                    te.getPos().getX() + pos.x,
                    te.getPos().getY() + pos.y,
                    te.getPos().getZ() + pos.z
            );
            int light = te.getWorld().getCombinedLight(lp, 0);
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit,
                    (float)(light & 0xFFFF),
                    (float)((light >> 16) & 0xFFFF)
            );

            GlStateManager.color(1F, 1F, 1F, a);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem()
                    .renderItem(f.stack, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1F, 1F, 1F, 1F);

            GlStateManager.popMatrix();
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }


    /**
     * Плавно поворачивает current к target по кратчайшему пути, не более чем на step градусов за вызов.
     */
    private static float approachDegrees(float current, float target, float step) {
        // Разница углов с учётом -180..180
        float delta = MathHelper.wrapDegrees(target - current);

        if (delta > step) {
            delta = step;
        } else if (delta < -step) {
            delta = -step;
        }

        return current + delta;
    }

}
