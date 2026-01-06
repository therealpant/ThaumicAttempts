package therealpant.thaumicattempts.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * API для вызова/конфигурации флюкс-аномалий.
 */
public final class FluxAnomalyApi {

    private FluxAnomalyApi() {}

    /**
     * Спавнит сущность, отвечающую за распространение заражения.
     */
    @Nonnull
    public static EntityFluxAnomalyBurst spawn(@Nonnull World world, @Nonnull BlockPos center,
                                               @Nonnull FluxAnomalySettings settings) {
        EntityFluxAnomalyBurst anomaly = createAnomaly(world, center, settings);
        world.spawnEntity(anomaly);
        return anomaly;
    }

    /**
     * Пытается заспавнить сущность аномалии и возвращает результат.
     */
    public static boolean trySpawn(@Nonnull World world, @Nonnull BlockPos center,
                                   @Nonnull FluxAnomalySettings settings) {
        EntityFluxAnomalyBurst anomaly = createAnomaly(world, center, settings);
        return world.spawnEntity(anomaly);
    }

    /**
     * ВАРИАНТ 2: Пытается заспавнить и вернуть инстанс сущности.
     * Возвращает null, если spawnEntity вернул false.
     */
    @Nullable
    public static EntityFluxAnomalyBurst trySpawnEntity(@Nonnull World world, @Nonnull BlockPos center,
                                                        @Nonnull FluxAnomalySettings settings) {
        EntityFluxAnomalyBurst anomaly = createAnomaly(world, center, settings);
        return world.spawnEntity(anomaly) ? anomaly : null;
    }

    @Nonnull
    private static EntityFluxAnomalyBurst createAnomaly(@Nonnull World world, @Nonnull BlockPos center,
                                                        @Nonnull FluxAnomalySettings settings) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(settings, "settings");

        return new EntityFluxAnomalyBurst(world, center, settings);
    }
}
