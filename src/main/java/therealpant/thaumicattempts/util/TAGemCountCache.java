package therealpant.thaumicattempts.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TAGemCountCache {
    private static final Map<UUID, Integer> CLIENT_AMBER_COUNTS = new ConcurrentHashMap<>();

    private TAGemCountCache() {}

    public static int getClientAmberCount(UUID playerId) {
        if (playerId == null) return -1;
        Integer count = CLIENT_AMBER_COUNTS.get(playerId);
        return count == null ? -1 : count;
    }

    public static void setClientAmberCount(UUID playerId, int count) {
        if (playerId == null) return;
        CLIENT_AMBER_COUNTS.put(playerId, count);
    }

    public static void clearClientAmberCount(UUID playerId) {
        if (playerId == null) return;
        CLIENT_AMBER_COUNTS.remove(playerId);
    }
}
