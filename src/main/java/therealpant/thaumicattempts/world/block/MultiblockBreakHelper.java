package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.crafting.Part;

import javax.annotation.Nullable;

public final class MultiblockBreakHelper {
    private static boolean restoring;

    private MultiblockBreakHelper() {
    }

    public interface CenterFinder {
        BlockPos findCenter(World world, BlockPos brokenPos);
    }

    public static boolean isRestoring() {
        return restoring;
    }

    public static void restoreFromBlueprint(World world, BlockPos brokenPos,
                                            ResourceLocation blueprintId, CenterFinder centerFinder) {
        if (world == null || world.isRemote || restoring) {
            return;
        }

        BlockPos center = centerFinder.findCenter(world, brokenPos);
        restoreFromBlueprint(world, brokenPos, blueprintId, center);
    }

    public static void restoreFromBlueprint(World world, BlockPos brokenPos,
                                            ResourceLocation blueprintId, BlockPos center) {
        if (world == null || world.isRemote || restoring) {
            return;
        }

        Part[][][] blueprint = getBlueprintParts(blueprintId);
        if (blueprint == null || center == null) {
            return;
        }

        restoring = true;
        try {
            restoreBlueprintBlocks(world, center, brokenPos, blueprint);
        } finally {
            restoring = false;
        }
    }

    @Nullable
    public static Part[][][] getBlueprintParts(ResourceLocation blueprintId) {
        Object recipe = ThaumcraftApi.getCraftingRecipes().get(blueprintId);
        if (recipe instanceof ThaumcraftApi.BluePrint) {
            return ((ThaumcraftApi.BluePrint) recipe).getParts();
        }
        return null;
    }

    @Nullable
    public static IBlockState getSourceState(ResourceLocation blueprintId, int dx, int dy, int dz) {
        Part[][][] blueprint = getBlueprintParts(blueprintId);
        if (blueprint == null || dy < 0 || dy >= blueprint.length) {
            return null;
        }

        int y = blueprint.length - 1 - dy;
        int z = dz + maxZSize(blueprint) / 2;
        int x = dx + maxXSize(blueprint) / 2;
        if (y < 0 || y >= blueprint.length || blueprint[y] == null
                || z < 0 || z >= blueprint[y].length || blueprint[y][z] == null
                || x < 0 || x >= blueprint[y][z].length || blueprint[y][z][x] == null) {
            return null;
        }
        return sourceState(blueprint[y][z][x]);
    }

    public static int maxZSize(Part[][][] parts) {
        int max = 0;
        for (Part[][] zLayer : parts) {
            if (zLayer != null && zLayer.length > max) {
                max = zLayer.length;
            }
        }
        return max;
    }

    public static int maxXSize(Part[][][] parts) {
        int max = 0;
        for (Part[][] zLayer : parts) {
            if (zLayer == null) {
                continue;
            }
            for (Part[] xRow : zLayer) {
                if (xRow != null && xRow.length > max) {
                    max = xRow.length;
                }
            }
        }
        return max;
    }

    @Nullable
    public static IBlockState sourceState(Part part) {
        Object source = part.getSource();
        if (source instanceof IBlockState) {
            return (IBlockState) source;
        }
        if (source instanceof Block) {
            return ((Block) source).getDefaultState();
        }
        if (source instanceof ItemStack) {
            ItemStack stack = (ItemStack) source;
            if (stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                return block.getStateFromMeta(stack.getMetadata());
            }
            return null;
        }
        if ("AIR".equals(source)) {
            return Blocks.AIR.getDefaultState();
        }
        return null;
    }

    private static void restoreBlueprintBlocks(World world, BlockPos center, BlockPos brokenPos, Part[][][] blueprint) {
        int xOrigin = maxXSize(blueprint) / 2;
        int zOrigin = maxZSize(blueprint) / 2;
        for (int y = 0; y < blueprint.length; y++) {
            Part[][] zLayer = blueprint[y];
            if (zLayer == null) {
                continue;
            }

            int dy = blueprint.length - 1 - y;
            for (int z = 0; z < zLayer.length; z++) {
                Part[] xRow = zLayer[z];
                if (xRow == null) {
                    continue;
                }

                int dz = z - zOrigin;
                for (int x = 0; x < xRow.length; x++) {
                    Part part = xRow[x];
                    if (part == null) {
                        continue;
                    }

                    BlockPos restorePos = center.add(x - xOrigin, dy, dz);
                    if (restorePos.equals(brokenPos)) {
                        continue;
                    }

                    IBlockState source = sourceState(part);
                    if (source != null) {
                        world.setBlockState(restorePos, source, 3);
                    }
                }
            }
        }
    }
}
