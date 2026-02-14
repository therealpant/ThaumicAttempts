package therealpant.thaumicattempts.client.tooltip;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.data.enchantments.TAInfusionEnchantmentData;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class TAInfusionEnchantTooltipHandler {

    private TAInfusionEnchantTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;

        int momentum = TAInfusionEnchantmentData.getLevel(stack, TAInfusionEnchantmentData.ENCH_RIFT_MOMENTUM);
        if (momentum > 0) {
            event.getToolTip().add(TextFormatting.GOLD
                    + I18n.format("enchantment.thaumicattempts.rift_momentum")
                    + " " + roman(momentum));
        }

        int guard = TAInfusionEnchantmentData.getLevel(stack, TAInfusionEnchantmentData.ENCH_ARCANE_GUARD);
        if (guard > 0) {
            event.getToolTip().add(TextFormatting.GOLD
                    + I18n.format("enchantment.thaumicattempts.arcane_guard")
                    + " " + guard);
        }
    }

    private static String roman(int n) {
        if (n <= 1) return "I";
        if (n == 2) return "II";
        if (n == 3) return "III";
        if (n == 4) return "IV";
        return "V";
    }
}
