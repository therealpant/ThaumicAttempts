package therealpant.thaumicattempts.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import therealpant.thaumicattempts.ThaumicAttempts;

public class ItemPrism extends Item {
    public static final int META_AIR = 0;
    public static final int META_AQUA = 1;
    public static final int META_IGNIS = 2;
    public static final int META_TERRA = 3;
    public static final int VARIANT_COUNT = 4;

    private static final String[] VARIANT_KEYS = {
            "air",
            "aqua",
            "ignis",
            "terra"
    };

    public ItemPrism() {
        setHasSubtypes(true);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".prism");
        setRegistryName(ThaumicAttempts.MODID, "prism");
    }

    @Override
    public int getMetadata(int damage) {
        return clampMeta(damage);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = clampMeta(stack.getItemDamage());
        return "item." + ThaumicAttempts.MODID + ".prism." + VARIANT_KEYS[meta];
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int meta = 0; meta < VARIANT_COUNT; meta++) {
            items.add(new ItemStack(this, 1, meta));
        }
    }

    private static int clampMeta(int meta) {
        return MathHelper.clamp(meta, 0, VARIANT_COUNT - 1);
    }
}
