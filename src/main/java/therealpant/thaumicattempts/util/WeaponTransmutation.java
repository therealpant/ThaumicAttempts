package therealpant.thaumicattempts.util;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import com.google.common.collect.Multimap;

import java.util.Map;
import java.util.UUID;

/** Persistent, stack-specific bonuses produced by weapon transmutation. */
public final class WeaponTransmutation {
    public static final String TAG_DAMAGE_LEVEL = "TA.TransmutationDamage";
    public static final String TAG_SPEED_LEVEL = "TA.TransmutationSpeed";
    public static final String TAG_DURABILITY_RESERVE = "TA.TransmutationDurability";
    public static final String TAG_TOTAL = "TA.TransmutationTotal";
    public static final String TAG_CREATED = "TA.TransmutationCreated";
    public static final String TAG_AIR = "TA.TransmutationAir";
    public static final String TAG_TERRA = "TA.TransmutationTerra";
    public static final String TAG_AQUA = "TA.TransmutationAqua";
    public static final String TAG_IGNIS = "TA.TransmutationIgnis";

    private static final UUID DAMAGE_UUID = UUID.fromString("734f0675-c353-4e76-9102-3b1aa0427991");
    private static final UUID SPEED_UUID = UUID.fromString("909e93dd-1a7a-48c8-a544-1222b760f8ca");

    private WeaponTransmutation() {}

    public static void addDamage(ItemStack stack) {
        int previous = get(stack, TAG_DAMAGE_LEVEL);
        tag(stack).setInteger(TAG_DAMAGE_LEVEL, previous + 1);
        refreshAttributeModifiers(stack, previous, get(stack, TAG_SPEED_LEVEL));
    }

    public static void addSpeed(ItemStack stack) {
        int previous = get(stack, TAG_SPEED_LEVEL);
        tag(stack).setInteger(TAG_SPEED_LEVEL, previous + 1);
        refreshAttributeModifiers(stack, get(stack, TAG_DAMAGE_LEVEL), previous);
    }

    public static void addDurability(ItemStack stack) {
        tag(stack).setInteger(TAG_DURABILITY_RESERVE, get(stack, TAG_DURABILITY_RESERVE) + 100);
    }

    /** Converts stacks made by the first implementation, which used extra tooltip modifiers. */
    public static void normalizeLegacyModifiers(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) return;
        boolean legacy = false;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND).values()) {
            if (DAMAGE_UUID.equals(modifier.getID()) || SPEED_UUID.equals(modifier.getID())) {
                legacy = true;
                break;
            }
        }
        if (legacy) {
            refreshAttributeModifiers(stack, get(stack, TAG_DAMAGE_LEVEL), get(stack, TAG_SPEED_LEVEL));
        }
    }

    private static void refreshAttributeModifiers(ItemStack stack, int previousDamage, int previousSpeed) {
        Multimap<String, AttributeModifier> original = stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
        NBTTagList result = new NBTTagList();
        boolean hadSeparateDamage = false;
        boolean hadSeparateSpeed = false;
        double currentAttackSpeed = 4.0D;
        for (Map.Entry<String, AttributeModifier> entry : original.entries()) {
            AttributeModifier modifier = entry.getValue();
            if (DAMAGE_UUID.equals(modifier.getID())) {
                hadSeparateDamage = true;
                continue;
            }
            if (SPEED_UUID.equals(modifier.getID())) {
                hadSeparateSpeed = true;
                continue;
            }
            if (SharedMonsterAttributes.ATTACK_SPEED.getName().equals(entry.getKey())
                    && modifier.getOperation() == 0) currentAttackSpeed += modifier.getAmount();
        }

        int damageLevels = get(stack, TAG_DAMAGE_LEVEL);
        int speedLevels = get(stack, TAG_SPEED_LEVEL);
        double damageIncrease = hadSeparateDamage ? damageLevels : damageLevels - previousDamage;
        double originalInterval = currentAttackSpeed > 0.0D
                ? 1.0D / currentAttackSpeed + (hadSeparateSpeed ? 0.0D : 0.1D * previousSpeed)
                : 0.0D;
        double targetSpeed = originalInterval > 0.0D
                ? 1.0D / Math.max(0.05D, originalInterval - 0.1D * speedLevels)
                : currentAttackSpeed;
        double speedIncrease = targetSpeed - currentAttackSpeed;

        boolean damageChanged = false;
        boolean speedChanged = false;
        for (Map.Entry<String, AttributeModifier> entry : original.entries()) {
            AttributeModifier modifier = entry.getValue();
            if (DAMAGE_UUID.equals(modifier.getID()) || SPEED_UUID.equals(modifier.getID())) continue;
            if (!damageChanged && damageIncrease != 0.0D
                    && SharedMonsterAttributes.ATTACK_DAMAGE.getName().equals(entry.getKey())
                    && modifier.getOperation() == 0) {
                modifier = withAmount(modifier, modifier.getAmount() + damageIncrease);
                damageChanged = true;
            } else if (!speedChanged && speedIncrease != 0.0D
                    && SharedMonsterAttributes.ATTACK_SPEED.getName().equals(entry.getKey())
                    && modifier.getOperation() == 0) {
                modifier = withAmount(modifier, modifier.getAmount() + speedIncrease);
                speedChanged = true;
            }
            writeModifier(result, entry.getKey(), modifier);
        }
        tag(stack).setTag("AttributeModifiers", result);
    }

    private static AttributeModifier withAmount(AttributeModifier original, double amount) {
        return new AttributeModifier(original.getID(), original.getName(), amount, original.getOperation());
    }

    private static void writeModifier(NBTTagList list, String attribute, AttributeModifier modifier) {
        NBTTagCompound data = SharedMonsterAttributes.writeAttributeModifierToNBT(modifier);
        data.setString("AttributeName", attribute);
        data.setString("Slot", "mainhand");
        list.appendTag(data);
    }

    /** Converts the reserve into repairs; in total it grants exactly 100 uses per Terra prism result. */
    public static void applyDurabilityReserve(ItemStack stack) {
        if (stack.isEmpty() || !stack.isItemStackDamageable() || !stack.isItemDamaged()) return;
        int reserve = get(stack, TAG_DURABILITY_RESERVE);
        if (reserve <= 0) return;
        int repaired = Math.min(reserve, stack.getItemDamage());
        stack.setItemDamage(stack.getItemDamage() - repaired);
        tag(stack).setInteger(TAG_DURABILITY_RESERVE, reserve - repaired);
    }

    public static int getLevel(ItemStack stack, String key) {
        return get(stack, key);
    }

    public static int getTotal(ItemStack stack) { return get(stack, TAG_TOTAL); }

    public static boolean isCreated(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTagCompound()
                && stack.getTagCompound().getBoolean(TAG_CREATED);
    }

    public static void recordElement(ItemStack stack, int prismMeta) {
        String key;
        if (prismMeta == 0) key = TAG_AIR;
        else if (prismMeta == 1) key = TAG_AQUA;
        else if (prismMeta == 2) key = TAG_IGNIS;
        else key = TAG_TERRA;
        tag(stack).setInteger(key, get(stack, key) + 1);
        tag(stack).setInteger(TAG_TOTAL, getTotal(stack) + 1);
    }

    public static void makeCreated(ItemStack stack) {
        tag(stack).setBoolean(TAG_CREATED, true);
    }

    private static int get(ItemStack stack, String key) {
        NBTTagCompound compound = stack.getTagCompound();
        return compound == null ? 0 : Math.max(0, compound.getInteger(key));
    }

    private static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }
}
