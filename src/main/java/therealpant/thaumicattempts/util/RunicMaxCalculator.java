package therealpant.thaumicattempts.util;

import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import therealpant.thaumicattempts.data.enchantments.TAInfusionEnchantmentData;

public final class RunicMaxCalculator {
    private static final String RUNIC_TAG = "TC.RUNIC";
    private static final EntityEquipmentSlot[] ARMOR_SLOTS = new EntityEquipmentSlot[]{
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
    };

    private RunicMaxCalculator() {}

    public static int getRunicMax(EntityPlayer player) {
        if (player == null) return 0;
        int total = 0;
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            total += getRunicValue(player.getItemStackFromSlot(slot));
        }
        total += getBaublesRunic(player);
        return Math.max(0, total);
    }

    private static int getRunicValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int customGuard = TAInfusionEnchantmentData.getLevel(stack, TAInfusionEnchantmentData.ENCH_ARCANE_GUARD);
        NBTTagCompound tag = stack.getTagCompound();
        int thaumcraftRunic = 0;
        if (tag != null && tag.hasKey(RUNIC_TAG)) {
            thaumcraftRunic = tag.getInteger(RUNIC_TAG);
        }
        return Math.max(0, Math.max(customGuard, thaumcraftRunic));
    }

    private static int getBaublesRunic(EntityPlayer player) {
        if (player == null) return 0;
        if (!isBaublesLoaded()) return 0;
        try {
            Class<?> api = Class.forName("baubles.api.BaublesApi");
            Method getHandler = api.getMethod("getBaublesHandler", EntityPlayer.class);
            Object handler = getHandler.invoke(null, player);
            if (handler == null) return 0;
            Method getSlots = handler.getClass().getMethod("getSlots");
            Method getStack = handler.getClass().getMethod("getStackInSlot", int.class);
            int slots = (int) getSlots.invoke(handler);
            int total = 0;
            for (int i = 0; i < slots; i++) {
                Object stackObj = getStack.invoke(handler, i);
                if (stackObj instanceof ItemStack) {
                    total += getRunicValue((ItemStack) stackObj);
                }
            }
            return total;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isBaublesLoaded() {
        try {
            return Loader.isModLoaded("baubles");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
