package therealpant.thaumicattempts.golemcraft.tile;

import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.util.ResourceLocation;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;

public final class ModTiles {
    // [Регистрация ТЕ] — один раз, чтобы не было "missing mapping"
    public static void register() {
        GameRegistry.registerTileEntity(
                TileEntityGolemCrafter.class,
                new ResourceLocation(ThaumicAttempts.MODID, "golem_crafter")
        );

    }
}