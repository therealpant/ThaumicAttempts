package therealpant.thaumicattempts.items;

import net.minecraft.item.Item;
import therealpant.thaumicattempts.ThaumicAttempts;

public class ItemStorageBell extends Item {
    public ItemStorageBell() {
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".storage_bell");
        setRegistryName(ThaumicAttempts.MODID, "storage_bell");
        setMaxStackSize(1);
    }
}
