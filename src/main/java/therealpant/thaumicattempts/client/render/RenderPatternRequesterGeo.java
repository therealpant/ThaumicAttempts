package therealpant.thaumicattempts.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
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
import therealpant.thaumicattempts.client.model.PatternRequesterModel;
import therealpant.thaumicattempts.golemnet.block.BlockPatternRequester;
import therealpant.thaumicattempts.golemnet.net.msg.S2CPatternRequesterAnim;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

import java.util.List;

@SideOnly(Side.CLIENT)
public class RenderPatternRequesterGeo extends GeoBlockRenderer<TilePatternRequester> {

    private static final ResourceLocation TEX_EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/pattern_requester_e.png");

    private static final float EMISSIVE_Y_OFFSET = 0.01f;


    public RenderPatternRequesterGeo() {
        super(new PatternRequesterModel());
    }

    private EnumFacing getFacing(TilePatternRequester te) {
        if (te == null || te.getWorld() == null) return EnumFacing.UP;
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (state.getBlock() instanceof BlockPatternRequester) {
            return state.getValue(BlockPatternRequester.FACING);
        }
        return EnumFacing.UP;
    }

    private void applyFacingTransform(EnumFacing facing) {
        switch (facing) {
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
            case UP:
            default:
                break;
        }
    }

    @Override
    public void rotateBlock(EnumFacing facing) {
        GlStateManager.translate(0.0F, 0.5F, 0.0F);
        applyFacingTransform(facing);
        GlStateManager.translate(0.0F, -0.5F, 0.0F);
    }

    private static final float OFFSET_UP = 0.30f + 0.125f;
    private static final float OFFSET_DN = -0.30f + 0.125f;

    private static final float BOB_AMPL = 0.06f;
    private static final float BOB_SPEED = (float) (Math.PI * 2.0 / 60.0);
    private static final float SPIN_BASE = 1.4f;
    private static final float SCALE = 0.55f;

    private final ItemStack renderMirror = new ItemStack(BlocksTC.mirror);

