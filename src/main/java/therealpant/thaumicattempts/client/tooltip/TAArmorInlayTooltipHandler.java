package therealpant.thaumicattempts.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.util.TAGemInlayUtil;
import therealpant.thaumicattempts.util.TAGemPlayerUtil;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class TAArmorInlayTooltipHandler {

    private TAArmorInlayTooltipHandler() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) {
            return;
        }
        if (!TAGemInlayUtil.hasGem(stack)) {
            return;
        }
        ResourceLocation gemId = TAGemInlayUtil.getGemId(stack);
        if (gemId == null) {
            return;
        }
        int tier = TAGemInlayUtil.getTier(stack);
        EntityPlayer player = Minecraft.getMinecraft().player;
        int sameGemCount = TAGemPlayerUtil.getSameGemCount(player, gemId);
        int setCount = player == null ? 0 : sameGemCount;

        String tierName = I18n.format(getTierNameKey(tier));
        String tierRoman = I18n.format(getTierRomanKey(tier));
        String gemName = I18n.format(getGemNameKey(gemId));
        String inlayValue = I18n.format(
                "tooltip.thaumicattempts.inlay",
                TextFormatting.GRAY + tierName,
                TextFormatting.GRAY + tierRoman,
                TextFormatting.GRAY + gemName
        );
        event.getToolTip().add(TextFormatting.DARK_GRAY + inlayValue);

        int passiveValue = getPassiveValue(gemId, tier, sameGemCount);
        String passiveDetail = I18n.format(getPassiveKey(gemId), passiveValue);
        String passiveText = I18n.format("tooltip.thaumicattempts.passive", passiveDetail);
        event.getToolTip().add(TextFormatting.GREEN + passiveText);

        String setName = I18n.format(getGemSetKey(gemId));
        String smallSet = I18n.format("tooltip.thaumicattempts.set.small", setName);
        String fullSet = I18n.format("tooltip.thaumicattempts.set.full", setName);

        TextFormatting smallColor = setCount >= 2 ? TextFormatting.GREEN : TextFormatting.DARK_GRAY;
        TextFormatting fullColor = setCount >= 4 ? TextFormatting.GREEN : TextFormatting.DARK_GRAY;
        event.getToolTip().add(smallColor + smallSet);
        event.getToolTip().add(fullColor + fullSet);
    }

    private static String getTierNameKey(int tier) {
        switch (tier) {
            case 1:
                return "tooltip.thaumicattempts.tier.simple";
            case 2:
                return "tooltip.thaumicattempts.tier.improved";
            case 3:
                return "tooltip.thaumicattempts.tier.exquisite";
            default:
                return "tooltip.thaumicattempts.tier.simple";
        }
    }

    private static String getTierRomanKey(int tier) {
        switch (tier) {
            case 1:
                return "tooltip.thaumicattempts.tier.roman.1";
            case 2:
                return "tooltip.thaumicattempts.tier.roman.2";
            case 3:
                return "tooltip.thaumicattempts.tier.roman.3";
            default:
                return "tooltip.thaumicattempts.tier.roman.1";
        }
    }

    private static String getGemNameKey(ResourceLocation gemId) {
        if (AmberGemDefinition.ID.equals(gemId)) {
            return "tooltip.thaumicattempts.gem.amber";
        }
        if (AmethystGemDefinition.ID.equals(gemId)) {
            return "tooltip.thaumicattempts.gem.amethyst";
        }
        return "tooltip.thaumicattempts.gem.diamond";
    }

    private static String getGemSetKey(ResourceLocation gemId) {
        if (AmberGemDefinition.ID.equals(gemId)) {
            return "tooltip.thaumicattempts.gem.amber.set";
        }
        if (AmethystGemDefinition.ID.equals(gemId)) {
            return "tooltip.thaumicattempts.gem.amethyst.set";
        }
        return "tooltip.thaumicattempts.gem.diamond.set";
    }

    private static String getPassiveKey(ResourceLocation gemId) {
        if (AmberGemDefinition.ID.equals(gemId)) {
            return "tooltip.thaumicattempts.passive.amber";
        }
        if (AmethystGemDefinition.ID.equals(gemId)) {
            return "tooltip.thaumicattempts.passive.amethyst";
        }
        return "tooltip.thaumicattempts.passive.diamond";
    }

    private static int getPassiveValue(ResourceLocation gemId, int tier, int sameGemCount) {
        int base;
        if (AmberGemDefinition.ID.equals(gemId)) {
            base = getTierValue(tier, 5, 6, 7);
        } else if (AmethystGemDefinition.ID.equals(gemId)) {
            base = getTierValue(tier, 3, 4, 5);
        } else {
            base = getTierValue(tier, 3, 4, 5);
        }
        return base * Math.max(1, sameGemCount);
    }

    private static int getTierValue(int tier, int tier1, int tier2, int tier3) {
        switch (tier) {
            case 1:
                return tier1;
            case 2:
                return tier2;
            case 3:
                return tier3;
            default:
                return tier1;
        }
    }
}