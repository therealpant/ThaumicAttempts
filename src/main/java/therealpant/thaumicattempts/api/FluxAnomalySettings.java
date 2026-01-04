package therealpant.thaumicattempts.api;

import java.util.Objects;

/**
 * Конфигурация нового {@link therealpant.thaumicattempts.world.EntityFluxAnomalyBurst}.
 */
public class FluxAnomalySettings {

    private int radiusBlocks = 40;
    private int totalSpreads = 6500;
    private int budgetPerTick = 220;
    private FluxAnomalySpawnMethod spawnMethod = FluxAnomalySpawnMethod.API;
    private FluxAnomalyResource resource = FluxAnomalyResource.empty();

    public int getRadiusBlocks() {
        return radiusBlocks;
    }

    public FluxAnomalySettings radiusBlocks(int radiusBlocks) {
        this.radiusBlocks = radiusBlocks;
        return this;
    }

    public int getTotalSpreads() {
        return totalSpreads;
    }

    public FluxAnomalySettings totalSpreads(int totalSpreads) {
        this.totalSpreads = totalSpreads;
        return this;
    }

    public int getBudgetPerTick() {
        return budgetPerTick;
    }

    public FluxAnomalySettings budgetPerTick(int budgetPerTick) {
        this.budgetPerTick = budgetPerTick;
        return this;
    }

    public FluxAnomalySpawnMethod getSpawnMethod() {
        return spawnMethod;
    }

    public FluxAnomalySettings spawnMethod(FluxAnomalySpawnMethod spawnMethod) {
        this.spawnMethod = Objects.requireNonNull(spawnMethod, "Spawn method");
        return this;
    }

    public FluxAnomalyResource getResource() {
        return resource;
    }

    public FluxAnomalySettings resource(FluxAnomalyResource resource) {
        this.resource = Objects.requireNonNull(resource, "Resource");
        return this;
    }
}