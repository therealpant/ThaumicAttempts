package therealpant.thaumicattempts.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import therealpant.thaumicattempts.ThaumicAttempts;

@Config(modid = ThaumicAttempts.MODID)
public class TAConfig {
    @Config.Comment("Should enable eldritch stone crafting recipe?")
    @Config.Name("Enable Eldritch Stone Recipe")
    @Config.RequiresMcRestart
    public static boolean ENABLE_ELDRITCH_STONE_RECIPE = true;

    @Config.Comment("Enable custom model replacement for Thaumcraft Pillars.")
    @Config.Name("Enable Custom Pillar Model Replacement")
    @Config.RequiresMcRestart
    public static boolean ENABLE_PILLAR_MODEL_REPLACEMENT = false;

    @Config.Comment("Enable extra debug logging for flux anomaly seeds and resource placement.")
    @Config.Name("Enable Flux Anomaly Debug Logs")
    public static boolean ENABLE_FLUX_ANOMALY_DEBUG_LOGS = false;

    @Config.Comment("Enable debug logging for aura booster power attempts.")
    @Config.Name("Enable Aura Booster Debug Logs")
    public static boolean ENABLE_AURA_BOOSTER_DEBUG_LOGS = false;

    @Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
    public static class ConfigChangeListener {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(ThaumicAttempts.MODID)) {
                ConfigManager.sync(ThaumicAttempts.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
