package therealpant.thaumicattempts.world.block;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Inner faces of the lower rim in rift_well.geo.json, in model pixels. */
public final class RiftWellLiquidSurface {
    public static final double HEIGHT = 10.0D / 16.0D;
    public static final List<AxisAlignedBB> REGIONS = Collections.unmodifiableList(Arrays.asList(
            region(-46, -46, 46, 46),
            region(-14, -60, 14, -46),
            region(-14, 46, 14, 60),
            region(-60, -14, -46, 14),
            // bone103 protrudes one pixel into this arm of the basin.
            region(46, -13, 60, 14),
            region(51, -14, 60, -13)));

    private RiftWellLiquidSurface() {}

    private static AxisAlignedBB region(int minX, int minZ, int maxX, int maxZ) {
        return new AxisAlignedBB(0.5D + minX / 16.0D, HEIGHT, 0.5D + minZ / 16.0D,
                0.5D + maxX / 16.0D, HEIGHT, 0.5D + maxZ / 16.0D);
    }

    public static Vec3d randomPoint(Random random) {
        // Rejection sampling keeps the particle density uniform over the whole basin.
        while (true) {
            double x = 0.5D + (random.nextDouble() * 120.0D - 60.0D) / 16.0D;
            double z = 0.5D + (random.nextDouble() * 120.0D - 60.0D) / 16.0D;
            if (contains(x, z)) {
                return new Vec3d(x, HEIGHT + 0.02D, z);
            }
        }
    }

    public static boolean contains(double x, double z) {
        for (AxisAlignedBB region : REGIONS) {
            if (x >= region.minX && x <= region.maxX
                    && z >= region.minZ && z <= region.maxZ) return true;
        }
        return false;
    }
}
