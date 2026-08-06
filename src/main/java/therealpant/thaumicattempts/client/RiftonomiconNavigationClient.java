package therealpant.thaumicattempts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.items.ItemPortalRune;
import therealpant.thaumicattempts.net.msg.C2S_RiftonomiconWispClick;
import therealpant.thaumicattempts.net.msg.C2S_RiftonomiconAlchemyClick;
import therealpant.thaumicattempts.net.msg.C2S_RiftonomiconCreationClick;
import therealpant.thaumicattempts.world.block.BlockRiftonomicon;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;

import java.util.ArrayList;
import java.util.List;

public final class RiftonomiconNavigationClient {
    private static final int WISPS_PER_LEVEL = 6;
    private static final double WISP_RING_RADIUS = 2.1D;
    private static final double WISP_FIRST_LEVEL_Y = 1.5D + 12.0D / 16.0D;
    private static final double WISP_LEVEL_STEP = 0.64D;
    private static final double WISP_ODD_LEVEL_YAW = 30.0D;
    public static final int WISP_MINIMUM_BLOCK_LIGHT = 10;
    public static final double CREATION_SLOT_LOWERING = 8.0D / 16.0D;
    private static final double WISP_PICK_RADIUS_SQ = 0.22D;
    private static final double MAX_LOOK_DISTANCE = 8.0D;
    private static final double[][] WISP_OFFSETS = createWispOffsets();
    public static final double[][] ALCHEMY_PEARL_OFFSETS = {
            // Midpoints of the paired construction bones at portal animation t=15s:
            // 15+16 over 7, 9+10 over 3, 11+12 over 5, 13+14 over 6.
            { 32.5D / 16.0D, 18.35D / 16.0D, 0.0D },
            { 0.0D, 20.99D / 16.0D, -32.5D / 16.0D },
            { 0.0D, 16.13D / 16.0D, 32.5D / 16.0D },
            { -32.5D / 16.0D, 18.39D / 16.0D, 0.0D }
    };
    private static final double[][][] ALCHEMY_CONSTRUCTION_OFFSETS = {
            { { 32.5D / 16.0D, 18.24D / 16.0D, 8.5D / 16.0D },
              { 32.5D / 16.0D, 18.47D / 16.0D, -8.5D / 16.0D },
              { 32.5D / 16.0D, 10.87D / 16.0D, 0.0D } },
            { { -7.5D / 16.0D, 20.74D / 16.0D, -32.5D / 16.0D },
              { 7.5D / 16.0D, 21.24D / 16.0D, -32.5D / 16.0D },
              { 0.0D, 10.74D / 16.0D, -32.5D / 16.0D } },
            { { -8.5D / 16.0D, 15.76D / 16.0D, 32.5D / 16.0D },
              { 8.5D / 16.0D, 16.50D / 16.0D, 32.5D / 16.0D },
              { 0.0D, 9.87D / 16.0D, 32.5D / 16.0D } },
            { { -32.5D / 16.0D, 18.00D / 16.0D, 7.5D / 16.0D },
              { -32.5D / 16.0D, 18.78D / 16.0D, -7.5D / 16.0D },
              { -32.5D / 16.0D, 6.40D / 16.0D, 0.0D } }
    };

    private static HoveredWisp hoveredWisp;
    private static HoveredAlchemy hoveredAlchemy;
    private static HoveredCreation hoveredCreation;

