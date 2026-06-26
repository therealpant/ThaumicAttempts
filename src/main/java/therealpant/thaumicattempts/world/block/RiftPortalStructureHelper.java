package therealpant.thaumicattempts.world.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.init.TABlocks;

final class RiftPortalStructureHelper {
    private static boolean restoring;

    private RiftPortalStructureHelper() {
    }

    static boolean isRestoring() {
        return restoring;
    }

    static boolean isCenterPlatform(World world, BlockPos pos) {
        return world != null
                && world.getBlockState(pos).getBlock() == TABlocks.RIFT_PORTAL_PLATFORM
                && world.getBlockState(pos.up()).getBlock() == TABlocks.RIFT_STONE_PORTAL;
    }

    static BlockPos findCenterFromPlatform(World world, BlockPos pos) {
        if (isCenterPlatform(world, pos)) {
            return pos;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = pos.add(dx, 0, dz);
                if (isCenterPlatform(world, candidate)) {
                    return candidate;
                }
            }
        }
        return pos;
    }

    static void restoreFromPlatform(World world, BlockPos pos) {
        restore(world, findCenterFromPlatform(world, pos), pos);
    }

    static void restoreFromPortal(World world, BlockPos pos) {
        restore(world, pos.down(), pos);
    }

    private static void restore(World world, BlockPos center, BlockPos brokenPos) {
        if (world == null || world.isRemote || restoring) {
            return;
        }

        restoring = true;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    restoreBlock(world, center.add(dx, 0, dz), brokenPos, getBaseSourceState(dx, dz));
                }
            }

            restoreBlock(world, center.up(), brokenPos, TABlocks.DARK_JASPER_BRICKS.getDefaultState());
            restoreBlock(world, center.up(2), brokenPos, TABlocks.DARK_JASPER_BRICKS.getDefaultState());
        } finally {
            restoring = false;
        }
    }

    private static IBlockState getBaseSourceState(int dx, int dz) {
        if (dx != 0 && dz != 0) {
            return TABlocks.DARK_JASPER_BRICKS.getDefaultState();
        }
        return TABlocks.POLISHED_DARK_JASPER.getDefaultState();
    }

    private static void restoreBlock(World world, BlockPos pos, BlockPos brokenPos, IBlockState state) {
        if (pos.equals(brokenPos)) {
            return;
        }
        world.setBlockState(pos, state, 3);
    }
}
