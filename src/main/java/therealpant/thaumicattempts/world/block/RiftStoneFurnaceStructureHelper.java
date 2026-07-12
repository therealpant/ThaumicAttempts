package therealpant.thaumicattempts.world.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.init.TABlocks;

final class RiftStoneFurnaceStructureHelper {
    private RiftStoneFurnaceStructureHelper() {
    }

    static boolean isRestoring() {
        return MultiblockBreakHelper.isRestoring();
    }

    static void restoreFromPart(World world, BlockPos pos, BlockRiftStoneFurnace.Part part) {
        MultiblockBreakHelper.restoreFromBlueprint(world, pos, TAMultiblockTriggers.RIFT_STONE_FURNACE_MB_ID,
                RiftStoneFurnaceStructureHelper::findCenter);
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
}
