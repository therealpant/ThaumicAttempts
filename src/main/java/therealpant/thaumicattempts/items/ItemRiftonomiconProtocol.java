package therealpant.thaumicattempts.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import therealpant.thaumicattempts.ThaumicAttempts;

public class ItemRiftonomiconProtocol extends Item {
    public static final int META_NAVIGATION_PROTOCOL = 0;
    public static final int META_SPATIAL_SUBSYSTEM = 1;
    public static final int META_ALCHEMICAL_PROTOCOL = 2;
    public static final int META_CATALYZING_SUBSYSTEM = 3;
    public static final int META_TRANSMUTATION_PROTOCOL = 4;
    public static final int META_PRISMATIC_SUBSYSTEM = 5;
    public static final int META_EXTRACTION_PROTOCOL = 6;
    public static final int META_SCALING_SUBSYSTEM = 7;
    public static final int VARIANT_COUNT = 8;

    private static final String[] VARIANT_KEYS = {
            "navigation_protocol",
            "spatial_subsystem",
            "alchemical_protocol",
            "catalyzing_subsystem",
            "transmutation_protocol",
            "prismatic_subsystem",
            "extraction_protocol",
            "scaling_subsystem"
    };

    public ItemRiftonomiconProtocol() {
        setHasSubtypes(true);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".riftonomicon_protocol");
        setRegistryName(ThaumicAttempts.MODID, "riftonomicon_protocol");
    }

    @Override
    public int getMetadata(int damage) {
        return clampMeta(damage);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = clampMeta(stack.getItemDamage());
        return "item." + ThaumicAttempts.MODID + ".riftonomicon_protocol." + VARIANT_KEYS[meta];
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
