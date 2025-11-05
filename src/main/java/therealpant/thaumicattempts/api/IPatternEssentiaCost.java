package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;


public interface IPatternEssentiaCost {
    int getEssentiaCost(ItemStack pattern, World world);
}
