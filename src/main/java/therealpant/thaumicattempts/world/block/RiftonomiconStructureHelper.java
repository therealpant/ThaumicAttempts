package therealpant.thaumicattempts.world.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.crafting.Part;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.init.TABlocks;

public final class RiftonomiconStructureHelper {
    public static final int CORE_Y_OFFSET = 3;

    private RiftonomiconStructureHelper() {
    }

    static boolean isRestoring() {
        return MultiblockBreakHelper.isRestoring();
    }

    static void restoreFromPart(World world, BlockPos pos) {
        MultiblockBreakHelper.restoreFromBlueprint(world, pos, TAMultiblockTriggers.RIFTONOMICON_MB_ID,
                RiftonomiconStructureHelper::findCenter);
    }

    private static BlockPos findCenter(World world, BlockPos pos) {
        Part[][][] blueprint = MultiblockBreakHelper.getBlueprintParts(TAMultiblockTriggers.RIFTONOMICON_MB_ID);
        if (blueprint != null) {
            int ySize = blueprint.length;
            int zSize = MultiblockBreakHelper.maxZSize(blueprint);
            int xSize = MultiblockBreakHelper.maxXSize(blueprint);
            int xOrigin = xSize / 2;
            int zOrigin = zSize / 2;

            for (int dy = -ySize + 1; dy <= ySize - 1; dy++) {
                for (int dx = -xOrigin; dx <= xOrigin; dx++) {
                    for (int dz = -zOrigin; dz <= zOrigin; dz++) {
                        BlockPos candidate = pos.add(dx, dy, dz);
                        IBlockState state = world.getBlockState(candidate);
                        if (state.getBlock() == TABlocks.RIFTONOMICON
                                && state.getValue(BlockRiftonomicon.PART) == BlockRiftonomicon.Part.CORE) {
                            return candidate.add(0, -CORE_Y_OFFSET, 0);
                        }
                    }
                }
            }
        }
        return pos;
    }

    public static IBlockState getSourceState(int dx, int dy, int dz) {
        return MultiblockBreakHelper.getSourceState(TAMultiblockTriggers.RIFTONOMICON_MB_ID, dx, dy, dz);
    }

    public static BlockRiftonomicon.Part getPartFor(int dx, int dy, int dz) {
        return TAMultiblockTriggers.getRiftonomiconPartFor(dx, dy, dz);
    }
}
