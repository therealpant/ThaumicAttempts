package therealpant.thaumicattempts.client.fx;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.client.fx.particles.FXBoreParticles;

/** Item fragment travelling along an upward arc, then vertically down. */
public class FXPrismInfusionItem extends FXBoreParticles {
    private static final double ARC_PORTION = 0.72D;
    private final double startX, startY, startZ;
    private final double waypointX, waypointY, waypointZ;
    private final double endX, endY, endZ;
    private final double controlX, controlY, controlZ;

    public FXPrismInfusionItem(World world, double startX, double startY, double startZ,
                               double waypointX, double waypointY, double waypointZ,
                               double endX, double endY, double endZ, ItemStack stack,
                               BlockPos colorPos) {
        super(world, startX, startY, startZ, endX, endY, endZ,
                0.0D, 0.0D, 0.0D, stack);
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.waypointX = waypointX;
        this.waypointY = waypointY;
        this.waypointZ = waypointZ;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.controlX = (startX + waypointX) * 0.5D;
        this.controlY = Math.max(startY, waypointY) + 7.35D;
        this.controlZ = (startZ + waypointZ) * 0.5D;
        getObjectColor(colorPos);
        setAlphaF(0.3F);
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (++particleAge >= particleMaxAge) {
            setPosition(endX, endY, endZ);
            setExpired();
            return;
        }

        double progress = particleAge / (double) particleMaxAge;
        if (progress <= ARC_PORTION) {
            double t = progress / ARC_PORTION;
            double inverse = 1.0D - t;
            setPosition(
                    inverse * inverse * startX + 2.0D * inverse * t * controlX + t * t * waypointX,
                    inverse * inverse * startY + 2.0D * inverse * t * controlY + t * t * waypointY,
                    inverse * inverse * startZ + 2.0D * inverse * t * controlZ + t * t * waypointZ
            );
        } else {
            double t = (progress - ARC_PORTION) / (1.0D - ARC_PORTION);
            setPosition(
                    waypointX + (endX - waypointX) * t,
                    waypointY + (endY - waypointY) * t,
                    waypointZ + (endZ - waypointZ) * t
            );
        }
    }
}
