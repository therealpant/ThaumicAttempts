package therealpant.thaumicattempts.client.fx;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.client.fx.particles.FXBoreParticles;

/** Thaumcraft item fragment with a straight, deterministic route to its target. */
public class FXDirectInfusionItem extends FXBoreParticles {
    private final double startX;
    private final double startY;
    private final double startZ;
    private final double endX;
    private final double endY;
    private final double endZ;

    public FXDirectInfusionItem(World world, double startX, double startY, double startZ,
                                double endX, double endY, double endZ, ItemStack stack,
                                BlockPos colorPos) {
        super(world, startX, startY, startZ, endX, endY, endZ,
                0.0D, 0.0D, 0.0D, stack);
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
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
        setPosition(
                startX + (endX - startX) * progress,
                startY + (endY - startY) * progress,
                startZ + (endZ - startZ) * progress
        );
    }
}
