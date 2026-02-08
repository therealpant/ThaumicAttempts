package therealpant.thaumicattempts.events;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.init.MobEffects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;
import therealpant.thaumicattempts.api.gems.GemDamageSource;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.capability.AmberCasterCapability;
import therealpant.thaumicattempts.capability.IAmberCasterData;
import therealpant.thaumicattempts.effects.AmberEffects;
import therealpant.thaumicattempts.effects.AmethystEffects;
import therealpant.thaumicattempts.effects.DiamondEffects;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.net.msg.S2C_AmberCountUpdate;
import therealpant.thaumicattempts.util.RunicMaxCalculator;
import therealpant.thaumicattempts.util.RunicShieldAdapter;
import therealpant.thaumicattempts.util.TAGemArmorUtil;
import therealpant.thaumicattempts.util.TAGemInlayUtil;
import therealpant.thaumicattempts.ThaumicAttempts;

import static org.apache.commons.lang3.reflect.MethodUtils.invokeMethod;
import static thaumcraft.api.items.RechargeHelper.getCharge;

public class TAGemEventHandler {
    private static final String NBT_AMETHYST_STACKS = "ta_amethyst_stacks";
    private static final String NBT_AMETHYST_EXPIRE = "ta_amethyst_expire";
    private static final String NBT_AMETHYST_LAST_RUNIC_UPDATE = "ta_amethyst_runic_update";
    private static final String NBT_AMETHYST_LAST_STACKS = "ta_amethyst_lastStacks";
    private static final String NBT_AMETHYST_LAST_TARGET = "ta_amethyst_lastTarget";
    private static final String NBT_AMETHYST_OVER_N = "ta_am_over_n";
    private static final String NBT_AMETHYST_OVER_LAST = "ta_am_over_last";
    private static final String NBT_AMETHYST_OVER_CD = "ta_am_over_cd_until";

    private static final String NBT_DIAMOND_VIS_BONUS = "ta_diamond_vis_bonus";
    private static final String NBT_DIAMOND_HITCOUNT = "ta_diamond_hitcount";
    private static final String NBT_DIAMOND_INTERNAL_CAST = "ta_diamond_internal_cast";

    private static final String RUNIC_TAG = "TC.RUNIC";
    private static final String RUNIC_BASE_TAG = "ta_am_base_runic";
    private static final String RUNIC_BONUS_TAG = "ta_am_bonus_runic";
    private static final String RUNIC_BASE_TAG_LEGACY = "TA.RUNIC_BASE";
    private static final String RUNIC_TMP_TAG_LEGACY = "TA.RUNIC_TMP";


