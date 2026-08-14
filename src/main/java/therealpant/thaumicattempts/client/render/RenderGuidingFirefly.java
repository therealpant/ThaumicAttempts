package therealpant.thaumicattempts.client.render;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.lib.UtilsFX;
import therealpant.thaumicattempts.world.EntityGuidingFirefly;

/** A compact TC6-wisp-style renderer used by the summoned firefly. */
public final class RenderGuidingFirefly extends Render<EntityGuidingFirefly> {
    public RenderGuidingFirefly(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.0f;
    }

    @Override
    public void doRender(EntityGuidingFirefly entity,
                         double x,
                         double y,
                         double z,
                         float entityYaw,
                         float partialTicks) {
        if (entity.getHealth() <= 0.0f) return;

        double xx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double yy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double zz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        // +15%, +20% or +25% for one, two or three shots per volley.
        float sizeMultiplier = 1.10f + entity.getProjectilesPerAttack() * 0.05f;

        GL11.glPushMatrix();
        GL11.glAlphaFunc(GL11.GL_GREATER, 1.0f / 255.0f);
        GL11.glDepthMask(false);
        bindTexture(ParticleEngine.particleTexture);
        UtilsFX.renderFacingQuad(xx, yy, zz, 64, 64,
                512 + entity.ticksExisted % 16, 0.22f * sizeMultiplier,
                0xFFFFFF, 1.0f, 1, partialTicks);
        UtilsFX.renderFacingQuad(xx, yy, zz, 64, 64,
                320 + entity.ticksExisted % 16, 0.38f * sizeMultiplier,
                0xFFFFFF, 0.25f, 1, partialTicks);
        bindTexture(UtilsFX.nodeTexture);
        UtilsFX.renderFacingQuad(xx, yy, zz, 32, 32,
                800 + entity.ticksExisted % 16, 0.32f * sizeMultiplier,
                entity.getFocusColor(), 0.65f, 1, partialTicks);
        GL11.glDepthMask(true);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityGuidingFirefly entity) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
