package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockDarkJasperFull extends Block {
    public BlockDarkJasperFull(String name) {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.STONE);
        setTranslationKey(ThaumicAttempts.MODID + "." + name);
        setRegistryName(ThaumicAttempts.MODID, name);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }
}
