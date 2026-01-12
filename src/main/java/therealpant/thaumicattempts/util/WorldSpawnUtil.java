package therealpant.thaumicattempts.util;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import thaumcraft.api.blocks.BlocksTC;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Утилиты для безопасного поиска позиций в мире.
 */
public final class WorldSpawnUtil {

    // Размеры EntityTaintSeed из Thaumcraft:
    // setSize(1.5f, 1.25f)
    private static final double TAINT_SEED_HALF_WIDTH = 0.75;
    private static final double TAINT_SEED_HEIGHT = 1.25;

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

    public static boolean isSafeSeedPos(World world, BlockPos pos) {
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
        if (world.isAirBlock(pos)) return true;
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial().isLiquid()) return false;
        if (state.getMaterial().isReplaceable()) {
            AxisAlignedBB bb = state.getCollisionBoundingBox(world, pos);
            return bb == null || bb == Block.NULL_AABB || bb.equals(Block.NULL_AABB);
        }
        return false;
    }

    public static AxisAlignedBB getTaintSeedAabbAt(BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        return new AxisAlignedBB(
                x - TAINT_SEED_HALF_WIDTH, y, z - TAINT_SEED_HALF_WIDTH,
                x + TAINT_SEED_HALF_WIDTH, y + TAINT_SEED_HEIGHT, z + TAINT_SEED_HALF_WIDTH
        );
    }

    public static boolean isSafeSeedAabb(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        if (!world.isAreaLoaded(pos, 2)) return false;

        AxisAlignedBB bb = getTaintSeedAabbAt(pos);

        // Не должно быть коллизий с блоками
        if (!world.getCollisionBoxes(null, bb).isEmpty()) return false;

        // Не должно содержать жидкость
        if (world.containsAnyLiquid(bb)) return false;

        return true;
    }

    public static boolean ensureSeedPocket(World world, BlockPos seedPos) {
        if (world == null || seedPos == null) return false;
        if (!world.isAreaLoaded(seedPos, 3)) return false;

        // Карман вокруг точки. Делаем +1 запас, потому что хитбокс 1.5 блока.
        BlockPos min = seedPos.add(-1, 0, -1);
        BlockPos max = seedPos.add( 1, 1,  1);

        for (BlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            IBlockState st = world.getBlockState(pos);
            if (st == null) continue;

            // не ломаем "защищённые"
            if (st.getBlock() == Blocks.BEDROCK) continue;
            if (st.getBlock().hasTileEntity(st) || world.getTileEntity(pos) != null) continue;
            if (st.getBlockHardness(world, pos) < 0.0f) continue;

            Material mat = st.getMaterial();

            // жидкости лучше "затыкать" таинт-роком, чтобы не было затопления
            if (mat.isLiquid()) {
                world.setBlockState(pos, BlocksTC.taintRock.getDefaultState(), 3);
                continue;
            }

            // если блок даёт коллизию — очищаем
            // (replaceable тоже можно очищать, но это не обязательно)
            if (!mat.isReplaceable() && !world.isAirBlock(pos)) {
                world.setBlockToAir(pos);
            }
        }

        // Финальная проверка: после вырубки хитбокс точно свободен
        return isSafeSeedAabb(world, seedPos);
    }

}