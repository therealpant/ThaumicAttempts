package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockMirrorManagerBase extends Block {
    public BlockMirrorManagerBase() {
        super(Material.ROCK);
        setHardness(2.5F);
        setResistance(12.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".mirror_manager_base");
        setRegistryName(ThaumicAttempts.MODID, "mirror_manager_base");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }
}
