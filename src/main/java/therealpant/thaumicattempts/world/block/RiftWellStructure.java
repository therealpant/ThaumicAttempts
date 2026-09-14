package therealpant.thaumicattempts.world.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.init.TABlocks;

public final class RiftWellStructure {
    public static final int RADIUS = 4;
    private static boolean dismantling;

    private RiftWellStructure() {}

    public static boolean canForm(World world, BlockPos center) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos part = center.add(x, 0, z);
                IBlockState state = world.getBlockState(part);
                if (state.getBlock() != TABlocks.RIFT_WELL_PART
                        && !state.getBlock().isReplaceable(world, part)) return false;
            }
        }
        return true;
    }

    public static boolean form(World world, BlockPos center) {
        if (!canForm(world, center)) return false;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (x != 0 || z != 0) {
                    world.setBlockState(center.add(x, 0, z),
                            TABlocks.RIFT_WELL_PART.getDefaultState(), 3);
                }
            }
        }
        return true;
    }

    public static BlockPos findController(World world, BlockPos part) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                BlockPos candidate = part.add(x, 0, z);
                if (world.getBlockState(candidate).getBlock() == TABlocks.RIFT_WELL) return candidate;
            }
        }
        return null;
    }

    public static void dismantle(World world, BlockPos center, boolean dropController) {
        if (dismantling) return;
        dismantling = true;
        try {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (x == 0 && z == 0) continue;
                    BlockPos part = center.add(x, 0, z);
                    if (world.getBlockState(part).getBlock() == TABlocks.RIFT_WELL_PART) {
                        world.setBlockToAir(part);
                    }
                }
            }
            if (world.getBlockState(center).getBlock() == TABlocks.RIFT_WELL) {
                if (dropController) world.destroyBlock(center, true);
                else world.setBlockToAir(center);
            }
        } finally {
            dismantling = false;
        }
    }

    public static boolean isDismantling() {
        return dismantling;
    }
}
