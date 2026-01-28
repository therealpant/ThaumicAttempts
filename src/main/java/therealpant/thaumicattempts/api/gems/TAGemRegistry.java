package therealpant.thaumicattempts.api.gems;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;

/**
 * Registry for gem definitions.
 */
public final class TAGemRegistry {
    private static final Map<ResourceLocation, ITAGemDefinition> REGISTRY = new LinkedHashMap<>();

    private TAGemRegistry() {}

    /**
     * Register a gem definition.
     *
     * @param def gem definition
     */
    public static void register(ITAGemDefinition def) {
        if (def == null) {
            ThaumicAttempts.LOGGER.error("[TA] Gem registry: null definition rejected");
            return;
        }
        ResourceLocation id = def.getId();
        if (id == null) {
            ThaumicAttempts.LOGGER.error("[TA] Gem registry: null id for {}", def.getClass().getName());
            return;
        }
        if (REGISTRY.containsKey(id)) {
            ThaumicAttempts.LOGGER.error("[TA] Gem registry: duplicate id {} for {}", id, def.getClass().getName());
            return;
        }
        REGISTRY.put(id, def);
    }

    /**
     * Fetch a gem definition by id.
     *
     * @param id gem id
     * @return gem definition or null
     */
    public static ITAGemDefinition get(ResourceLocation id) {
        return id == null ? null : REGISTRY.get(id);
    }

    /**
     * All registered gem definitions.
     *
     * @return immutable collection of all definitions
     */
    public static Collection<ITAGemDefinition> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}