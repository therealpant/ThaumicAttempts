package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.crafting.Part;

import javax.annotation.Nullable;

/** Restores every TA multiblock directly from its registered Thaumcraft blueprint. */
public final class MultiblockBreakHelper {
    private static boolean restoring;

    private MultiblockBreakHelper() {
    }

    public static boolean isRestoring() {
        return restoring;
    }

    /**
     * Finds the best matching blueprint and restores all its source parts except the
     * part currently being broken. The broken source part is supplied by getDrops.
     */
    @Nullable
    public static Match restoreFromBlueprint(World world, BlockPos brokenPos, IBlockState brokenState,
                                             ResourceLocation... blueprintIds) {
        if (world == null || world.isRemote || restoring) return null;

        Match match = findMatch(world, brokenPos, brokenState, blueprintIds);
        if (match == null) return null;

        restoreMatch(world, brokenPos, match);
        return match;
    }

    /** Restores a previously resolved match, allowing controllers to drop contents first. */
    public static void restoreMatch(World world, BlockPos brokenPos, @Nullable Match match) {
        if (world == null || world.isRemote || restoring || match == null) return;

        restoring = true;
        try {
            restoreSourceParts(world, brokenPos, match);
        } finally {
            restoring = false;
        }
    }

    /** Finds the blueprint alignment without changing the world. */
    @Nullable
    public static Match findMatch(IBlockAccess world, BlockPos brokenPos, IBlockState brokenState,
                                  ResourceLocation... blueprintIds) {
        if (world == null || brokenPos == null || brokenState == null
                || blueprintIds == null || blueprintIds.length == 0) {
            return null;
        }

        Match best = null;
        for (ResourceLocation blueprintId : blueprintIds) {
            Part[][][] parts = getBlueprintParts(blueprintId);
            if (parts == null) continue;

            int xOrigin = maxXSize(parts) / 2;
            int zOrigin = maxZSize(parts) / 2;
            for (int y = 0; y < parts.length; y++) {
                Part[][] zLayer = parts[y];
                if (zLayer == null) continue;

                int dy = parts.length - 1 - y;
                for (int z = 0; z < zLayer.length; z++) {
                    Part[] xRow = zLayer[z];
                    if (xRow == null) continue;

                    for (int x = 0; x < xRow.length; x++) {
                        Part part = xRow[x];
                        IBlockState target = targetState(part);
                        if (!sameState(target, brokenState)) continue;

                        BlockPos offset = new BlockPos(x - xOrigin, dy, z - zOrigin);
                        BlockPos center = brokenPos.add(-offset.getX(), -offset.getY(), -offset.getZ());
                        int score = scoreAlignment(world, brokenPos, brokenState, center, parts);
                        Match candidate = new Match(blueprintId, parts, center, offset, part, score);
                        if (best == null || candidate.score > best.score) best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /** Adds the drops of the source part corresponding to the broken formed part. */
    public static void addSourceDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                                      IBlockState formedState, int fortune, ResourceLocation... blueprintIds) {
        Match match = findMatch(world, pos, formedState, blueprintIds);
        if (match == null) return;

        IBlockState source = match.getBrokenSourceState();
        if (source != null && source.getBlock() != Blocks.AIR) {
            source.getBlock().getDrops(drops, world, pos, source, fortune);
        }
    }

    @Nullable
    public static Part[][][] getBlueprintParts(ResourceLocation blueprintId) {
        Object recipe = ThaumcraftApi.getCraftingRecipes().get(blueprintId);
        return recipe instanceof ThaumcraftApi.BluePrint
                ? ((ThaumcraftApi.BluePrint) recipe).getParts()
                : null;
    }

    public static int maxZSize(Part[][][] parts) {
        int max = 0;
        if (parts == null) return max;
        for (Part[][] zLayer : parts) {
            if (zLayer != null) max = Math.max(max, zLayer.length);
        }
        return max;
    }

    public static int maxXSize(Part[][][] parts) {
        int max = 0;
        if (parts == null) return max;
        for (Part[][] zLayer : parts) {
            if (zLayer == null) continue;
            for (Part[] xRow : zLayer) {
                if (xRow != null) max = Math.max(max, xRow.length);
            }
        }
        return max;
    }

    @Nullable
    public static IBlockState sourceState(Part part) {
        return part == null ? null : stateFromPartObject(part.getSource());
    }

    @Nullable
    private static IBlockState targetState(Part part) {
        return part == null ? null : stateFromPartObject(part.getTarget());
    }

    @Nullable
    private static IBlockState stateFromPartObject(Object value) {
        if (value instanceof IBlockState) return (IBlockState) value;
        if (value instanceof Block) return ((Block) value).getDefaultState();
        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack) value;
            if (stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                return block.getStateFromMeta(stack.getMetadata());
            }
            return null;
        }
        return "AIR".equals(value) ? Blocks.AIR.getDefaultState() : null;
    }

    private static boolean sameState(@Nullable IBlockState expected, @Nullable IBlockState actual) {
        return expected != null && actual != null && expected.equals(actual);
    }

    private static int scoreAlignment(IBlockAccess world, BlockPos brokenPos, IBlockState brokenState,
                                      BlockPos center, Part[][][] parts) {
        int score = 0;
        int xOrigin = maxXSize(parts) / 2;
        int zOrigin = maxZSize(parts) / 2;

        for (int y = 0; y < parts.length; y++) {
            Part[][] zLayer = parts[y];
            if (zLayer == null) continue;
            int dy = parts.length - 1 - y;

            for (int z = 0; z < zLayer.length; z++) {
                Part[] xRow = zLayer[z];
                if (xRow == null) continue;
                for (int x = 0; x < xRow.length; x++) {
                    IBlockState expected = targetState(xRow[x]);
                    if (expected == null || expected.getBlock() == Blocks.AIR) continue;

                    BlockPos checkPos = center.add(x - xOrigin, dy, z - zOrigin);
                    IBlockState actual = checkPos.equals(brokenPos)
                            ? brokenState
                            : world.getBlockState(checkPos);
                    score += sameState(expected, actual) ? 4 : -1;
                }
            }
        }
        return score;
    }

    private static void restoreSourceParts(World world, BlockPos brokenPos, Match match) {
        Part[][][] parts = match.parts;
        int xOrigin = maxXSize(parts) / 2;
        int zOrigin = maxZSize(parts) / 2;

        for (int y = 0; y < parts.length; y++) {
            Part[][] zLayer = parts[y];
            if (zLayer == null) continue;
            int dy = parts.length - 1 - y;

            for (int z = 0; z < zLayer.length; z++) {
                Part[] xRow = zLayer[z];
                if (xRow == null) continue;
                for (int x = 0; x < xRow.length; x++) {
                    Part part = xRow[x];
                    if (part == null) continue;

                    BlockPos restorePos = match.center.add(x - xOrigin, dy, z - zOrigin);
                    if (restorePos.equals(brokenPos)) continue;

                    IBlockState source = sourceState(part);
                    if (source != null) world.setBlockState(restorePos, source, 3);
                }
            }
        }
    }

    public static final class Match {
        private final ResourceLocation blueprintId;
        private final Part[][][] parts;
        private final BlockPos center;
        private final BlockPos brokenOffset;
        private final Part brokenPart;
        private final int score;

        private Match(ResourceLocation blueprintId, Part[][][] parts, BlockPos center,
                      BlockPos brokenOffset, Part brokenPart, int score) {
            this.blueprintId = blueprintId;
            this.parts = parts;
            this.center = center;
            this.brokenOffset = brokenOffset;
            this.brokenPart = brokenPart;
            this.score = score;
        }

        public ResourceLocation getBlueprintId() {
            return blueprintId;
        }

        /** Blueprint origin at the center of its lowest layer. */
        public BlockPos getCenter() {
            return center;
        }

        public BlockPos getBrokenOffset() {
            return brokenOffset;
        }

        @Nullable
        public IBlockState getBrokenSourceState() {
            return sourceState(brokenPart);
        }
    }
}
