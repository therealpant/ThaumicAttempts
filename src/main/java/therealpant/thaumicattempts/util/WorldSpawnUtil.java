package therealpant.thaumicattempts.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Утилиты для безопасного поиска позиций в мире.
 */
public final class WorldSpawnUtil {

    private WorldSpawnUtil() {}

    /**
     * Ищет безопасную позицию для спавна семени в кубе 3x3x3 вокруг переданного центра.
     * Требования:
     * <ul>
     *     <li>Под блоком должен быть solid по стороне UP.</li>
     *     <li>Целевая позиция и позиция над ней должны быть воздухом или заменяемыми блоками.</li>
     *     <li>Позиции с жидкостями отсекаются.</li>
     * </ul>
     * Если позиции не найдены — используется {@code precipitationHeight(center).up()} с теми же проверками.
     *
     * @return безопасная позиция или {@code null}, если подходящая позиция не найдена
     */
    @Nullable
    public static BlockPos findSafeSeedPos(World world, BlockPos center, Random rand) {
        if (world == null || center == null) return null;

        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    candidates.add(center.add(dx, dy, dz));
                }
            }
        }

        if (rand != null) {
            Collections.shuffle(candidates, rand);
        }

        for (BlockPos pos : candidates) {
            if (isSafeSeedPos(world, pos)) {
                return pos.toImmutable();
            }
        }

        BlockPos fallback = world.getPrecipitationHeight(center).up();
        if (isSafeSeedPos(world, fallback)) {
            return fallback.toImmutable();
        }

        return null;
    }

    private static boolean isSafeSeedPos(World world, BlockPos pos) {
        if (pos == null) return false;
        if (pos.getY() < 1 || pos.getY() >= world.getHeight() - 1) return false;
        if (!world.isBlockLoaded(pos)) {
            world.getChunk(pos);
        }

        if (!isAirOrReplaceable(world, pos)) return false;
        if (!isAirOrReplaceable(world, pos.up())) return false;

        BlockPos belowPos = pos.down();
        IBlockState belowState = world.getBlockState(belowPos);
        if (belowState.getMaterial().isLiquid()) return false;

        return belowState.isSideSolid(world, belowPos, EnumFacing.UP);
    }

    private static boolean isAirOrReplaceable(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial().isLiquid()) return false;
        return world.isAirBlock(pos) || state.getMaterial().isReplaceable();
    }
}