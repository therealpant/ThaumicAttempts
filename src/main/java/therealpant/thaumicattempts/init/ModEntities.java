// src/main/java/therealpant/thaumicattempts/init/ModEntities.java
package therealpant.thaumicattempts.init;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.EntityFluxAnomalyBurst;

public final class ModEntities {

    private static int nextId = 1;

    private ModEntities() {}

    public static void register() {
        registerEntity(
                "flux_anomaly_burst",
                EntityFluxAnomalyBurst.class,
                64,   // tracking range
                1,    // update frequency
                false // send velocity
        );
    }

    private static void registerEntity(String name,
                                       Class<? extends Entity> entityClass,
                                       int trackingRange,
                                       int updateFrequency,
                                       boolean sendsVelocityUpdates) {

        ResourceLocation id = new ResourceLocation(ThaumicAttempts.MODID, name);

        EntityRegistry.registerModEntity(
                id,
                entityClass,
                name,
                nextId++,
                ThaumicAttempts.INSTANCE,
                trackingRange,
                updateFrequency,
                sendsVelocityUpdates
        );
    }
}
