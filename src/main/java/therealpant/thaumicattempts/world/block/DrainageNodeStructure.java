package therealpant.thaumicattempts.world.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.init.TABlocks;

/** Owns the two invisible upper cells of the 1x1x3 drainage column. */
public final class DrainageNodeStructure {
    private static boolean dismantling;

    private DrainageNodeStructure() {}

    public static boolean canForm(World world, BlockPos controller) {
        for (int y=1;y<=2;y++) {
            BlockPos part=controller.up(y);
            if (!world.getBlockState(part).getBlock().isReplaceable(world,part)) return false;
        }
        return true;
    }

    public static boolean form(World world, BlockPos controller) {
        if (!canForm(world,controller)) return false;
        for (int y=1;y<=2;y++) world.setBlockState(controller.up(y),
                TABlocks.DRAINAGE_NODE_PART.getDefaultState(),3);
        return true;
    }

    public static BlockPos findController(World world, BlockPos part) {
        for (int y=1;y<=2;y++) {
            BlockPos candidate=part.down(y);
            if (world.getBlockState(candidate).getBlock()==TABlocks.DRAINAGE_NODE) return candidate;
        }
        return null;
    }

    public static void removeUpperParts(World world, BlockPos controller) {
        if (dismantling) return;
        dismantling=true;
        try {
            for (int y=1;y<=2;y++) if (world.getBlockState(controller.up(y)).getBlock()
                    ==TABlocks.DRAINAGE_NODE_PART) world.setBlockToAir(controller.up(y));
        } finally {
            dismantling=false;
        }
    }

    public static void dismantleFromPart(World world, BlockPos controller) {
        if (dismantling) return;
        dismantling=true;
        try {
            for (int y=1;y<=2;y++) if (world.getBlockState(controller.up(y)).getBlock()
                    ==TABlocks.DRAINAGE_NODE_PART) world.setBlockToAir(controller.up(y));
            if (world.getBlockState(controller).getBlock()==TABlocks.DRAINAGE_NODE)
                world.destroyBlock(controller,true);
        } finally {
            dismantling=false;
        }
    }

    public static boolean isDismantling() { return dismantling; }
}