    private RiftonomiconNavigationClient() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        ActiveNavigation active = findActiveNavigation(mc.player);
        float partialTicks = event.getPartialTicks();
        if (active != null) {
            List<WispView> wisps = buildWisps(active);
            hoveredWisp = findHoveredWisp(mc.player, partialTicks, active.tile.getPos(), wisps);
            renderHoveredLabel(mc, partialTicks, wisps);
        } else {
            hoveredWisp = null;
        }
        hoveredAlchemy = findHoveredAlchemy(mc.player, partialTicks);
        hoveredCreation = findHoveredCreation(mc.player, partialTicks);
    }

    @SubscribeEvent
    public static void onMouse(MouseEvent event) {
        if (event.getButton() != 1 || !event.isButtonstate()) return;
        if (hoveredCreation != null) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            ItemStack held = player == null ? ItemStack.EMPTY : player.getHeldItemMainhand();
            // The virtual Creation slot surrounds the real impetus port. Do not
            // swallow the linker's click: TA Augmentation must receive it and
            // bind its channel to the physical TileRiftonomiconImpetusPort.
            if (isImpetusLinker(held)) return;
            ThaumicAttempts.NET.sendToServer(new C2S_RiftonomiconCreationClick(
                    hoveredCreation.tilePos, hoveredCreation.slot));
            event.setCanceled(true);
            return;
        }
        if (hoveredAlchemy != null) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            ItemStack held = player == null ? ItemStack.EMPTY : player.getHeldItemMainhand();
            boolean extract = player != null && player.isSneaking();
            if (!extract && (held.isEmpty() || held.getItem() != ModBlocksItems.ALCHEMICAL_CORE)) return;
            ThaumicAttempts.NET.sendToServer(new C2S_RiftonomiconAlchemyClick(
                    hoveredAlchemy.tilePos, hoveredAlchemy.slot, extract));
            event.setCanceled(true);
            return;
        }
        if (hoveredWisp == null) return;
        EntityPlayer player = Minecraft.getMinecraft().player;
        boolean extract = player != null && player.isSneaking();
        if (extract && !player.getHeldItemMainhand().isEmpty()) return;
        ThaumicAttempts.NET.sendToServer(new C2S_RiftonomiconWispClick(hoveredWisp.tilePos, hoveredWisp.slot, extract));
        event.setCanceled(true);
    }

    private static boolean isImpetusLinker(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) return false;
        return "thaumicaugmentation".equals(stack.getItem().getRegistryName().getNamespace())
                && "impetus_linker".equals(stack.getItem().getRegistryName().getPath());
    }

    private static HoveredAlchemy findHoveredAlchemy(EntityPlayer player, float partialTicks) {
        Vec3d eye = player.getPositionEyes(partialTicks);
        Vec3d look = player.getLook(partialTicks).normalize();
        HoveredAlchemy result = null;
        double best = Double.MAX_VALUE;
        for (TileEntity raw : player.world.loadedTileEntityList) {
            if (!(raw instanceof TileRiftonomicon)) continue;
            TileRiftonomicon tile = (TileRiftonomicon) raw;
            if (!tile.hasAlchemicalProtocol()) continue;
            for (int slot = 0; slot < ALCHEMY_CONSTRUCTION_OFFSETS.length; slot++) {
                for (double[] off : ALCHEMY_CONSTRUCTION_OFFSETS[slot]) {
                    Vec3d point = new Vec3d(tile.getPos()).add(
                            new Vec3d(0.5D + off[0], off[1], 0.5D + off[2]));
                    Vec3d delta = point.subtract(eye);
                    double along = delta.dotProduct(look);
                    if (along < 0.0D || along > MAX_LOOK_DISTANCE) continue;
                    if (eye.add(look.scale(along)).squareDistanceTo(point) <= 0.16D && along < best) {
                        best = along;
                        result = new HoveredAlchemy(tile.getPos(), slot);
                    }
                }
            }
        }
        return result;
    }

    private static HoveredCreation findHoveredCreation(EntityPlayer player, float partialTicks) {
        Vec3d eye = player.getPositionEyes(partialTicks);
        Vec3d look = player.getLook(partialTicks).normalize();
        HoveredCreation result = null;
        double best = Double.MAX_VALUE;
        for (TileEntity raw : player.world.loadedTileEntityList) {
            if (!(raw instanceof TileRiftonomicon)) continue;
            TileRiftonomicon tile = (TileRiftonomicon) raw;
            if (!tile.hasCreationProtocol()) continue;

            Vec3d center = new Vec3d(tile.getPos()).add(new Vec3d(0.5D, -0.42D, 0.5D));
            double centerAlong = center.subtract(eye).dotProduct(look);
            if (centerAlong >= 0.0D && centerAlong <= MAX_LOOK_DISTANCE
                    && eye.add(look.scale(centerAlong)).squareDistanceTo(center) <= 0.20D) {
                best = centerAlong;
                result = new HoveredCreation(tile.getPos(), -1);
            }

            int limit = tile.getCreationStationLimit();
            for (int slot = 0; slot < limit; slot++) {
                for (double[] off : ALCHEMY_CONSTRUCTION_OFFSETS[slot]) {
                    Vec3d point = new Vec3d(tile.getPos()).add(
                            new Vec3d(0.5D + off[0], off[1] - CREATION_SLOT_LOWERING, 0.5D + off[2]));
                    Vec3d delta = point.subtract(eye);
                    double along = delta.dotProduct(look);
                    if (along < 0.0D || along > MAX_LOOK_DISTANCE) continue;
                    if (eye.add(look.scale(along)).squareDistanceTo(point) <= 0.16D && along < best) {
                        best = along;
                        result = new HoveredCreation(tile.getPos(), slot);
                    }
                }
            }
        }
        return result;
    }

    private static ActiveNavigation findActiveNavigation(EntityPlayer player) {
        TileRiftonomicon nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (TileEntity tile : player.world.loadedTileEntityList) {
            if (!(tile instanceof TileRiftonomicon)) continue;
            TileRiftonomicon riftonomicon = (TileRiftonomicon) tile;
            if (!riftonomicon.hasNavigationProtocol()) continue;

            double distance = player.getDistanceSqToCenter(riftonomicon.getPos());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = riftonomicon;
            }
        }

        return nearest == null ? null : new ActiveNavigation(nearest);
    }

    private static List<WispView> buildWisps(ActiveNavigation active) {
        List<WispView> wisps = new ArrayList<>();
        BlockPos base = active.tile.getPos().down(BlockRiftonomicon.CORE_Y_OFFSET);
        Vec3d center = new Vec3d(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);

        int limit = active.tile.getNavigationRuneSlotLimit();
        for (int slot = 0; slot < limit; slot++) {
            ItemStack rune = active.tile.getNavigationRuneInSlot(slot);
            ItemPortalRune.PortalPoint target = active.tile.getNavigationRuneTarget(slot);
            if (rune.isEmpty() || target == null) continue;

            double[] offset = WISP_OFFSETS[slot];
            Vec3d pos = center.add(new Vec3d(offset[0], offset[1], offset[2]));
            wisps.add(new WispView(active.tile.getPos(), slot, rune, target, pos, getStoneYaw(offset)));
        }
        return wisps;
    }

    private static double[][] createWispOffsets() {
        double[][] offsets = new double[TileRiftonomicon.MAX_NAVIGATION_RUNE_SLOT_COUNT][3];
        for (int slot = 0; slot < offsets.length; slot++) {
            int level = slot / WISPS_PER_LEVEL;
            int position = slot % WISPS_PER_LEVEL;
            double yaw = position * 60.0D;
            if ((level & 1) != 0) yaw += WISP_ODD_LEVEL_YAW;
            double angle = Math.toRadians(yaw);
            offsets[slot][0] = Math.cos(angle) * WISP_RING_RADIUS;
            offsets[slot][1] = WISP_FIRST_LEVEL_Y + level * WISP_LEVEL_STEP;
            offsets[slot][2] = Math.sin(angle) * WISP_RING_RADIUS;
        }
        return offsets;
    }

    public static double[] getStoneOffset(int slot) {
        return WISP_OFFSETS[slot];
    }

    public static float getStoneYaw(double[] offset) {
        double radialAngle = Math.toDegrees(Math.atan2(offset[2], offset[0]));
        return (float) (180.0D - radialAngle);
    }

    private static HoveredWisp findHoveredWisp(EntityPlayer player, float partialTicks, BlockPos tilePos, List<WispView> wisps) {
        Vec3d eye = player.getPositionEyes(partialTicks);
        Vec3d look = player.getLook(partialTicks).normalize();
        HoveredWisp hovered = null;
        double bestDistance = Double.MAX_VALUE;

        for (WispView wisp : wisps) {
            Vec3d toWisp = wisp.pos.subtract(eye);
            double rayDistance = toWisp.dotProduct(look);
            if (rayDistance < 0.0D || rayDistance > MAX_LOOK_DISTANCE) continue;

            Vec3d closest = eye.add(look.scale(rayDistance));
            double distanceSq = closest.squareDistanceTo(wisp.pos);
            if (distanceSq <= WISP_PICK_RADIUS_SQ && rayDistance < bestDistance) {
                bestDistance = rayDistance;
                hovered = new HoveredWisp(tilePos, wisp.slot);
            }
        }
        return hovered;
    }

    private static void renderHoveredLabel(Minecraft mc, float partialTicks, List<WispView> wisps) {
        if (hoveredWisp == null) return;
        for (WispView wisp : wisps) {
            if (wisp.slot != hoveredWisp.slot || !wisp.tilePos.equals(hoveredWisp.tilePos)) continue;
            renderLabel(mc, partialTicks, wisp);
            return;
        }
    }

    private static void renderLabel(Minecraft mc, float partialTicks, WispView wisp) {
        List<String> lines = new ArrayList<>();
        if (wisp.rune.hasDisplayName()) {
            lines.add(wisp.rune.getDisplayName());
        } else {
            lines.add(wisp.target.worldName);
            lines.add(wisp.target.pos.getX() + ", " + wisp.target.pos.getY() + ", " + wisp.target.pos.getZ());
        }

        FontRenderer font = mc.fontRenderer;
        double x = wisp.pos.x - mc.getRenderManager().viewerPosX;
        double y = wisp.pos.y + 0.55D - mc.getRenderManager().viewerPosY;
        double z = wisp.pos.z - mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.018F, -0.018F, 0.018F);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.getStringWidth(line));
        }
        int totalHeight = lines.size() * 10;
        drawLabelBackground(maxWidth, totalHeight);

        int yOffset = -totalHeight / 2 + 1;
        for (String line : lines) {
            font.drawString(line, -font.getStringWidth(line) / 2, yOffset, 0xE6F7FF);
            yOffset += 10;
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static void drawLabelBackground(int width, int height) {
        int halfWidth = width / 2 + 3;
        int top = -height / 2 - 2;
        int bottom = height / 2 + 2;

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(-halfWidth, bottom, 0.0D).color(0.0F, 0.0F, 0.0F, 0.35F).endVertex();
        buffer.pos(halfWidth, bottom, 0.0D).color(0.0F, 0.0F, 0.0F, 0.35F).endVertex();
        buffer.pos(halfWidth, top, 0.0D).color(0.0F, 0.0F, 0.0F, 0.35F).endVertex();
        buffer.pos(-halfWidth, top, 0.0D).color(0.0F, 0.0F, 0.0F, 0.35F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private static final class ActiveNavigation {
        private final TileRiftonomicon tile;

        private ActiveNavigation(TileRiftonomicon tile) {
            this.tile = tile;
        }
    }

    private static final class WispView {
        private final BlockPos tilePos;
        private final int slot;
        private final ItemStack rune;
        private final ItemPortalRune.PortalPoint target;
        private final Vec3d pos;
        private final float yaw;

        private WispView(BlockPos tilePos, int slot, ItemStack rune, ItemPortalRune.PortalPoint target,
                         Vec3d pos, float yaw) {
            this.tilePos = tilePos;
            this.slot = slot;
            this.rune = rune;
            this.target = target;
            this.pos = pos;
            this.yaw = yaw;
        }
    }

    private static final class HoveredWisp {
        private final BlockPos tilePos;
        private final int slot;

        private HoveredWisp(BlockPos tilePos, int slot) {
            this.tilePos = tilePos;
            this.slot = slot;
        }
    }

    private static final class HoveredAlchemy {
        private final BlockPos tilePos;
        private final int slot;
        private HoveredAlchemy(BlockPos tilePos, int slot) { this.tilePos = tilePos; this.slot = slot; }
    }

    private static final class HoveredCreation {
        private final BlockPos tilePos;
        private final int slot;
        private HoveredCreation(BlockPos tilePos, int slot) { this.tilePos = tilePos; this.slot = slot; }
    }
}
