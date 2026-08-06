package therealpant.thaumicattempts.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import thaumcraft.api.items.ItemsTC;

/** Supplies true item-rendered icons for Thaumonomicon scan requirements. */
public final class ResearchRequirementIcon {
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
            default:
                return original;
        }

        // TC6 blue-tints unknown research keys before selecting the render
        // branch. Item icons must start from neutral white, just like icons
        // belonging to ordinary ResearchEntries.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return icon;
    }
}
