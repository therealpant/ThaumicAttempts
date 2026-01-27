package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import therealpant.thaumicattempts.ThaumicAttempts;

public class BlockRistCristalBlock extends Block {

    public BlockRistCristalBlock() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(10.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".rist_cristal_block");
        setRegistryName(ThaumicAttempts.MODID, "rist_cristal_block");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }
}