    @Override
    public void render(TilePatternRequester te,
                       double x, double y, double z,
                       float partialTicks,
                       int destroyStage,
                       float alpha) {

        if (te == null || te.getWorld() == null) return;

        float[] prevLight = RenderSafety.captureLightmap();
        try {
            super.render(te, x, y, z, partialTicks, destroyStage, alpha);

            renderEmissiveLayer(te, x, y, z, partialTicks);
            if (RenderSafety.isItemRender()) {
                return;
            }

            renderMirror(te, x, y, z, partialTicks);
            renderFlyingItems(te, x, y, z, partialTicks);
        } finally {
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderMirror(TilePatternRequester te, double x, double y, double z, float partialTicks) {
        long t = te.getWorld().getTotalWorldTime();
        float tt = t + partialTicks;
        float bob = MathHelper.sin(tt * BOB_SPEED) * BOB_AMPL;
        float spin = (tt * SPIN_BASE) % 360f;

        EnumFacing facing = getFacing(te);

        GlStateManager.pushMatrix();
        float[] prevLight = RenderSafety.captureLightmap();
        try {
            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
            applyFacingTransform(facing);

            float yOff = (facing == EnumFacing.DOWN) ? OFFSET_DN : OFFSET_UP;
            GlStateManager.translate(0.0, yOff + bob, 0.0F);
            GlStateManager.rotate(spin, 0, 1, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);

            BlockPos lp = new BlockPos(
                    te.getPos().getX(),
                    te.getPos().getY() + (yOff + bob),
                    te.getPos().getZ()
            );
            int light = te.getWorld().getCombinedLight(lp, 0);
            RenderSafety.setLightmapFromPacked(light);

            RenderSafety.beginItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(renderMirror, ItemCameraTransforms.TransformType.FIXED);
            RenderSafety.endItemLighting();
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(prevLight);
            RenderSafety.resetGlState();
        }
    }

    private void renderFlyingItems(TilePatternRequester te, double x, double y, double z, float partialTicks) {
        te.clientCullFlying();
        List<TilePatternRequester.FlyingItem> flying = te.getFlying();
        if (flying.isEmpty()) return;

        long t = te.getWorld().getTotalWorldTime();
        float tt = t + partialTicks;
        float bob = MathHelper.sin(tt * BOB_SPEED) * BOB_AMPL;
        EnumFacing facing = getFacing(te);
        float mirrorY = ((facing == EnumFacing.DOWN) ? OFFSET_DN : OFFSET_UP) + bob;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
            applyFacingTransform(facing);
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );

            for (TilePatternRequester.FlyingItem f : flying) {
                if (f.stack.isEmpty()) continue;

                float age = (t + partialTicks) - f.start;
                if (age < 0f) continue;

                float p = MathHelper.clamp(age / (float) f.duration, 0f, 1f);
                float ease = smooth(p);
                Vec3d pos = requesterPath(f.mode, ease, mirrorY, f.seed);

                float spin = ((t + partialTicks) * (5.2f + ((f.seed & 127) / 36f))) % 360f;
                float wob = MathHelper.sin((t + partialTicks) * 0.33f + (f.seed & 255) * 0.017f) * 8f;

                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(pos.x, pos.y, pos.z);
                    GlStateManager.rotate(spin, 0f, 1f, 0f);
                    GlStateManager.rotate(wob, 0f, 0f, 1f);
                    GlStateManager.scale(0.42f, 0.42f, 0.42f);

                    float[] prevLight = RenderSafety.captureLightmap();
                    try {
                        int light = te.getWorld().getCombinedLight(te.getPos(), 0);
                        RenderSafety.setLightmapFromPacked(light);
                        RenderSafety.beginItemLighting();
                        Minecraft.getMinecraft().getRenderItem().renderItem(f.stack, ItemCameraTransforms.TransformType.FIXED);
                        RenderSafety.endItemLighting();
                    } finally {
                        RenderSafety.restoreLightmap(prevLight);
                        RenderSafety.resetGlState();
                    }
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.popMatrix();
        }
    }

    private static float smooth(float p) {
        p = MathHelper.clamp(p, 0f, 1f);
        return p * p * (3f - 2f * p);
    }

    private static Vec3d requesterPath(int mode, float p, float mirrorY, long seed) {
        if (mode == S2CPatternRequesterAnim.MODE_BOUNCE) {
            if (p < 0.5f) {
                return spiralPath(p * 2f, mirrorY, false, false, seed);
            }
            return spiralPath((p - 0.5f) * 2f, mirrorY, true, true, seed);
        }

        boolean toBlock = mode != S2CPatternRequesterAnim.MODE_BLOCK_TO_MIRROR;
        return spiralPath(p, mirrorY, toBlock, false, seed);
    }

    private static Vec3d spiralPath(float p, float mirrorY, boolean mirrorToBlock, boolean mirrored, long seed) {
        float q = mirrorToBlock ? p : 1f - p;
        q = smooth(q);

        double base = ((seed & 1023L) / 1023.0) * Math.PI * 2.0;
        double dir = (((seed >> 10) & 1L) == 0L) ? 1.0 : -1.0;
        if (mirrored) dir = -dir;

        double theta = base + dir * (Math.PI * 0.5) * q;
        double radius = 0.34 * Math.sin(Math.PI * q);
        double x = Math.cos(theta) * radius;
        double z = Math.sin(theta) * radius;

        double targetY = -1.125;
        double y = mirrorY + (targetY - mirrorY) * q;
        double latePeak = Math.sin(Math.PI * Math.pow(q, 0.72));
        y += 0.24 * Math.max(0.0, latePeak);

        return new Vec3d(x, y, z);
    }

    private void renderEmissiveLayer(TilePatternRequester te,
                                     double x, double y, double z,
                                     float partialTicks) {
        GeoModel model = this.getGeoModelProvider().getModel(
                this.getGeoModelProvider().getModelLocation(te)
        );

        float[] prevLight = RenderSafety.captureLightmap();

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y + EMISSIVE_Y_OFFSET, z + 0.5);

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
}
