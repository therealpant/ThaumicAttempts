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
