package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockEldritchConstarction extends Block {

    public BlockEldritchConstarction() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".eldritch_constarction");
        setRegistryName(ThaumicAttempts.MODID, "eldritch_constarction");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }
}