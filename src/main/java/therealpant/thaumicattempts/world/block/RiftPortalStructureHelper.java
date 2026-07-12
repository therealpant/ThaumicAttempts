package therealpant.thaumicattempts.world.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.init.TABlocks;

final class RiftPortalStructureHelper {
    private RiftPortalStructureHelper() {
    }

    static boolean isRestoring() {
        return MultiblockBreakHelper.isRestoring();
    }

    static boolean isCenterPlatform(World world, BlockPos pos) {
        return world != null
                && world.getBlockState(pos).getBlock() == TABlocks.RIFT_PORTAL_PLATFORM
                && (world.getBlockState(pos.up()).getBlock() == TABlocks.RIFT_STONE_PORTAL
                || world.getBlockState(pos.up()).getBlock() == TABlocks.RIFT_STONE_ALTAR);
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
        BlockPos center = findCenterFromPlatform(world, pos);
        boolean altar = world.getBlockState(center.up()).getBlock() == TABlocks.RIFT_STONE_ALTAR;
        MultiblockBreakHelper.restoreFromBlueprint(
                world,
                pos,
                altar ? TAMultiblockTriggers.TRANSMUTATION_ALTAR_MB_ID : TAMultiblockTriggers.RIFT_PORTAL_MB_ID,
                center
        );
    }

    static void restoreFromAltar(World world, BlockPos pos) {
        MultiblockBreakHelper.restoreFromBlueprint(
                world, pos, TAMultiblockTriggers.TRANSMUTATION_ALTAR_MB_ID, pos.down());
    }

    static void restoreFromPortal(World world, BlockPos pos) {
        MultiblockBreakHelper.restoreFromBlueprint(
                world,
                pos,
                TAMultiblockTriggers.RIFT_PORTAL_MB_ID,
                pos.down()
        );
    }
}
