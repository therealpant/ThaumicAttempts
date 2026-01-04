package therealpant.thaumicattempts.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;

import javax.annotation.Nonnull;
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
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(settings, "settings");

        EntityFluxAnomalyBurst anomaly = new EntityFluxAnomalyBurst(world, center, settings);
        world.spawnEntity(anomaly);
        return anomaly;
    }
}
