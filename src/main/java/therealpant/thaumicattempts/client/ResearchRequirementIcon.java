package therealpant.thaumicattempts.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import thaumcraft.api.items.ItemsTC;
import therealpant.thaumicattempts.init.ModBlocksItems;

/** Supplies true item-rendered icons for Thaumonomicon scan requirements. */
public final class ResearchRequirementIcon {
    private static final long SEAL_CYCLE_MILLIS = 1000L;
    private static final NonNullList<ItemStack> SEAL_ICONS = NonNullList.create();

    private ResearchRequirementIcon() {}

    public static Object resolve(Object original, String researchKey) {
        if (researchKey == null) return original;
        String key = researchKey.startsWith("!") ? researchKey.substring(1) : researchKey;
        ItemStack icon;
        switch (key) {
            case "TA_NAVIGATION_PEARL_SCAN":
                icon = new ItemStack(ItemsTC.primordialPearl);
                break;
            case "TA_NAVIGATION_STAR_SCAN":
                icon = new ItemStack(Items.NETHER_STAR);
                break;
            case "TA_CRIMSON_RITES_SCAN":
                icon = new ItemStack(ItemsTC.curio, 1, 6);
                break;
            case "TA_TWISTED_CURIOSITY_SCAN":
                icon = new ItemStack(ItemsTC.curio, 1, 5);
                break;
            case "TA_ELDRITCH_CURIOSITY_SCAN":
                icon = new ItemStack(ItemsTC.curio, 1, 2);
                break;
            case "TA_ANCIENT_CURIOSITY_SCAN":
                icon = new ItemStack(ItemsTC.curio, 1, 3);
                break;
            case "TA_STORAGE_BELL_SCAN":
                icon = new ItemStack(ModBlocksItems.STORAGE_BELL);
                break;
            case "TA_GOLEM_SEAL_SCAN":
                icon = getCyclingSealIcon();
                break;
            default:
                return original;
        }

        // TC6 blue-tints unknown research keys before selecting the render
        // branch. Item icons must start from neutral white, just like icons
        // belonging to ordinary ResearchEntries.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return icon;
    }

    private static ItemStack getCyclingSealIcon() {
        if (SEAL_ICONS.isEmpty()) {
            ItemsTC.seals.getSubItems(CreativeTabs.SEARCH, SEAL_ICONS);
        }
        if (SEAL_ICONS.isEmpty()) {
            return new ItemStack(ItemsTC.seals);
        }

        int index = (int) ((System.currentTimeMillis() / SEAL_CYCLE_MILLIS) % SEAL_ICONS.size());
        return SEAL_ICONS.get(index);
    }
}
