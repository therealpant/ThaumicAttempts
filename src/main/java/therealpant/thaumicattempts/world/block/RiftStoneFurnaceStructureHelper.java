package therealpant.thaumicattempts.world.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.blocks.BlocksTC;
import therealpant.thaumicattempts.init.TABlocks;

final class RiftStoneFurnaceStructureHelper {
    private static boolean restoring;

    private RiftStoneFurnaceStructureHelper() {
    }

    static boolean isRestoring() {
        return restoring;
    }

    static void restoreFromPart(World world, BlockPos pos, BlockRiftStoneFurnace.Part part) {
        if (world == null || world.isRemote || restoring) {
            return;
        }

        BlockPos center = findCenter(world, pos);
        restoring = true;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos restorePos = center.add(dx, 0, dz);
                    if (!restorePos.equals(pos)) {
                        world.setBlockState(restorePos, getLowerSourceState(dx, dz), 3);
                    }
                }
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos restorePos = center.add(dx, 1, dz);
                    if (restorePos.equals(pos)) {
                        continue;
                    }
                    if (dx == 0 && dz == 0) {
                        if (world.getBlockState(restorePos).getBlock() == BlocksTC.empty) {
                            world.setBlockState(restorePos, Blocks.AIR.getDefaultState(), 3);
                        }
                    } else {
                        world.setBlockState(restorePos, getUpperSourceState(dx, dz), 3);
                    }
                }
            }
        } finally {
            restoring = false;
        }
    }

    private static BlockPos findCenter(World world, BlockPos pos) {
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidate = pos.add(dx, dy, dz);
                    IBlockState state = world.getBlockState(candidate);
                    if (state.getBlock() == TABlocks.RIFT_STONE_FURNACE
                            && state.getValue(BlockRiftStoneFurnace.PART) == BlockRiftStoneFurnace.Part.CENTER_LOW) {
                        return candidate;
                    }
                }
            }
        }
        return pos;
    }

    private static IBlockState getLowerSourceState(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return BlocksTC.smelterVoid.getDefaultState();
        }
        return BlocksTC.metalAlchemicalAdvanced.getDefaultState();
    }

    private static IBlockState getUpperSourceState(int dx, int dz) {
        if (dx != 0 && dz != 0) {
            return BlocksTC.alembic.getDefaultState();
        }
        return TABlocks.DARK_JASPER_BRICKS.getDefaultState();
    }
}
