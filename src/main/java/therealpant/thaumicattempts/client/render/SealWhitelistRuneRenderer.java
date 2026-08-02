package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.seals.SealHandler;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.link.BellLinkingHandler;

import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID, value = Side.CLIENT)
public final class SealWhitelistRuneRenderer {

    private static final ResourceLocation RUNES =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/seals/rune.png");
    private static final float RUNE_SIZE = 0.115F;
    private static final double BOB_AMPLITUDE = 0.055D;
    private static final double BOB_SPEED = 0.085D;

    private SealWhitelistRuneRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) return;

        ItemStack bell = getLinkedBell(player);
        if (bell.isEmpty()) return;

        SealPos sealPos = BellLinkingHandler.getLinkedSealForRender(bell);
        if (sealPos == null) return;

        int dimension = BellLinkingHandler.getLinkedSealDimensionForRender(
                bell, player.dimension);
        if (dimension != player.dimension
                || SealHandler.getSealEntity(dimension, sealPos) == null) {
            return;
        }

        int rune = BellLinkingHandler.getLinkedRuneForRender(bell);
        Set<UUID> golemIds = BellLinkingHandler.getLinkedGolemsForRender(bell);
        RenderManager renderManager = mc.getRenderManager();
        float partialTicks = event.getPartialTicks();
        double animationTime = player.ticksExisted + partialTicks;

        RenderSafety.GlStateSnapshot state = RenderSafety.captureGlState();
        float[] lightmap = RenderSafety.captureLightmap();
        GlStateManager.pushMatrix();
        try {
            mc.getTextureManager().bindTexture(RUNES);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSafety.setFullBrightLightmap();

            EnumFacing face = sealPos.face;
            renderRune(
                    renderManager,
                    sealPos.pos.getX() + 0.5D + face.getXOffset() * 0.68D,
                    sealPos.pos.getY() + 1.25D
                            + Math.max(0, face.getYOffset()) * 0.35D
                            + getBobOffset(animationTime, sealPos.hashCode()),
                    sealPos.pos.getZ() + 0.5D + face.getZOffset() * 0.68D,
                    rune,
                    mc.gameSettings.thirdPersonView == 2);

            if (!golemIds.isEmpty()) {
                for (Entity entity : mc.world.loadedEntityList) {
                    if (!(entity instanceof EntityThaumcraftGolem)
                            || !golemIds.contains(entity.getUniqueID())
                            || entity.isDead) {
                        continue;
                    }

                    double x = entity.prevPosX
                            + (entity.posX - entity.prevPosX) * partialTicks;
                    double y = entity.prevPosY
                            + (entity.posY - entity.prevPosY) * partialTicks
                            + entity.height + 0.38D
                            + getBobOffset(animationTime, entity.getUniqueID().hashCode());
                    double z = entity.prevPosZ
                            + (entity.posZ - entity.prevPosZ) * partialTicks;
                    renderRune(
                            renderManager, x, y, z, rune,
                            mc.gameSettings.thirdPersonView == 2);
                }
            }
        } finally {
            GlStateManager.popMatrix();
            RenderSafety.restoreLightmap(lightmap);
            RenderSafety.restoreGlState(state);
        }
    }

    private static double getBobOffset(double animationTime, int phaseSeed) {
        double phase = Math.floorMod(phaseSeed, 6283) / 1000.0D;
        return Math.sin(animationTime * BOB_SPEED + phase) * BOB_AMPLITUDE;
    }

    private static ItemStack getLinkedBell(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (BellLinkingHandler.isStorageBellForRender(main)
                && BellLinkingHandler.getLinkedSealForRender(main) != null) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        if (BellLinkingHandler.isStorageBellForRender(off)
                && BellLinkingHandler.getLinkedSealForRender(off) != null) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static void renderRune(RenderManager renderManager,
                                   double worldX, double worldY, double worldZ,
                                   int runeIndex, boolean reversePitch) {
        int index = Math.floorMod(runeIndex, 8);
        int column = index & 3;
        int row = index >> 2;
        double u0 = column * 0.25D;
        double u1 = u0 + 0.25D;
        double v0 = row * 0.25D;
        double v1 = v0 + 0.25D;

        GlStateManager.pushMatrix();
        GlStateManager.translate(
                worldX - renderManager.viewerPosX,
                worldY - renderManager.viewerPosY,
                worldZ - renderManager.viewerPosZ);
        GlStateManager.rotate(
                -renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(
                renderManager.playerViewX * (reversePitch ? -1.0F : 1.0F),
                1.0F, 0.0F, 0.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-RUNE_SIZE, -RUNE_SIZE, 0.0D).tex(u0, v1).endVertex();
        buffer.pos(RUNE_SIZE, -RUNE_SIZE, 0.0D).tex(u1, v1).endVertex();
        buffer.pos(RUNE_SIZE, RUNE_SIZE, 0.0D).tex(u1, v0).endVertex();
        buffer.pos(-RUNE_SIZE, RUNE_SIZE, 0.0D).tex(u0, v0).endVertex();
        tessellator.draw();
        GlStateManager.popMatrix();
    }
}
