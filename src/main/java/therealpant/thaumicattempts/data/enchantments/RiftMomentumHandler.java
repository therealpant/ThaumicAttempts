package therealpant.thaumicattempts.data.enchantments;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;
import therealpant.thaumicattempts.ThaumicAttempts;

import java.lang.reflect.Method;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class RiftMomentumHandler {

    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("5f7df2e0-2ee4-48b6-8cd4-8a8fe0cf827f");
    private static final String NBT_ACTIVE_UNTIL = "ta_rift_momentum_active_until";
    private static final String NBT_LAST_DRAIN = "ta_rift_momentum_last_drain";

    private RiftMomentumHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) return;

        long now = player.world.getTotalWorldTime();
        NBTTagCompound data = player.getEntityData();

        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();
        int mainLevel = TAInfusionEnchantmentData.getLevel(main, TAInfusionEnchantmentData.ENCH_RIFT_MOMENTUM);
        int offLevel = TAInfusionEnchantmentData.getLevel(off, TAInfusionEnchantmentData.ENCH_RIFT_MOMENTUM);
        int maxLevel = Math.max(mainLevel, offLevel);

        if (maxLevel <= 0) {
            data.setLong(NBT_ACTIVE_UNTIL, 0L);
            removeAttackSpeed(player);
            return;
        }

        long lastDrain = data.getLong(NBT_LAST_DRAIN);
        if (now - lastDrain >= 20L) {
            data.setLong(NBT_LAST_DRAIN, now);
            if (drainArmorCharge(player, 1)) {
                data.setLong(NBT_ACTIVE_UNTIL, now + 20L);
            } else {
                data.setLong(NBT_ACTIVE_UNTIL, 0L);
            }
        }

        boolean active = data.getLong(NBT_ACTIVE_UNTIL) > now;
        float attackBonus = active ? getAttackSpeedBonusPercent(maxLevel) : 0.0f;
        if (attackBonus > 0.0f && (isWeapon(main) || isWeapon(off))) {
            applyAttackSpeed(player, attackBonus);
        } else {
            removeAttackSpeed(player);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntityPlayer() == null) return;
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = player.getHeldItemMainhand();
        int lvl = TAInfusionEnchantmentData.getLevel(held, TAInfusionEnchantmentData.ENCH_RIFT_MOMENTUM);
        if (lvl <= 0 || !isTool(held)) return;
        if (!hasArmorCharge(player)) return;

        int vanillaEff = net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
                net.minecraft.init.Enchantments.EFFICIENCY,
                held
        );
        int totalEff = vanillaEff + lvl;
        float extra = efficiencyBonus(totalEff) - efficiencyBonus(vanillaEff);
        if (extra > 0.0f) {
            event.setNewSpeed(event.getNewSpeed() + extra);
        }
    }

    private static float efficiencyBonus(int level) {
        if (level <= 0) return 0.0f;
        return (float) (level * level + 1);
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof net.minecraft.item.ItemSword || stack.getItem() instanceof ItemAxe;
    }

    private static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemTool;
    }

    private static float getAttackSpeedBonusPercent(int lvl) {
        if (lvl == 1) return 0.10f;
        if (lvl == 2) return 0.20f;
        return 0.30f;
    }

    private static void applyAttackSpeed(EntityPlayer player, float bonusPct) {
        AttributeModifier existing = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getModifier(ATTACK_SPEED_UUID);
        if (existing != null) {
            player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).removeModifier(existing);
        }
        AttributeModifier modifier = new AttributeModifier(ATTACK_SPEED_UUID, "ta_rift_momentum_attack_speed", bonusPct, 1);
        player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).applyModifier(modifier);
    }

    private static void removeAttackSpeed(EntityPlayer player) {
        AttributeModifier existing = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getModifier(ATTACK_SPEED_UUID);
        if (existing != null) {
            player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).removeModifier(existing);
        }
    }

    private static boolean hasArmorCharge(EntityPlayer player) {
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack == null || stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof IRechargable)) continue;
            if (RechargeHelper.getCharge(stack) > 0) return true;
        }
        return false;
    }

    private static boolean drainArmorCharge(EntityPlayer player, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.inventory.armorInventory) {
            if (remaining <= 0) return true;
            if (stack == null || stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof IRechargable)) continue;
            int charge = RechargeHelper.getCharge(stack);
            if (charge <= 0) continue;
            int toDrain = Math.min(remaining, charge);
            if (consumeCharge(stack, player, toDrain)) {
                remaining -= toDrain;
            }
        }
        return remaining <= 0;
    }

    private static boolean consumeCharge(ItemStack stack, EntityPlayer player, int amount) {
        try {
            Method method = RechargeHelper.class.getMethod("consumeCharge", ItemStack.class, net.minecraft.entity.EntityLivingBase.class, int.class);
            Object result = method.invoke(null, stack, player, amount);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) {
        }
        try {
            Method method = RechargeHelper.class.getMethod("consumeCharge", ItemStack.class, EntityPlayer.class, int.class);
            Object result = method.invoke(null, stack, player, amount);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
