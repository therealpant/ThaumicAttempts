package therealpant.thaumicattempts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.net.msg.C2S_AnomalyArchiveInteract;
import therealpant.thaumicattempts.world.block.BlockAnomalyArchive;
import therealpant.thaumicattempts.world.tile.TileAnomalyArchive;

public final class AnomalyArchiveInteractionClient {
    private static ArchiveHit hoveredArchive;

    private AnomalyArchiveInteractionClient() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.currentScreen != null) {
            hoveredArchive = null;
            return;
        }
        hoveredArchive = findHoveredArchive(mc.player, event.getPartialTicks());
        if (hoveredArchive != null) {
            renderOutline(mc, hoveredArchive.box);
        }
    }

    @SubscribeEvent
    public static void onMouse(MouseEvent event) {
        if (event.getButton() != 1 || !event.isButtonstate()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }

        boolean pickaxe = BlockAnomalyArchive.isPickaxe(
                mc.player.getHeldItem(EnumHand.MAIN_HAND));
        boolean thaumometer = !mc.player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()
                && mc.player.getHeldItem(EnumHand.MAIN_HAND).getItem() == ItemsTC.thaumometer;
        if (!pickaxe && !thaumometer) return;

        ArchiveHit hit = findHoveredArchive(mc.player, 1.0F);
        if (hit == null) return;
        ThaumicAttempts.NET.sendToServer(
                new C2S_AnomalyArchiveInteract(hit.pos, thaumometer));
        event.setCanceled(true);
    }

    private static ArchiveHit findHoveredArchive(EntityPlayer player,
                                                  float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d eye = player.getPositionEyes(partialTicks);
        Vec3d look = player.getLook(partialTicks).normalize();
        double reach = mc.playerController == null
                ? 5.0D : mc.playerController.getBlockReachDistance();
        Vec3d end = eye.add(look.scale(reach));

        ArchiveHit nearest = null;
        for (TileEntity tile : player.world.loadedTileEntityList) {
            if (!(tile instanceof TileAnomalyArchive)) continue;
            BlockPos pos = tile.getPos();
            if (!(player.world.getBlockState(pos).getBlock()
                    instanceof BlockAnomalyArchive)) {
                continue;
            }
            RayTraceResult hit =
                    BlockAnomalyArchive.rayTraceInteraction(pos, eye, end);
            if (hit == null) continue;
            double distanceSq = eye.squareDistanceTo(hit.hitVec);
            if (nearest == null || distanceSq < nearest.distanceSq) {
                nearest = new ArchiveHit(
                        pos, BlockAnomalyArchive.getInteractionBox(pos), distanceSq,
                        hit.hitVec);
            }
        }
        if (nearest == null) return null;

        RayTraceResult obstruction = player.world.rayTraceBlocks(
                eye, nearest.hitVec, false, true, false);
        if (obstruction != null
                && obstruction.typeOfHit == RayTraceResult.Type.BLOCK
                && !nearest.pos.equals(obstruction.getBlockPos())
                && eye.squareDistanceTo(obstruction.hitVec) + 1.0E-5D
                < nearest.distanceSq) {
            return null;
        }
        return nearest;
    }

    private static void renderOutline(Minecraft mc, AxisAlignedBB box) {
        double x = mc.getRenderManager().viewerPosX;
        double y = mc.getRenderManager().viewerPosY;
        double z = mc.getRenderManager().viewerPosZ;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        RenderGlobal.drawSelectionBoundingBox(
                box.grow(0.002D).offset(-x, -y, -z),
                0.0F, 0.0F, 0.0F, 0.45F);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glLineWidth(1.0F);
    }

    private static final class ArchiveHit {
        private final BlockPos pos;
        private final AxisAlignedBB box;
        private final double distanceSq;
        private final Vec3d hitVec;

        private ArchiveHit(BlockPos pos, AxisAlignedBB box,
                           double distanceSq, Vec3d hitVec) {
            this.pos = pos;
            this.box = box;
            this.distanceSq = distanceSq;
            this.hitVec = hitVec;
        }
    }
}
