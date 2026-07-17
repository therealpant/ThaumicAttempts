package therealpant.thaumicattempts.util;

import net.minecraft.item.ItemStack;
import thaumcraft.api.casters.FocusModSplit;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.common.items.casters.ItemFocus;
import therealpant.thaumicattempts.items.ItemPrism;

import java.lang.reflect.Field;
import java.util.Locale;

/** Direct, permanent editing of the serialized settings inside a TC6 focus. */
public final class FocusTransmutation {
    private FocusTransmutation() {}

    public static boolean addForPrism(ItemStack stack, int prismMeta) {
        return visit(stack, prismMeta, true);
    }

    public static boolean hasSettingForPrism(ItemStack stack, int prismMeta) {
        return visit(stack, prismMeta, false);
    }

    private static boolean visit(ItemStack stack, int prismMeta, boolean increment) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemFocus)) return false;
        ItemFocus focus = (ItemFocus) stack.getItem();
        FocusPackage focusPackage = focus.getPackage(stack);
        if (focusPackage == null || focusPackage.nodes == null) return false;

        boolean found = visitPackage(focusPackage, prismMeta, increment);
        if (found && increment) focus.setPackage(stack, focusPackage);
        return found;
    }

    /** Visits the main chain and every package stored inside Split nodes. */
    private static boolean visitPackage(FocusPackage focusPackage, int prismMeta, boolean increment) {
        if (focusPackage == null || focusPackage.nodes == null) return false;
        boolean found = false;
        for (IFocusElement element : focusPackage.nodes) {
            if (element instanceof FocusNode) {
                FocusNode node = (FocusNode) element;
                for (String key : node.getSettingList()) {
                    if (!matchesPrism(key, prismMeta)) continue;
                    NodeSetting setting = node.getSetting(key);
                    if (setting == null) continue;
                    if (increment) setValueUnclamped(setting, setting.getValue() + 1);
                    found = true;
                }
            }

            if (element instanceof FocusModSplit) {
                for (Object child : ((FocusModSplit) element).getSplitPackages()) {
                    if (child instanceof FocusPackage) {
                        found |= visitPackage((FocusPackage) child, prismMeta, increment);
                    }
                }
            } else if (element instanceof FocusPackage) {
                found |= visitPackage((FocusPackage) element, prismMeta, increment);
            }
        }
        return found;
    }

    private static boolean matchesPrism(String key, int meta) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (meta == ItemPrism.META_IGNIS) return "power".equals(normalized);
        if (meta == ItemPrism.META_TERRA) {
            // Thaumcraft's Fire node calls its setting "duration" internally,
            // while displaying it as "Burn duration". Aliases also cover addons.
            return "duration".equals(normalized)
                    || "burn".equals(normalized)
                    || "burnduration".equals(normalized)
                    || "burn_duration".equals(normalized);
        }
        if (meta == ItemPrism.META_AIR) return "radius".equals(normalized);
        if (meta == ItemPrism.META_AQUA) {
            return "fork".equals(normalized) || "forks".equals(normalized);
        }
        return false;
    }

    /**
     * Applies a serialized/transmuted value without Thaumcraft's crafting-table cap.
     * The range is widened only on this NodeSetting instance, so the focus table
     * retains its normal limits while already-transmuted values survive loading.
     */
    public static void setValueUnclamped(NodeSetting setting, int value) {
        if (setting == null) return;
        prepareValueRange(setting, value);
        setting.setValue(value);
    }

    /** Called by the NodeSetting core hook before Thaumcraft applies its clamp. */
    public static void prepareValueRange(NodeSetting setting, int value) {
        if (setting == null) return;
        Object type = setting.getType();
        if (!(type instanceof NodeSetting.NodeSettingIntRange)) return;
        try {
            Field max = NodeSetting.NodeSettingIntRange.class.getDeclaredField("max");
            max.setAccessible(true);
            if (value > max.getInt(type)) max.setInt(type, value);
        } catch (ReflectiveOperationException ignored) {
            // Fail safely with Thaumcraft's normal capped value.
        }
    }
}
