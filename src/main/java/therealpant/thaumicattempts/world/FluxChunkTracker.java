// src/main/java/therealpant/thaumicattempts/world/FluxChunkTracker.java
package therealpant.thaumicattempts.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import therealpant.thaumicattempts.ThaumicAttempts;

import java.util.Set;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class FluxChunkTracker {

    /** Ключи чанков (cx, cz) для загруженных чанков OVERWORLD (dim 0). */
    public static final Set<Long> loadedChunks = new LongOpenHashSet();

    private FluxChunkTracker() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event == null) return;
        if (event.getWorld() == null || event.getWorld().isRemote) return;
        if (event.getWorld().provider == null) return;
        if (event.getWorld().provider.getDimension() != 0) return; // ВАЖНО: только оверворлд

        int cx = event.getChunk().x;
        int cz = event.getChunk().z;
        loadedChunks.add(pack(cx, cz));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event == null) return;
        if (event.getWorld() == null || event.getWorld().isRemote) return;
        if (event.getWorld().provider == null) return;
        if (event.getWorld().provider.getDimension() != 0) return; // ВАЖНО: только оверворлд

        int cx = event.getChunk().x;
        int cz = event.getChunk().z;
        loadedChunks.remove(pack(cx, cz));
    }

    public static long pack(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }
}
