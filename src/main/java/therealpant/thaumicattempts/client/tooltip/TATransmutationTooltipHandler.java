package therealpant.thaumicattempts.client.tooltip;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.util.WeaponTransmutation;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class TATransmutationTooltipHandler {
    private TATransmutationTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;
        if (WeaponTransmutation.isCreated(stack)) {
            event.getToolTip().add(TextFormatting.DARK_AQUA
                    + I18n.format("tooltip.thaumicattempts.transmutation.created"));
            return;
        }
        add(event, stack, "aer", WeaponTransmutation.TAG_AIR);
        add(event, stack, "terra", WeaponTransmutation.TAG_TERRA);
        add(event, stack, "aqua", WeaponTransmutation.TAG_AQUA);
        add(event, stack, "ignis", WeaponTransmutation.TAG_IGNIS);
    }

    private static void add(ItemTooltipEvent event, ItemStack stack, String element, String tag) {
        int count = WeaponTransmutation.getLevel(stack, tag);
        if (count > 0) {
            event.getToolTip().add(TextFormatting.DARK_AQUA
                    + I18n.format("tooltip.thaumicattempts.transmutation", elementName(element), count));
        }
    }

    private static String elementName(String element) {
        return Character.toUpperCase(element.charAt(0)) + element.substring(1);
    }
}
