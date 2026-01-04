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
    public int anomalyStage;
    public long nextAnomalySpawnTime;

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

    public void addFlux(World world, double amount) {
        if (amount <= 0) return;

        fluxGeneratedTotal += amount;
        recomputeStage();
        markDirty();
    }

    public void recomputeStage() {
        if (fluxGeneratedTotal < 10000) {
            anomalyStage = 0;
        } else if (fluxGeneratedTotal < 15000) {
            anomalyStage = 1;
        } else if (fluxGeneratedTotal < 30000) {
            anomalyStage = 2;
        } else {
            anomalyStage = 3;
        }
    }

    public boolean canTrySpawn(World world) {
        if (world == null || anomalyStage <= 0) return false;
        return world.getTotalWorldTime() >= nextAnomalySpawnTime;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        fluxGeneratedTotal = nbt.getDouble("fluxTotal");
        anomalyStage = nbt.getInteger("stage");
        nextAnomalySpawnTime = nbt.getLong("nextSpawn");
        recomputeStage();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setDouble("fluxTotal", fluxGeneratedTotal);
        nbt.setInteger("stage", anomalyStage);
        nbt.setLong("nextSpawn", nextAnomalySpawnTime);
        return nbt;
    }
}