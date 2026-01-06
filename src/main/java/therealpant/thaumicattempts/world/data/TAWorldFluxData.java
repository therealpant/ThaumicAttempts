package therealpant.thaumicattempts.world.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

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

    // ВАРИАНТ 2: трекинг аномалий даже в выгруженных чанках
    private final List<AnomalyRecord> trackedAnomalies = new ArrayList<>();

    public static final class AnomalyRecord {
        public final int dim;
        public final int x, y, z;
        public final UUID id;
        public final long createdTime;

        public AnomalyRecord(int dim, int x, int y, int z, UUID id, long createdTime) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.createdTime = createdTime;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

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

    // ======== Трекинг аномалий ========

    public void trackAnomaly(World world, BlockPos pos, UUID anomalyId) {
        if (world == null || pos == null || anomalyId == null) return;

        int dim = world.provider != null ? world.provider.getDimension() : 0;
        long now = world.getTotalWorldTime();

        // убрать дубликат по UUID
        for (Iterator<AnomalyRecord> it = trackedAnomalies.iterator(); it.hasNext();) {
            AnomalyRecord rec = it.next();
            if (anomalyId.equals(rec.id)) {
                it.remove();
                break;
            }
        }

        trackedAnomalies.add(new AnomalyRecord(dim, pos.getX(), pos.getY(), pos.getZ(), anomalyId, now));
        markDirty();
    }

    public void untrackAnomaly(UUID anomalyId) {
        if (anomalyId == null) return;
        boolean changed = trackedAnomalies.removeIf(r -> anomalyId.equals(r.id));
        if (changed) markDirty();
    }

    @Nullable
    public AnomalyRecord findNearestTracked(int dim, BlockPos from) {
        if (from == null || trackedAnomalies.isEmpty()) return null;

        AnomalyRecord best = null;
        double bestDist = Double.MAX_VALUE;

        for (AnomalyRecord rec : trackedAnomalies) {
            if (rec.dim != dim) continue;

            double dx = (rec.x + 0.5) - (from.getX() + 0.5);
            double dy = (rec.y + 0.5) - (from.getY() + 0.5);
            double dz = (rec.z + 0.5) - (from.getZ() + 0.5);
            double dist = dx * dx + dy * dy + dz * dz;

            if (dist < bestDist) {
                bestDist = dist;
                best = rec;
            }
        }

        return best;
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

        trackedAnomalies.clear();
        if (nbt.hasKey("anomalies", 9)) { // 9 = NBTTagList
            NBTTagList list = nbt.getTagList("anomalies", 10); // 10 = NBTTagCompound
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                int dim = t.getInteger("dim");
                int x = t.getInteger("x");
                int y = t.getInteger("y");
                int z = t.getInteger("z");
                long created = t.getLong("created");
                UUID id = t.hasUniqueId("id") ? t.getUniqueId("id") : null;
                if (id != null) trackedAnomalies.add(new AnomalyRecord(dim, x, y, z, id, created));
            }
        }

        recomputeStage(); // страховка
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setDouble("fluxTotal", fluxGeneratedTotal);
        nbt.setInteger("stage", stage);
        nbt.setLong("nextSpawn", nextAnomalySpawnTime);

        nbt.setLong("lastSpawnAttempt", lastSpawnAttemptTime);
        nbt.setString("lastSpawnFailReason", lastSpawnFailReason == null ? "" : lastSpawnFailReason);
        nbt.setInteger("lastSpawnCheckedCandidates", lastSpawnCheckedCandidates);
        nbt.setLong("lastNextSpawnTimeSet", lastNextAnomalySpawnTimeSet);

        NBTTagList list = new NBTTagList();
        for (AnomalyRecord rec : trackedAnomalies) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("dim", rec.dim);
            t.setInteger("x", rec.x);
            t.setInteger("y", rec.y);
            t.setInteger("z", rec.z);
            t.setUniqueId("id", rec.id);
            t.setLong("created", rec.createdTime);
            list.appendTag(t);
        }
        nbt.setTag("anomalies", list);

        return nbt;
    }
}
