// src/main/java/therealpant/thaumicattempts/world/FluxChunkSampler.java
package therealpant.thaumicattempts.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import thaumcraft.api.aura.AuraHelper;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class FluxChunkSampler {

    private static final int SAMPLE_INTERVAL_TICKS = 40;
    private static final int BATCH_SIZE = 64;

    /** Последнее измеренное значение флакса по чанкам (ключ: pack(cx, cz)). */
    private static final Map<Long, Float> lastFluxByChunk = new HashMap<>();
    private static List<Long> snapshotChunks = new ArrayList<>();
    private static int cursor = 0;

    // ===== Метрики для /ta_flux_status =====
    public static long lastSampleWorldTime = -1;
    public static int lastBatchProcessed = 0;
    public static int lastLoadedChunksSnapshot = 0;
    public static int lastKnownLoadedChunks = 0;
    public static int lastTrackedFluxChunks = 0;

    private FluxChunkSampler() {}

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (world == null || world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (world.provider == null || world.provider.getDimension() != 0) return;
        if ((world.getTotalWorldTime() % SAMPLE_INTERVAL_TICKS) != 0) return;

        // Метрики на входе
        lastSampleWorldTime = world.getTotalWorldTime();
        lastKnownLoadedChunks = FluxChunkTracker.loadedChunks.size();
        lastTrackedFluxChunks = lastFluxByChunk.size();

        // Если закончили проход по snapshot — обновляем его (если есть что обновлять)
        if (cursor >= snapshotChunks.size()) {
            if (FluxChunkTracker.loadedChunks.isEmpty()) {
                snapshotChunks.clear();
                cursor = 0;
                lastLoadedChunksSnapshot = 0;
                lastBatchProcessed = 0;
                return;
            }
            snapshotChunks = new ArrayList<>(FluxChunkTracker.loadedChunks);
            cursor = 0;
            lastLoadedChunksSnapshot = snapshotChunks.size();
        }

        int processed = 0;

        while (cursor < snapshotChunks.size() && processed < BATCH_SIZE) {
            long key = snapshotChunks.get(cursor++);
            int cx = (int) (key >> 32);
            int cz = (int) key;

            // Центр чанка. Y можно любое “безопасное”, 64 ок.
            BlockPos pos = new BlockPos(cx * 16 + 8, 64, cz * 16 + 8);

            // Если чанк выгрузили после снятия snapshot — пропускаем
            if (!world.isBlockLoaded(pos)) {
                continue;
            }

            float flux = AuraHelper.getFlux(world, pos);
            float previous = lastFluxByChunk.getOrDefault(key, flux);
            float delta = flux - previous;

            lastFluxByChunk.put(key, flux);

            // Порог, чтобы не ловить шум
            if (delta > 0.25f) {
                TAWorldFluxData.get(world).addFlux((double) delta);
            }

            processed++;
        }

        lastBatchProcessed = processed;

        // Чистка, если карта распухла: удаляем записи по чанкам, которые больше не загружены.
        if (lastFluxByChunk.size() > 50000) {
            // Безопасная очистка: НЕ используем removeIf на keySet() с fastutil-наборами снаружи
            lastFluxByChunk.entrySet().removeIf(e -> !FluxChunkTracker.loadedChunks.contains(e.getKey()));
        }
    }
}