    private static final EntityEquipmentSlot[] ARMOR_SLOTS = new EntityEquipmentSlot[]{
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
    };
    private final Map<UUID, Integer> amberCountCache = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event) {
        if (event.player == null || event.player.world == null || event.player.world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.player.world.getTotalWorldTime();

        GemSummary amberSummary = getGemSummary(event.player, AmberGemDefinition.ID);
        GemSummary amethystSummary = getGemSummary(event.player, AmethystGemDefinition.ID);

        updateAmethystState(event.player, now, amethystSummary);
        updateDiamondModifiers(event.player);
        updateAmberFrequency(event.player, now, amberSummary.count);
        syncAmberCount(event.player, amberSummary.count);
    }

    @SubscribeEvent
    public void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        if (event.isCanceled() || event.getAmount() <= 0f) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        GemSummary amethystSummary = getGemSummary(player, AmethystGemDefinition.ID);
        if (amethystSummary.count > 0) {
            applyAmethystStacks(player, amethystSummary, event.getAmount());
            damageGemInlays(player, GemDamageSource.ON_PLAYER_HURT);
        }
    }

    @SubscribeEvent
    public void onLivingHurtByPlayer(LivingHurtEvent event) {
        if (event.getEntityLiving() == null || event.getEntityLiving().world.isRemote) return;
        EntityPlayer player = resolvePlayerFromDamageSource(event.getSource());
        if (player == null) return;
        boolean melee = event.getSource().getImmediateSource() == player;

        GemSummary amberSummary = getGemSummary(player, AmberGemDefinition.ID);
        if (amberSummary.count > 0 && isFocusDamage(event.getSource(), player)) {
            float bonus = getTotalDamageBonus(amberSummary, AmberEffects::getDamageBonusPerGem);
            if (bonus > 0f) {
                event.setAmount(event.getAmount() * (1.0f + bonus));
            }
        }

        if (melee) {
            GemSummary diamondSummary = getGemSummary(player, DiamondGemDefinition.ID);
            if (diamondSummary.count > 0) {
                applyDiamondSet4VisBonus(player, diamondSummary, event);
                if (diamondSummary.count >= DiamondEffects.SET2_REQUIRED) {
                    NBTTagCompound data = getPersistedData(player);
                    if (!data.getBoolean(NBT_DIAMOND_INTERNAL_CAST)) {
                        int hits = data.getInteger(NBT_DIAMOND_HITCOUNT) + 1;
                        if (hits >= DiamondEffects.SET4_HIT_THRESHOLD) {
                            data.setInteger(NBT_DIAMOND_HITCOUNT, 0);
                            triggerDiamondFocusStrikes(player, (EntityLivingBase) event.getEntityLiving());
                        } else {
                            data.setInteger(NBT_DIAMOND_HITCOUNT, hits);
                        }
                    }
                } else {
                    NBTTagCompound data = getPersistedData(player);
                    data.setInteger(NBT_DIAMOND_HITCOUNT, 0);
                }
                damageGemInlays(player, GemDamageSource.ON_PLAYER_HIT);
            }
        }
    }


    @SubscribeEvent
    public void onFocusCast(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntityPlayer() == null) return;
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;

        ItemStack stack = event.getItemStack();
        if (!isCasterItem(stack)) return;

        GemSummary amberSummary = getGemSummary(player, AmberGemDefinition.ID);
        if (amberSummary.count <= 0) return;

        damageGemInlays(player, GemDamageSource.ON_FOCUS_CAST);
    }

    @SubscribeEvent
    public void onPlayerLogout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null || event.player.world == null || event.player.world.isRemote) return;
        amberCountCache.remove(event.player.getUniqueID());
    }


    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntityPlayer() == null || !event.isWasDeath()) return;
        clearAmethystData(event.getEntityPlayer());
        NBTTagCompound data = getPersistedData(event.getEntityPlayer());
        data.setInteger(NBT_DIAMOND_HITCOUNT, 0);
        data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, false);
        data.setFloat(NBT_DIAMOND_VIS_BONUS, 0f);
    }

    private void updateAmethystState(EntityPlayer player, long now, GemSummary summary) {
        NBTTagCompound data = getPersistedData(player);
        int stacks = data.getInteger(NBT_AMETHYST_STACKS);
        long expire = data.getLong(NBT_AMETHYST_EXPIRE);

        if (summary.count <= 0) {
            if (stacks > 0 || expire > 0) {
                data.setInteger(NBT_AMETHYST_STACKS, 0);
                data.setLong(NBT_AMETHYST_EXPIRE, 0L);
            }
            data.setInteger(NBT_AMETHYST_OVER_N, 0);
            data.setLong(NBT_AMETHYST_OVER_LAST, 0L);
            data.setLong(NBT_AMETHYST_OVER_CD, 0L);
            updateAmethystRunicBonus(player, 0, now);
            return;
        }
        if (stacks > 0 && now > expire) {
            stacks = 0;
            data.setInteger(NBT_AMETHYST_STACKS, 0);
            data.setLong(NBT_AMETHYST_EXPIRE, 0L);
        }
        updateAmethystRunicBonus(player, stacks, now);
        applyAmethystSet2Defense(player, summary);
        updateAmethystOverload(player, summary, now);
    }

    private void applyAmethystStacks(EntityPlayer player, GemSummary summary, float damageAmount) {
        if (damageAmount <= 0f) return;
        int cap = getAmethystStackCap(summary);
        if (cap <= 0) return;

        NBTTagCompound data = getPersistedData(player);
        int stacks = data.getInteger(NBT_AMETHYST_STACKS);
        int newStacks = Math.min(cap, stacks + (stacks < cap ? 1 : 0));

        data.setInteger(NBT_AMETHYST_STACKS, newStacks);
        data.setLong(NBT_AMETHYST_EXPIRE, player.world.getTotalWorldTime() + AmethystEffects.STACK_DURATION_TICKS);
    }

    private void applyAmethystSet2Defense(EntityPlayer player, GemSummary summary) {
        if (summary.count < AmethystEffects.SET2_REQUIRED) return;
        int maxRunic = RunicMaxCalculator.getRunicMax(player);
        if (maxRunic <= 0) return;
        float current = RunicShieldAdapter.getCurrentShield(player);
        float effective = Math.min(current, maxRunic);
        float percent = effective / (float) maxRunic;
        if (percent >= AmethystEffects.SET2_MAX_PERCENT || percent < AmethystEffects.SET2_MIN_PERCENT) return;

        PotionEffect existing = player.getActivePotionEffect(MobEffects.RESISTANCE);
        if (existing != null && existing.getAmplifier() > 0) return;


        PotionEffect resistance = new PotionEffect(
                MobEffects.RESISTANCE,
                AmethystEffects.SET2_RESISTANCE_DURATION_TICKS,
                0,
                false,
                true);
        player.addPotionEffect(resistance);
    }

    private void updateAmethystOverload(EntityPlayer player, GemSummary summary, long now) {
        NBTTagCompound data = getPersistedData(player);
        if (summary.count < AmethystEffects.SET4_REQUIRED) {
            data.setInteger(NBT_AMETHYST_OVER_N, 0);
            data.setLong(NBT_AMETHYST_OVER_LAST, 0L);
            data.setLong(NBT_AMETHYST_OVER_CD, 0L);
            return;
        }

        int n = data.getInteger(NBT_AMETHYST_OVER_N);
        long last = data.getLong(NBT_AMETHYST_OVER_LAST);
        long cdUntil = data.getLong(NBT_AMETHYST_OVER_CD);

        if (last > 0 && now - last > AmethystEffects.OVERLOAD_RESET_TICKS) {
            n = 0;
            data.setInteger(NBT_AMETHYST_OVER_N, 0);
        }
        if (now < cdUntil) return;
        if (!player.isSneaking()) return;
        if (last > 0 && now - last < AmethystEffects.OVERLOAD_INTERVAL_TICKS) return;


        int maxRunic = RunicMaxCalculator.getRunicMax(player);
        if (maxRunic <= 0) return;

        float current = RunicShieldAdapter.getCurrentShield(player);
        if (current >= maxRunic) return;

        int cost = getOverloadCost(n);
        if (!tryConsumeVis(player, cost)) {
            data.setLong(NBT_AMETHYST_OVER_CD, now + AmethystEffects.OVERLOAD_COOLDOWN_TICKS);
            return;
        }

        float next = Math.min(current + AmethystEffects.OVERLOAD_RESTORE_AMOUNT, maxRunic);
        RunicShieldAdapter.setCurrentShield(player, next);
        data.setInteger(NBT_AMETHYST_OVER_N, n + 1);
        data.setLong(NBT_AMETHYST_OVER_LAST, now);
    }

    private void updateAmethystRunicBonus(EntityPlayer player, int stacks, long now) {
        NBTTagCompound data = getPersistedData(player);
        int lastStacks = data.getInteger(NBT_AMETHYST_LAST_STACKS);
        int lastTarget = data.getInteger(NBT_AMETHYST_LAST_TARGET);
        long lastUpdate = data.getLong(NBT_AMETHYST_LAST_RUNIC_UPDATE);

        EntityEquipmentSlot targetSlot = findRunicTargetSlot(player);
        int targetIndex = targetSlot == null ? -1 : targetSlot.ordinal();

        boolean needsUpdate = stacks != lastStacks
                || targetIndex != lastTarget
                || now - lastUpdate >= AmethystEffects.RUNIC_UPDATE_INTERVAL_TICKS;

        if (!needsUpdate && !hasRunicTmpOnArmor(player)) {
            return;
        }

        data.setInteger(NBT_AMETHYST_LAST_STACKS, stacks);
        data.setInteger(NBT_AMETHYST_LAST_TARGET, targetIndex);
        data.setLong(NBT_AMETHYST_LAST_RUNIC_UPDATE, now);

        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (stack.isEmpty()) continue;
            boolean isTarget = stacks > 0 && slot == targetSlot;
            if (isTarget) {
                applyRunicBonus(stack, stacks);
            } else {
                clearRunicBonus(stack);
            }
        }
    }

    private void updateDiamondModifiers(EntityPlayer player) {
        GemSummary summary = getGemSummary(player, DiamondGemDefinition.ID);
        float bonus = getTotalAttackSpeedBonus(summary);
        AttributeModifier attackSpeed = new AttributeModifier(DiamondEffects.ATTACK_SPEED_UUID,
                "ta_diamond_attack_speed", bonus, 1);

        if (bonus > 0f) {
            if (player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getModifier(DiamondEffects.ATTACK_SPEED_UUID) != null) {
                player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).removeModifier(DiamondEffects.ATTACK_SPEED_UUID);
            }
            player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).applyModifier(attackSpeed);
        } else if (player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getModifier(DiamondEffects.ATTACK_SPEED_UUID) != null) {
            player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).removeModifier(DiamondEffects.ATTACK_SPEED_UUID);
        }
    }

    private void updateAmberFrequency(EntityPlayer player, long now, int amberCount) {
        IAmberCasterData data = AmberCasterCapability.get(player);
        if (data == null) return;
        data.tick(now, amberCount >= AmberEffects.SET4_REQUIRED);
    }

    private void syncAmberCount(EntityPlayer player, int amberCount) {
        if (!(player instanceof EntityPlayerMP)) return;
        UUID playerId = player.getUniqueID();
        Integer last = amberCountCache.get(playerId);
        if (last != null && last == amberCount) return;
        amberCountCache.put(playerId, amberCount);
        ThaumicAttempts.NET.sendTo(new S2C_AmberCountUpdate(playerId, amberCount), (EntityPlayerMP) player);
    }

    private int getAmethystStackCap(GemSummary summary) {
        int maxStacks = 0;
        for (int tier : summary.tiers) {
            maxStacks += AmethystEffects.getStackBonusForTier(tier);
        }
        return maxStacks;
    }

    private EntityEquipmentSlot findRunicTargetSlot(EntityPlayer player) {
        ItemStack chest = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!chest.isEmpty()) return EntityEquipmentSlot.CHEST;
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (!stack.isEmpty()) return slot;
        }
        return null;
    }
    private boolean hasRunicTmpOnArmor(EntityPlayer player) {
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (stack.isEmpty()) continue;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && (tag.hasKey(RUNIC_BONUS_TAG, 1) || tag.hasKey(RUNIC_TMP_TAG_LEGACY, 1))) {
                return true;
            }
        }
        return false;
    }
    private void applyRunicBonus(ItemStack stack, int stacks) {
        if (stack.isEmpty()) return;
        if (stacks <= 0) {
            clearRunicBonus(stack);
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        int base = clampRunic(getStoredRunicBase(tag));
        tag.setByte(RUNIC_BASE_TAG, (byte) base);
        tag.removeTag(RUNIC_BASE_TAG_LEGACY);
        tag.removeTag(RUNIC_TMP_TAG_LEGACY);
        int tmp = clampRunic(stacks);
        int total = clampRunic(base + tmp);
        tag.setByte(RUNIC_BONUS_TAG, (byte) tmp);
        tag.setByte(RUNIC_TAG, (byte) total);
    }

    private void clearRunicBonus(ItemStack stack) {
        if (stack.isEmpty()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;
        if (!tag.hasKey(RUNIC_BASE_TAG, 1)
                && !tag.hasKey(RUNIC_BONUS_TAG, 1)
                && !tag.hasKey(RUNIC_BASE_TAG_LEGACY, 1)
                && !tag.hasKey(RUNIC_TMP_TAG_LEGACY, 1)) {
            return;
        }

        int base = getStoredRunicBase(tag);
        tag.setByte(RUNIC_TAG, (byte) clampRunic(base));
        tag.removeTag(RUNIC_BASE_TAG);
        tag.removeTag(RUNIC_BONUS_TAG);
        tag.removeTag(RUNIC_BASE_TAG_LEGACY);
        tag.removeTag(RUNIC_TMP_TAG_LEGACY);
    }
    private int getStoredRunicBase(NBTTagCompound tag) {
        if (tag == null) return 0;
        if (tag.hasKey(RUNIC_BASE_TAG, 1)) return tag.getByte(RUNIC_BASE_TAG);
        if (tag.hasKey(RUNIC_BASE_TAG_LEGACY, 1)) return tag.getByte(RUNIC_BASE_TAG_LEGACY);
        if (tag.hasKey(RUNIC_TAG, 1)) return tag.getByte(RUNIC_TAG);
        return 0;
    }

    private int clampRunic(int value) {
        if (value < 0) return 0;
        return Math.min(127, value);
    }

    private int getOverloadCost(int currentSeries) {
        double cost = 5.0d + (currentSeries * currentSeries) / 5.0d;
        return (int) Math.ceil(cost);
    }

    private boolean tryConsumeVis(EntityPlayer player, int cost) {
        if (cost <= 0) return true;
        BlockPos pos = player.getPosition();
        float available = AuraHelper.getVis(player.world, pos);
        if (available + 1.0e-4f >= cost) {
            float drained = AuraHelper.drainVis(player.world, pos, cost, true);
            if (drained + 1.0e-4f >= cost) {
                return true;
            }
        }
        return consumeChargeFromItems(player, cost);
    }

    private boolean consumeChargeFromItems(EntityPlayer player, int cost) {
        int available = getTotalCharge(player);
        if (available < cost) return false;
        if (consumeChargeFromPlayer(player, cost)) return true;
        int remaining = cost;
        for (ItemStack stack : player.inventory.mainInventory) {
            remaining = drainChargeFromStack(player, stack, remaining);
            if (remaining <= 0) return true;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            remaining = drainChargeFromStack(player, stack, remaining);
            if (remaining <= 0) return true;
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            remaining = drainChargeFromStack(player, stack, remaining);
            if (remaining <= 0) return true;
        }
        remaining = drainChargeFromBaubles(player, remaining);
        return remaining <= 0;
    }

    private int drainChargeFromStack(EntityPlayer player, ItemStack stack, int remaining) {
        if (remaining <= 0 || stack == null || stack.isEmpty()) return remaining;
        if (!(stack.getItem() instanceof IRechargable)) return remaining;
        int charge = RechargeHelper.getCharge(stack);
        if (charge <= 0) return remaining;
        int toDrain = Math.min(charge, remaining);
        if (consumeCharge(stack, player, toDrain)) {
            return remaining - toDrain;
        }
        return remaining;
    }

    private int drainChargeFromBaubles(EntityPlayer player, int remaining) {
        if (remaining <= 0) return 0;
        try {
            Class<?> api = Class.forName("baubles.api.BaublesApi");
            Method getHandler = api.getMethod("getBaublesHandler", EntityPlayer.class);
            Object handler = getHandler.invoke(null, player);
            if (handler == null) return remaining;
            Method getSlots = handler.getClass().getMethod("getSlots");
            Method getStack = handler.getClass().getMethod("getStackInSlot", int.class);
            int slots = (int) getSlots.invoke(handler);
            int pending = remaining;
            for (int i = 0; i < slots; i++) {
                Object stackObj = getStack.invoke(handler, i);
                if (!(stackObj instanceof ItemStack)) continue;
                pending = drainChargeFromStack(player, (ItemStack) stackObj, pending);
                if (pending <= 0) return 0;
            }
            return pending;
        } catch (Throwable ignored) {
            return remaining;
            }
    }


    private int getTotalCharge(EntityPlayer player) {
        int total = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            total += getCharge(stack);
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            total += getCharge(stack);
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            total += getCharge(stack);
        }
        total += getBaublesCharge(player);
        return total;
    }

    private int getCharge(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof IRechargable)) return 0;
        return Math.max(0, RechargeHelper.getCharge(stack));
    }

    private int getBaublesCharge(EntityPlayer player) {
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
                    total += getCharge((ItemStack) stackObj);
                }
            }
            return total;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean consumeCharge(ItemStack stack, EntityPlayer player, int amount) {
        try {
            Method method = RechargeHelper.class.getMethod("consumeCharge", ItemStack.class, EntityLivingBase.class, int.class);
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

    private boolean consumeChargeFromPlayer(EntityPlayer player, int amount) {
        try {
            Method method = RechargeHelper.class.getMethod("consumeCharge", EntityLivingBase.class, int.class);
            Object result = method.invoke(null, player, amount);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) {
        }
        try {
            Method method = RechargeHelper.class.getMethod("consumeCharge", EntityPlayer.class, int.class);
            Object result = method.invoke(null, player, amount);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void clearAmethystData(EntityPlayer player) {
        NBTTagCompound data = getPersistedData(player);
        data.setInteger(NBT_AMETHYST_STACKS, 0);
        data.setLong(NBT_AMETHYST_EXPIRE, 0L);
        data.setInteger(NBT_AMETHYST_OVER_N, 0);
        data.setLong(NBT_AMETHYST_OVER_LAST, 0L);
        data.setLong(NBT_AMETHYST_OVER_CD, 0L);
    }

    private void applyDiamondSet4VisBonus(EntityPlayer player, GemSummary summary, LivingHurtEvent event) {
        if (summary.count < DiamondEffects.SET4_REQUIRED) {
            clearDiamondVisBonus(player);
            return;
        }
        if (!tryDrainVisFromAura(player, 10)) return;

        float baseDamage = (float) player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        if (baseDamage <= 0f) return;

        NBTTagCompound data = getPersistedData(player);
        float stored = data.getFloat(NBT_DIAMOND_VIS_BONUS);
        float newStored = stored + 2.0f;
        data.setFloat(NBT_DIAMOND_VIS_BONUS, newStored);
        event.setAmount(event.getAmount() + 2.0f);

        if (newStored >= baseDamage) {
            data.setFloat(NBT_DIAMOND_VIS_BONUS, 0f);
            triggerDiamondOverflowStrike(player, (EntityLivingBase) event.getEntityLiving());
        }
    }

    private void clearDiamondVisBonus(EntityPlayer player) {
        NBTTagCompound data = getPersistedData(player);
        if (data.hasKey(NBT_DIAMOND_VIS_BONUS)) {
            data.setFloat(NBT_DIAMOND_VIS_BONUS, 0f);
        }
    }

    private float getTotalAttackSpeedBonus(GemSummary summary) {
        float total = 0f;
        for (int tier : summary.tiers) {
            total += DiamondEffects.getAttackSpeedBonusPerGem(tier);
        }
        return Math.min(DiamondEffects.MAX_ATTACK_SPEED_BONUS, total);
    }

    private boolean tryDrainVisFromAura(EntityPlayer player, int cost) {
        if (cost <= 0) return true;
        BlockPos pos = player.getPosition();
        float available = AuraHelper.getVis(player.world, pos);
        if (available + 1.0e-4f < cost) {
            return false;
        }
        float drained = AuraHelper.drainVis(player.world, pos, cost, true);
        return drained + 1.0e-4f >= cost;
    }

    private void triggerDiamondOverflowStrike(EntityPlayer player, EntityLivingBase originalTarget) {
        NBTTagCompound data = getPersistedData(player);
        data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, true);
        try {
            EntityLivingBase target = pickNearestDiamondTarget(player, originalTarget);
            if (target == null || !target.isEntityAlive()) return;
            boolean cast = castFluxStrike(player, target);
            if (!cast) {
                target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(player, player), 8.0f);
            }
        } finally {
            data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, false);
        }
    }

    private EntityLivingBase pickNearestDiamondTarget(EntityPlayer player, EntityLivingBase fallback) {
        List<EntityLivingBase> targets = findDiamondTargets(player);
        if (!targets.isEmpty()) {
            return targets.get(0);
        }
        return fallback;
    }

    private void triggerDiamondFocusStrikes(EntityPlayer player, EntityLivingBase originalTarget) {
        NBTTagCompound data = getPersistedData(player);
        data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, true);
        try {
            List<EntityLivingBase> targets = findDiamondTargets(player);
            for (int i = 0; i < DiamondEffects.SET4_STRIKE_COUNT; i++) {
                EntityLivingBase target = targets.isEmpty() ? originalTarget : targets.get(i % targets.size());
                if (target == null || !target.isEntityAlive()) continue;
                boolean cast = castFluxStrike(player, target);
                if (!cast) {
                    target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(player, player), DiamondEffects.SET2_STRIKE_DAMAGE);
                }
            }
        } finally {
            data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, false);
        }
    }
    private List<EntityLivingBase> findDiamondTargets(EntityPlayer player) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(DiamondEffects.SET2_TARGET_RADIUS);
        List<EntityLivingBase> targets = player.world.getEntitiesWithinAABB(EntityLivingBase.class, box, entity -> {
            if (entity == null || entity == player) return false;
            if (!entity.isEntityAlive()) return false;
            return entity.canBeAttackedWithItem();
        });
        targets.sort(Comparator.comparingDouble(player::getDistanceSq));
        return targets;
    }

    private boolean castFluxStrike(EntityPlayer player, EntityLivingBase target) {
        try {
            Class<?> packageClass = Class.forName("thaumcraft.api.casters.FocusPackage");
            Class<?> nodeClass = Class.forName("thaumcraft.api.casters.FocusNode");
            Object focusPackage = packageClass.getDeclaredConstructor().newInstance();
            Object root = Class.forName("thaumcraft.api.casters.FocusMediumRoot").getDeclaredConstructor().newInstance();
            Object flux = Class.forName("thaumcraft.common.items.casters.foci.FocusEffectFlux").getDeclaredConstructor().newInstance();

            invokeMethod(root, "initialize");
            invokeMethod(flux, "initialize");
            invokeMethod(root, "setupFromCasterToTarget", EntityLivingBase.class, Entity.class, player, target);

            invokeMethod(focusPackage, "setCaster", EntityLivingBase.class, player);
            invokeMethod(focusPackage, "setTarget", Entity.class, target);
            invokeMethod(focusPackage, "addNode", nodeClass, root);
            invokeMethod(focusPackage, "addNode", nodeClass, flux);

            Class<?> engineClass = Class.forName("thaumcraft.api.casters.FocusEngine");
            if (invokeStaticFocusCast(engineClass, player, focusPackage)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean invokeStaticFocusCast(Class<?> engineClass, EntityPlayer player, Object focusPackage) {
        try {
            Method method = findCastMethod(engineClass, EntityLivingBase.class, focusPackage.getClass(), boolean.class);
            if (method != null) {
                method.invoke(null, player, focusPackage, true);
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = findCastMethod(engineClass, focusPackage.getClass(), boolean.class);
            if (method != null) {
                method.invoke(null, focusPackage, true);
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = findCastMethod(engineClass, EntityLivingBase.class, focusPackage.getClass());
            if (method != null) {
                method.invoke(null, player, focusPackage);
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = findCastMethod(engineClass, focusPackage.getClass());
            if (method != null) {
                method.invoke(null, focusPackage);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private Method findCastMethod(Class<?> engineClass, Class<?>... paramTypes) {
        try {
            return engineClass.getMethod("castFocusPackage", paramTypes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void invokeMethod(Object target, String name, Class<?> paramType, Object arg) {
        if (target == null) return;
        try {
            Method method = target.getClass().getMethod(name, paramType);
            method.invoke(target, arg);
        } catch (Throwable ignored) {
        }
    }

    private void invokeMethod(Object target, String name, Class<?> paramTypeA, Class<?> paramTypeB, Object argA, Object argB) {
        if (target == null) return;
        try {
            Method method = target.getClass().getMethod(name, paramTypeA, paramTypeB);
            method.invoke(target, argA, argB);
        } catch (Throwable ignored) {
        }
    }

    private void invokeMethod(Object target, String name) {
        if (target == null) return;
        try {
            Method method = target.getClass().getMethod(name);
            method.invoke(target);
        } catch (Throwable ignored) {
        }
    }

    private boolean isCasterItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ICaster;
    }

    private boolean isFocusDamage(DamageSource source, EntityPlayer player) {
        if (source == null) return false;
        String type = source.getDamageType();
        if (type != null && type.toLowerCase(Locale.ROOT).contains("focus")) return true;
        String className = source.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains("focus")) return true;
        if (source instanceof EntityDamageSource) {
            Entity immediate = ((EntityDamageSource) source).getImmediateSource();
            if (immediate != null) {
                String immediateClass = immediate.getClass().getName().toLowerCase(Locale.ROOT);
                if (immediateClass.contains("focus")) return true;
            }
        }
        if (player != null && source.isMagicDamage()) {
            return isCasterItem(player.getHeldItemMainhand())
                    || isCasterItem(player.getHeldItemOffhand());
        }
        return false;
    }

    private EntityPlayer resolvePlayerFromDamageSource(DamageSource source) {
        if (source == null) return null;
        Entity trueSource = source.getTrueSource();
        if (trueSource instanceof EntityPlayer) return (EntityPlayer) trueSource;
        Entity immediate = source.getImmediateSource();
        EntityPlayer player = extractPlayerFromEntity(immediate);
        if (player != null) return player;
        if (source instanceof EntityDamageSourceIndirect) {
            Entity indirect = ((EntityDamageSourceIndirect) source).getImmediateSource();
            player = extractPlayerFromEntity(indirect);
            if (player != null) return player;
        }
        return null;
    }

    private EntityPlayer extractPlayerFromEntity(Entity entity) {
        if (entity instanceof EntityPlayer) return (EntityPlayer) entity;
        EntityLivingBase owner = resolveOwner(entity);
        return owner instanceof EntityPlayer ? (EntityPlayer) owner : null;
    }

    private EntityLivingBase resolveOwner(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof EntityLivingBase) return (EntityLivingBase) entity;
        EntityLivingBase owner = invokeLivingMethod(entity, "getThrower", "func_85052_h");
        if (owner != null) return owner;
        owner = invokeLivingMethod(entity, "getOwner", "func_184662_a");
        if (owner != null) return owner;
        owner = invokeLivingMethod(entity, "getShooter", "func_184655_a");
        if (owner != null) return owner;
        Object field = readField(entity, "thrower", "field_70192_c", "shootingEntity", "field_70235_a");
        return field instanceof EntityLivingBase ? (EntityLivingBase) field : null;
    }

    private EntityLivingBase invokeLivingMethod(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                Object result = method.invoke(target);
                if (result instanceof EntityLivingBase) {
                    return (EntityLivingBase) result;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Object readField(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void damageGemInlays(EntityPlayer player, GemDamageSource source) {
        for (TAGemArmorUtil.GemInlay inlay : TAGemArmorUtil.getEquippedGemInlays(player)) {
            ITAGemDefinition def = TAGemRegistry.get(inlay.getId());
            if (def == null) continue;
            if (def.getDamageSource() != source) continue;
            int maxDurability = def.getBaseDurability(inlay.getTier());
            if (maxDurability <= 0) continue;
            int newDamage = inlay.getDamage() + 1;
            if (newDamage >= maxDurability) {
                TAGemInlayUtil.clearGem(inlay.getStack());
            } else {
                TAGemInlayUtil.setDamage(inlay.getStack(), newDamage);
            }
        }
    }

    private GemSummary getGemSummary(EntityPlayer player, ResourceLocation id) {
        GemSummary summary = new GemSummary();
        for (TAGemArmorUtil.GemInlay inlay : TAGemArmorUtil.getEquippedGemInlays(player)) {
            if (!inlay.getId().equals(id)) continue;
            summary.count++;
            summary.tiers.add(inlay.getTier());
        }
        return summary;
    }

    private float getTotalDamageBonus(GemSummary summary, TierBonusResolver resolver) {
        float total = 0f;
        for (int tier : summary.tiers) {
            total += resolver.getBonus(tier);
        }
        return total;
    }

    private NBTTagCompound getPersistedData(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static class GemSummary {
        private final List<Integer> tiers = new ArrayList<>();
        private int count;
    }

    private interface TierBonusResolver {
        float getBonus(int tier);
    }
}
