package therealpant.thaumicattempts.world.item;

import net.minecraft.block.Block;
import net.minecraft.item.ItemSeeds;
import therealpant.thaumicattempts.ThaumicAttempts;

public class ItemAnomalySeeds extends ItemSeeds {
    public ItemAnomalySeeds(Block crops, Block soil) {
        super(crops, soil);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_seeds");
        setRegistryName(ThaumicAttempts.MODID, "ta_anomaly_seeds");
    }
}