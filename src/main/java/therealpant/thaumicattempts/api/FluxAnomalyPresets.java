package therealpant.thaumicattempts.api;

import net.minecraft.block.Block;
import net.minecraft.util.math.MathHelper;
import therealpant.thaumicattempts.init.TABlocks;

import java.util.concurrent.ThreadLocalRandom;

public final class FluxAnomalyPresets {

    private FluxAnomalyPresets() {}

    public static FluxAnomalySettings createSettings(FluxAnomalyTier tier) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        FluxAnomalySettings settings = new FluxAnomalySettings()
                .spawnMethod(FluxAnomalySpawnMethod.WORLD_GEN);

        switch (tier) {
            case SURFACE:
                settings.radiusBlocks(randomInRange(rnd, 11, 14))
                        .totalSpreads(randomInRange(rnd, 7000, 10000))
                        .budgetPerTick(randomInRange(rnd, 180, 240))
                        .resource(resource(TABlocks.RIFT_BUSH, rnd));
                break;
            case SHALLOW:
                settings.radiusBlocks(randomInRange(rnd, 11, 14))
                        .totalSpreads(randomInRange(rnd, 9000, 13000))
                        .budgetPerTick(randomInRange(rnd, 220, 300))
                        .resource(resource(TABlocks.ANOMALY_STONE, rnd));
                break;
            case DEEP:
                settings.radiusBlocks(randomInRange(rnd, 11, 15))
                        .totalSpreads(randomInRange(rnd, 11000, 16000))
                        .budgetPerTick(randomInRange(rnd, 240, 340))
                        .resource(resource(TABlocks.RIFT_GEOD, rnd));
                break;
            default:
                break;
        }

        return settings;
    }

    private static int randomInRange(ThreadLocalRandom rnd, int min, int max) {
        if (min >= max) return min;
        return rnd.nextInt(min, max + 1);
    }

    private static FluxAnomalyResource resource(Block block, ThreadLocalRandom rnd) {
        if (block == null) return FluxAnomalyResource.empty();
        int count = MathHelper.clamp(randomInRange(rnd, 2, 3), 0, 64);
        return FluxAnomalyResource.of(block, count);
    }
}