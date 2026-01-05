// src/main/java/therealpant/thaumicattempts/world/data/TAWorldFluxData.java
package therealpant.thaumicattempts.world.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Глобальная статистика загрязнения для мира.
 */
public class TAWorldFluxData extends WorldSavedData {

    private static final String DATA_NAME = "thaumicattempts_flux";

    public double fluxGeneratedTotal;
    public int stage;
    public long nextAnomalySpawnTime;
    public long lastSpawnAttemptTime;
    public String lastSpawnFailReason = "";
    public int lastSpawnCheckedCandidates;
    public long lastNextAnomalySpawnTimeSet;

    public TAWorldFluxData() {
        super(DATA_NAME);
    }

    public TAWorldFluxData(String name) {
        super(name);
    }

    public static TAWorldFluxData get(World world) {
        if (world == null) return new TAWorldFluxData();

        MapStorage storage = world.getMapStorage();
        if (storage == null) return new TAWorldFluxData();

        TAWorldFluxData data = (TAWorldFluxData) storage.getOrLoadData(TAWorldFluxData.class, DATA_NAME);
        if (data == null) {
            data = new TAWorldFluxData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** Добавить вклад в глобальную статистику. World-параметр не нужен. */
    public void addFlux(double amount) {
        if (amount <= 0) return;

        fluxGeneratedTotal += amount;
        recomputeStage();
        markDirty();
    }

    public void recomputeStage() {
        if (fluxGeneratedTotal < 10000) {
            stage = 0;
        } else if (fluxGeneratedTotal < 15000) {
            stage = 1;
        } else if (fluxGeneratedTotal < 30000) {
            stage = 2;
        } else {
            stage = 3;
        }
    }

    public boolean canTrySpawn(World world) {
        if (world == null || stage <= 0) return false;
        return world.getTotalWorldTime() >= nextAnomalySpawnTime;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        fluxGeneratedTotal = nbt.getDouble("fluxTotal");
        stage = nbt.getInteger("stage");
        nextAnomalySpawnTime = nbt.getLong("nextSpawn");
        lastSpawnAttemptTime = nbt.getLong("lastSpawnAttempt");
        lastSpawnFailReason = nbt.getString("lastSpawnFailReason");
        lastSpawnCheckedCandidates = nbt.getInteger("lastSpawnCheckedCandidates");
        lastNextAnomalySpawnTimeSet = nbt.getLong("lastNextSpawnTimeSet");
        recomputeStage(); // страховка
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setDouble("fluxTotal", fluxGeneratedTotal);
        nbt.setInteger("stage", stage);
        nbt.setLong("lastSpawnAttempt", lastSpawnAttemptTime);
        nbt.setString("lastSpawnFailReason", lastSpawnFailReason == null ? "" : lastSpawnFailReason);
        nbt.setInteger("lastSpawnCheckedCandidates", lastSpawnCheckedCandidates);
        nbt.setLong("lastNextSpawnTimeSet", lastNextAnomalySpawnTimeSet);
        nbt.setLong("nextSpawn", nextAnomalySpawnTime);
        return nbt;
    }
}
