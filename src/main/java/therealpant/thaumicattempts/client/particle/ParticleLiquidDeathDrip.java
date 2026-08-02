package therealpant.thaumicattempts.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ParticleLiquidDeathDrip extends Particle {
    private static final float HALF_WIDTH = 0.040F;
    private static final float HALF_HEIGHT = 0.080F;
    private final double targetY;
    private int formingTicks = 5;
    private int impactTicks;
    private boolean impacted;

    public ParticleLiquidDeathDrip(World world, double x, double y, double z,
                                   double targetY, TextureAtlasSprite texture) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.targetY = targetY;
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.particleGravity = 0.045F;
        this.particleMaxAge = 100;
        this.particleScale = 0.35F;
        this.setSize(0.04F, 0.04F);
        this.setAlphaF(1.0F);
        this.setRBGColorF(0.72F, 0.22F, 0.95F);
        this.setParticleTexture(texture);
    }

    @Override
    public int getBrightnessForRender(float partialTick) {
        return 15728880;
    }

    @Override
    public int getFXLayer() {
        return 1;
    }

    @Override
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ,
                               float rotationXY, float rotationXZ) {
        float minU = this.particleTexture.getMinU();
        float maxU = this.particleTexture.getMaxU();
        float minV = this.particleTexture.getMinV();
        float maxV = this.particleTexture.getMaxV();
        float halfWidth = HALF_WIDTH * this.particleScale;
        float halfHeight = HALF_HEIGHT * this.particleScale;
        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - interpPosZ);
        int brightness = this.getBrightnessForRender(partialTicks);
        int lightU = brightness >> 16 & 65535;
        int lightV = brightness & 65535;

        addVertex(buffer, x - rotationX * halfWidth - rotationXY * halfHeight,
                y - rotationZ * halfHeight,
                z - rotationYZ * halfWidth - rotationXZ * halfHeight,
                maxU, maxV, lightU, lightV);
        addVertex(buffer, x - rotationX * halfWidth + rotationXY * halfHeight,
                y + rotationZ * halfHeight,
                z - rotationYZ * halfWidth + rotationXZ * halfHeight,
                maxU, minV, lightU, lightV);
        addVertex(buffer, x + rotationX * halfWidth + rotationXY * halfHeight,
                y + rotationZ * halfHeight,
                z + rotationYZ * halfWidth + rotationXZ * halfHeight,
                minU, minV, lightU, lightV);
        addVertex(buffer, x + rotationX * halfWidth - rotationXY * halfHeight,
                y - rotationZ * halfHeight,
                z + rotationYZ * halfWidth - rotationXZ * halfHeight,
                minU, maxV, lightU, lightV);
    }

    private void addVertex(BufferBuilder buffer, float x, float y, float z,
                           float u, float v, int lightU, int lightV) {
        buffer.pos(x, y, z)
                .tex(u, v)
                .color(this.particleRed, this.particleGreen,
                        this.particleBlue, this.particleAlpha)
                .lightmap(lightU, lightV)
                .endVertex();
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (impacted) {
            impactTicks++;
            this.particleScale = 0.48F + impactTicks * 0.035F;
            this.particleAlpha = Math.max(0.0F, 1.0F - impactTicks / 6.0F);
            if (impactTicks >= 6) {
                this.setExpired();
            }
            return;
        }

        if (++this.particleAge >= this.particleMaxAge) {
            this.setExpired();
            return;
        }

        if (formingTicks > 0) {
            formingTicks--;
            this.particleScale = 0.35F + (5 - formingTicks) * 0.08F;
            return;
        }

        this.particleScale = 0.58F;
        this.motionY -= this.particleGravity;
        this.move(0.0D, this.motionY, 0.0D);
        this.motionY *= 0.98D;

        if (this.posY <= targetY) {
            this.posY = targetY;
            this.motionY = 0.0D;
            this.impacted = true;
            this.particleScale = 0.70F;
        }
    }
}
