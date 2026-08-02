package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib3.geo.exception.GeoModelException;
import software.bernie.geckolib3.renderers.geo.GeoItemRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.AnomalyCompassModel;
import therealpant.thaumicattempts.items.ItemAnomalyCompass;

import javax.annotation.Nullable;
import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;
import java.nio.FloatBuffer;

public class RenderAnomalyCompass extends GeoItemRenderer<ItemAnomalyCompass> {

    private final AnomalyCompassModel compassModel;
    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
    private boolean missingModelReported;

    public RenderAnomalyCompass() {
        this(new AnomalyCompassModel());
    }

    private RenderAnomalyCompass(AnomalyCompassModel model) {
        super(model);
        this.compassModel = model;
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        compassModel.setPointerDirection(resolveLocalDirection(stack, partialTicks));
        try {
            super.renderByItem(stack, partialTicks);
        } catch (GeoModelException exception) {
            if (!missingModelReported) {
                missingModelReported = true;
                ThaumicAttempts.LOGGER.error(
                        "Unable to render anomaly compass: its GeckoLib model could not be loaded.",
                        exception
                );
            }
        }
    }

    @Nullable
    private Vector3f resolveLocalDirection(ItemStack stack, float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) return null;

        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) return null;

        BlockPos target = ItemAnomalyCompass.getTarget(stack, minecraft.world.provider.getDimension());
        if (target == null) return null;

        Vec3d cameraPosition;
        if (camera instanceof EntityLivingBase) {
            cameraPosition = ((EntityLivingBase) camera).getPositionEyes(partialTicks);
        } else {
            cameraPosition = new Vec3d(
                    camera.prevPosX + (camera.posX - camera.prevPosX) * partialTicks,
                    camera.prevPosY + (camera.posY - camera.prevPosY) * partialTicks,
                    camera.prevPosZ + (camera.posZ - camera.prevPosZ) * partialTicks
            );
        }

        Vec3d worldDirection = new Vec3d(
                target.getX() + 0.5D - cameraPosition.x,
                target.getY() + 0.5D - cameraPosition.y,
                target.getZ() + 0.5D - cameraPosition.z
        );
        if (worldDirection.lengthSquared() < 1.0E-8D) return null;
        worldDirection = worldDirection.normalize();

        Vec3d forward = camera.getLook(partialTicks).normalize();
        Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
        if (right.lengthSquared() < 1.0E-8D) {
            double yaw = Math.toRadians(camera.rotationYaw);
            right = new Vec3d(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(forward).normalize();

        Vector3f eyeDirection = new Vector3f(
                (float) worldDirection.dotProduct(right),
                (float) worldDirection.dotProduct(up),
                (float) -worldDirection.dotProduct(forward)
        );

        modelViewBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        Matrix3f itemToEye = new Matrix3f(
                modelViewBuffer.get(0), modelViewBuffer.get(4), modelViewBuffer.get(8),
                modelViewBuffer.get(1), modelViewBuffer.get(5), modelViewBuffer.get(9),
                modelViewBuffer.get(2), modelViewBuffer.get(6), modelViewBuffer.get(10)
        );

        try {
            itemToEye.invert();
            itemToEye.transform(eyeDirection);
        } catch (RuntimeException ignored) {
            return null;
        }

        if (eyeDirection.lengthSquared() < 1.0E-6F) return null;
        eyeDirection.normalize();
        return eyeDirection;
    }
}
