package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockAuraBoosterCore extends Block {

    public BlockAuraBoosterCore() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".aura_booster_core");
        setRegistryName(ThaumicAttempts.MODID, "aura_booster_core");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }
}