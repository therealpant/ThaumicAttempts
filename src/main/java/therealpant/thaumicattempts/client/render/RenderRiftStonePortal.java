package therealpant.thaumicattempts.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import thaumcraft.client.lib.UtilsFX;
import therealpant.thaumicattempts.client.model.RiftStonePortalModel;
import therealpant.thaumicattempts.world.block.BlockRiftStonePortal;
import therealpant.thaumicattempts.world.tile.TileRiftStonePortal;

@SideOnly(Side.CLIENT)
public class RenderRiftStonePortal extends GeoBlockRenderer<TileRiftStonePortal> {
    private static final ResourceLocation CULTIST_PORTAL =
            new ResourceLocation("thaumcraft", "textures/misc/cultist_portal.png");
    private static final float PORTAL_CENTER_Y = 1.5F;
    private static final float PORTAL_HALF_HEIGHT = 1.0F;

    private EnumFacing lastFacing = EnumFacing.NORTH;
    private int lastRotationSteps;

    public RenderRiftStonePortal() {
        super(new RiftStonePortalModel());
    }

    @Override
    public void rotateBlock(EnumFacing facing) {
        this.lastFacing = facing == null ? EnumFacing.NORTH : facing;
        GlStateManager.translate(0.0F, 0.0F, 0.0F);
        GlStateManager.rotate(getYaw(this.lastFacing), 0.0F, 1.0F, 0.0F);
    }

    @Override
    public void render(TileRiftStonePortal te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te != null && te.getWorld() != null) {
            IBlockState state = te.getWorld().getBlockState(te.getPos());
            if (state.getBlock() instanceof BlockRiftStonePortal) {
                lastFacing = state.getValue(BlockRiftStonePortal.FACING);
            }
            lastRotationSteps = te.getModelRotationSteps();
        }
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);
        renderCultistPortal(te, x, y, z, partialTicks);
    }

    private void renderCultistPortal(TileRiftStonePortal te, double x, double y, double z, float partialTicks) {
        if (te == null) return;

        float progress = te.getCultistPortalProgress(partialTicks);
        if (progress <= 0.001F) return;

        float halfWidth = PORTAL_HALF_HEIGHT * progress;
        int frame = 15 - (int) ((System.nanoTime() / 50000000L) % 16L);
        float u1 = frame / 16.0F;
        float u2 = u1 + 0.0625F;

        Vec3d bottomLeft = toWorld(x, y, z, 0.0F, PORTAL_CENTER_Y - PORTAL_HALF_HEIGHT, -halfWidth);
        Vec3d topLeft = toWorld(x, y, z, 0.0F, PORTAL_CENTER_Y + PORTAL_HALF_HEIGHT, -halfWidth);
        Vec3d topRight = toWorld(x, y, z, 0.0F, PORTAL_CENTER_Y + PORTAL_HALF_HEIGHT, halfWidth);
        Vec3d bottomRight = toWorld(x, y, z, 0.0F, PORTAL_CENTER_Y - PORTAL_HALF_HEIGHT, halfWidth);

        GlStateManager.pushMatrix();
        try {
            bindTexture(CULTIST_PORTAL);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableCull();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, UtilsFX.VERTEXFORMAT_POS_TEX_CO_LM_NO);
            addPortalVertex(buffer, bottomLeft, u2, 0.0F);
            addPortalVertex(buffer, topLeft, u2, 1.0F);
            addPortalVertex(buffer, topRight, u1, 1.0F);
            addPortalVertex(buffer, bottomRight, u1, 0.0F);
            addPortalVertex(buffer, bottomRight, u1, 0.0F);
            addPortalVertex(buffer, topRight, u1, 1.0F);
            addPortalVertex(buffer, topLeft, u2, 1.0F);
            addPortalVertex(buffer, bottomLeft, u2, 0.0F);
            tessellator.draw();
        } finally {
            GlStateManager.enableCull();
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private Vec3d toWorld(double x, double y, double z, float localX, float localY, float localZ) {
        double yaw = Math.toRadians(getYaw(lastFacing));
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double rx = localX * cos + localZ * sin;
        double rz = localZ * cos - localX * sin;
        return new Vec3d(x + 0.5D + rx, y + localY, z + 0.5D + rz);
    }

    private void addPortalVertex(BufferBuilder buffer, Vec3d pos, float u, float v) {
        buffer.pos(pos.x, pos.y, pos.z)
                .tex(u, v)
                .color(1.0F, 1.0F, 1.0F, 1.0F)
                .lightmap(0, 220)
                .normal(0.0F, 0.0F, -1.0F)
                .endVertex();
    }

    private float getYaw(EnumFacing facing) {
        float yaw;
        switch (facing) {
            case SOUTH:
                yaw = 270.0F;
                break;
            case WEST:
                yaw = 180.0F;
                break;
            case EAST:
                yaw = 0.0F;
                break;
            case NORTH:
            default:
                yaw = 90.0F;
                break;
        }
        return yaw + lastRotationSteps * 45.0F;
    }
}
