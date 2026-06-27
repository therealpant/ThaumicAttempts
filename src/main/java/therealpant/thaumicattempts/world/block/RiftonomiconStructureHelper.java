package therealpant.thaumicattempts.world.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.init.TABlocks;

public final class RiftonomiconStructureHelper {
    private static boolean restoring;

    private RiftonomiconStructureHelper() {
    }

    static boolean isRestoring() {
        return restoring;
    }

    static void restoreFromPart(World world, BlockPos pos) {
        if (world == null || world.isRemote || restoring) {
            return;
        }

        BlockPos center = findCenter(world, pos);
        restoring = true;
        try {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos restorePos = center.add(dx, dy, dz);
                        if (restorePos.equals(pos)) {
                            continue;
                        }

                        IBlockState source = getSourceState(dx, dy, dz);
                        if (source != null) {
                            world.setBlockState(restorePos, source, 3);
                        }
                    }
                }
            }
        } finally {
            restoring = false;
        }
    }

    private static BlockPos findCenter(World world, BlockPos pos) {
        for (int dy = -4; dy <= 0; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos candidate = pos.add(dx, dy, dz);
                    IBlockState state = world.getBlockState(candidate);
                    if (state.getBlock() == TABlocks.RIFTONOMICON
                            && state.getValue(BlockRiftonomicon.PART) == BlockRiftonomicon.Part.CORE) {
                        return candidate.add(0, 0, 3);
                    }
                }
            }
        }
        return pos;
    }

    public static IBlockState getSourceState(int dx, int dy, int dz) {
        return TAMultiblockTriggers.getRiftonomiconSourceState(dx, dy, dz);
    }

    public static BlockRiftonomicon.Part getPartFor(int dx, int dy, int dz) {
        return TAMultiblockTriggers.getRiftonomiconPartFor(dx, dy, dz);
    }
}
