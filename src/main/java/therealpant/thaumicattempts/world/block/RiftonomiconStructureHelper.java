package therealpant.thaumicattempts.world.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
            for (int dy = 0; dy <= 3; dy++) {
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
        for (int dy = -3; dy <= 0; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos candidate = pos.add(dx, dy, dz);
                    IBlockState state = world.getBlockState(candidate);
                    if (state.getBlock() == TABlocks.RIFTONOMICON
                            && state.getValue(BlockRiftonomicon.PART) == BlockRiftonomicon.Part.CORE) {
                        return candidate;
                    }
                }
            }
        }
        return pos;
    }

    public static IBlockState getSourceState(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        int max = Math.max(ax, az);

        if (dy == 0) {
            if (dx == 0 && dz == 0) {
                return TABlocks.ELDRITCH_CONSTRUCTION.getDefaultState();
            }
            if (ax == 3 && az == 3) {
                return TABlocks.DARK_JASPER_BRICKS.getDefaultState();
            }
            if (max == 3) {
                return TABlocks.POLISHED_DARK_JASPER.getDefaultState();
            }
            return TABlocks.RIFT_STONE_BASE.getDefaultState();
        }

        if (dy == 1) {
            if (ax == 3 && az == 3) {
                return TABlocks.ELDRITCH_CONSTRUCTION.getDefaultState();
            }
            if (ax <= 1 && az <= 1) {
                return dx == 0 && dz == 0
                        ? TABlocks.RIFT_CRISTAL_BLOCK.getDefaultState()
                        : TABlocks.DARK_JASPER_BRICKS.getDefaultState();
            }
            return null;
        }

        if (dy == 2) {
            if (ax == 3 && az == 3) {
                return TABlocks.ELDRITCH_CONSTRUCTION.getDefaultState();
            }
            if ((ax == 1 && az == 1) || (dx == 0 && dz == 0)) {
                return TABlocks.RIFT_CRISTAL_BLOCK.getDefaultState();
            }
            return null;
        }

        if (dy == 3 && ((ax == 1 && az == 1) || (dx == 0 && dz == 0))) {
            return TABlocks.RIFT_CRISTAL_BLOCK.getDefaultState();
        }

        return null;
    }

    public static BlockRiftonomicon.Part getPartFor(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);

        if (dy == 0) {
            if (dx == 0 && dz == 0) {
                return BlockRiftonomicon.Part.CORE;
            }
            if (ax == 3 && az == 3) {
                return BlockRiftonomicon.Part.CORNER;
            }
            if (Math.max(ax, az) == 3) {
                return BlockRiftonomicon.Part.EDGE;
            }
            return BlockRiftonomicon.Part.BASE;
        }

        if (dy == 1 || dy == 2) {
            if (ax == 3 && az == 3) {
                return BlockRiftonomicon.Part.COLUMN;
            }
            return BlockRiftonomicon.Part.INNER;
        }

        return BlockRiftonomicon.Part.TOP;
    }
}
