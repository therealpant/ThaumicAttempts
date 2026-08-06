package therealpant.thaumicattempts.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import therealpant.thaumicattempts.ThaumicAttempts;

/** Alchemical protocol fuel with eight hidden wear states stored as metadata. */
public class ItemAlchemicalCore extends Item {
    public static final int USES = 8;
    public static final int MAX_WEAR = USES - 1;
    public static final int DAMAGED_STAGE_START = 3;
    public static final int CRITICAL_STAGE_START = 6;

    public ItemAlchemicalCore() {
        setHasSubtypes(true);
        setMaxStackSize(1);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".alchemical_core");
        setRegistryName(ThaumicAttempts.MODID, "alchemical_core");
    }

    @Override
    public int getMetadata(int damage) {
        return MathHelper.clamp(damage, 0, MAX_WEAR);
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) items.add(new ItemStack(this, 1, 0));
    }

    /** No vanilla durability is used, so the durability bar remains hidden. */
    public static boolean consumeUse(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemAlchemicalCore)) return false;
        int nextWear = stack.getMetadata() + 1;
        if (nextWear >= USES) {
            stack.shrink(1);
            return true;
        }
        stack.setItemDamage(nextWear);
        return false;
    }
}
