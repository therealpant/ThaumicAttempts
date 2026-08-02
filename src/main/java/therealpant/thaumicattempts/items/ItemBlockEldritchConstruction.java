package therealpant.thaumicattempts.items;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import therealpant.thaumicattempts.golemnet.block.BlockEldritchConstarction;

public class ItemBlockEldritchConstruction extends ItemBlock {
    public ItemBlockEldritchConstruction(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return BlockEldritchConstarction.Variant.fromMeta(damage).getMeta();
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        BlockEldritchConstarction.Variant variant =
                BlockEldritchConstarction.Variant.fromMeta(stack.getMetadata());
        if (variant == BlockEldritchConstarction.Variant.BASE) {
            return super.getTranslationKey(stack);
        }
        return super.getTranslationKey(stack) + "." + variant.getName();
    }
}
