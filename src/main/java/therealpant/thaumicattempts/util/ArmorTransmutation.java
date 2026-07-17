package therealpant.thaumicattempts.util;

import com.google.common.collect.Multimap;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.Map;
import java.util.UUID;

/** Persistent, stack-specific bonuses produced by armor transmutation. */
public final class ArmorTransmutation {
    public static final String TAG_ARMOR_LEVEL = "TA.TransmutationArmor";
    public static final String TAG_TOUGHNESS_LEVEL = "TA.TransmutationToughness";
    public static final String TAG_FALL_LEVEL = "TA.TransmutationFall";

    private static final double FALL_REDUCTION_PER_LEVEL = 0.10D;
    private static final double MAX_FALL_REDUCTION = 0.80D;

    private static final UUID[] ARMOR_UUIDS = new UUID[]{
            UUID.fromString("15de2f31-b0a8-4c73-83e1-fb54ec026af0"),
            UUID.fromString("d8dd0b44-40c1-4e7e-b927-62e0e757716d"),
            UUID.fromString("e8fdb54c-0adb-4a45-828d-4fc036fca962"),
            UUID.fromString("0d243c29-17c7-484e-a451-2e0455ab04c0")
    };
    private static final UUID[] TOUGHNESS_UUIDS = new UUID[]{
            UUID.fromString("59fe857f-5563-45cb-8b9d-d12e028fa2cc"),
            UUID.fromString("ee458b01-e013-4182-b1b7-4423803bc007"),
            UUID.fromString("c57f889d-0065-4dca-95a6-214d35043eaf"),
            UUID.fromString("8c894081-6ed8-4318-9778-fc84a39fe135")
    };

    private ArmorTransmutation() {}

    public static void addArmor(ItemStack stack) {
        tag(stack).setInteger(TAG_ARMOR_LEVEL, getLevel(stack, TAG_ARMOR_LEVEL) + 1);
        refreshAttributeModifiers(stack);
    }

    public static void addToughness(ItemStack stack) {
        tag(stack).setInteger(TAG_TOUGHNESS_LEVEL, getLevel(stack, TAG_TOUGHNESS_LEVEL) + 1);
        refreshAttributeModifiers(stack);
    }

    public static void addFallProtection(ItemStack stack) {
        tag(stack).setInteger(TAG_FALL_LEVEL, getLevel(stack, TAG_FALL_LEVEL) + 1);
    }

    public static float getFallDamageMultiplier(EntityPlayer player) {
        int levels = 0;
        for (ItemStack stack : player.inventory.armorInventory) {
            levels += getLevel(stack, TAG_FALL_LEVEL);
        }
        double reduction = Math.min(MAX_FALL_REDUCTION, levels * FALL_REDUCTION_PER_LEVEL);
        return (float) (1.0D - reduction);
    }

    public static int getLevel(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return 0;
        return Math.max(0, stack.getTagCompound().getInteger(key));
    }

    private static void refreshAttributeModifiers(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) return;
        EntityEquipmentSlot slot = ((ItemArmor) stack.getItem()).armorType;
        int slotIndex = slot.getIndex();
        UUID armorUuid = ARMOR_UUIDS[slotIndex];
        UUID toughnessUuid = TOUGHNESS_UUIDS[slotIndex];

        Multimap<String, AttributeModifier> original = stack.getAttributeModifiers(slot);
        NBTTagList result = new NBTTagList();
        for (Map.Entry<String, AttributeModifier> entry : original.entries()) {
            AttributeModifier modifier = entry.getValue();
            if (armorUuid.equals(modifier.getID()) || toughnessUuid.equals(modifier.getID())) continue;
            writeModifier(result, entry.getKey(), slot, modifier);
        }

        int armor = getLevel(stack, TAG_ARMOR_LEVEL);
        int toughness = getLevel(stack, TAG_TOUGHNESS_LEVEL);
        if (armor > 0) {
            writeModifier(result, SharedMonsterAttributes.ARMOR.getName(), slot,
                    new AttributeModifier(armorUuid, "ta_transmutation_armor", armor, 0));
        }
        if (toughness > 0) {
            writeModifier(result, SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), slot,
                    new AttributeModifier(toughnessUuid, "ta_transmutation_toughness", toughness, 0));
        }
        tag(stack).setTag("AttributeModifiers", result);
    }

    private static void writeModifier(NBTTagList list, String attribute, EntityEquipmentSlot slot,
                                      AttributeModifier modifier) {
        NBTTagCompound data = SharedMonsterAttributes.writeAttributeModifierToNBT(modifier);
        data.setString("AttributeName", attribute);
        data.setString("Slot", slot.getName());
        list.appendTag(data);
    }

    private static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
}
