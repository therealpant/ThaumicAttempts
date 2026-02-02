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
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.init.MobEffects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.items.casters.foci.FocusEffectFlux;
import therealpant.thaumicattempts.api.gems.GemDamageSource;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.capability.AmberCasterCapability;
import therealpant.thaumicattempts.capability.IAmberCasterData;
import therealpant.thaumicattempts.combat.ArcaneGuardData;
import therealpant.thaumicattempts.combat.ArcaneMarkData;
import therealpant.thaumicattempts.core.TAHooks;
import therealpant.thaumicattempts.effects.AmberEffects;
import therealpant.thaumicattempts.effects.AmethystEffects;
import therealpant.thaumicattempts.effects.DiamondEffects;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.net.msg.S2C_AmberCountUpdate;
import therealpant.thaumicattempts.util.TAGemArmorUtil;
import therealpant.thaumicattempts.util.TAGemInlayUtil;
import therealpant.thaumicattempts.util.ObfCompat;
import therealpant.thaumicattempts.ThaumicAttempts;



public class TAGemEventHandler {
    private static final String NBT_AMETHYST_STACKS = "ta_amethyst_stacks";
    private static final String NBT_AMETHYST_EXPIRE = "ta_amethyst_expire";
    private static final String NBT_AMETHYST_RECHARGE_TICKER = "ta_amethyst_rechargeTicker";
    private static final String NBT_AMETHYST_TOTEM_CD = "ta_amethyst_totem_cd_until";
    private static final String NBT_AMETHYST_WAVE_ACTIVE = "ta_amethyst_wave_active";
    private static final String NBT_AMETHYST_WAVE_NEXT = "ta_amethyst_wave_nextTick";
    private static final String NBT_AMETHYST_LAST_RUNIC_UPDATE = "ta_amethyst_runic_update";
    private static final String NBT_AMETHYST_LAST_STACKS = "ta_amethyst_lastStacks";
    private static final String NBT_AMETHYST_LAST_TARGET = "ta_amethyst_lastTarget";

    private static final String NBT_DIAMOND_HITCOUNT = "ta_diamond_hitcount";
    private static final String NBT_DIAMOND_INTERNAL_CAST = "ta_diamond_internal_cast";
    private static final String NBT_DIAMOND_STRIKES_LEFT = "ta_diamond_strikes_left";
    private static final String NBT_DIAMOND_STRIKE_NEXT = "ta_diamond_strike_next";
    private static final String NBT_DIAMOND_STRIKE_INTERVAL = "ta_diamond_strike_interval";
    private static final String NBT_DIAMOND_LAST_TARGET = "ta_diamond_last_target";

    private static final int DIAMOND_STRIKE_INTERVAL_TICKS = 2;
    private static final float DIAMOND_FOCUS_POWER = 5.0f;

    private static final String RUNIC_TAG = "TC.RUNIC";
    private static final String RUNIC_BASE_TAG = "TA.RUNIC_BASE";
    private static final String RUNIC_TMP_TAG = "TA.RUNIC_TMP";

    private static final EntityEquipmentSlot[] ARMOR_SLOTS = new EntityEquipmentSlot[]{
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
    };
    private final Map<UUID, Integer> amberCountCache = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event) {
        if (event.player == null || ObfCompat.isRemote(event.player)) return;
        if (event.phase != TickEvent.Phase.END) return;
        World world = ObfCompat.getWorld(event.player);
        if (world == null) return;
        long now = world.getTotalWorldTime();

        GemSummary amberSummary = getGemSummary(event.player, AmberGemDefinition.ID);
        GemSummary amethystSummary = getGemSummary(event.player, AmethystGemDefinition.ID);

        updateAmethystState(event.player, now, amethystSummary);
        updateDiamondModifiers(event.player);
        tickDiamondStrikeSeries(event.player, now);
        updateAmberFrequency(event.player, now, amberSummary.count);
        syncAmberCount(event.player, amberSummary.count);
    }

    @SubscribeEvent
    public void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        if (event.isCanceled() || event.getAmount() <= 0f) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (ObfCompat.isRemote(player)) return;

        GemSummary amethystSummary = getGemSummary(player, AmethystGemDefinition.ID);
        if (amethystSummary.count > 0) {
            applyAmethystStacks(player, amethystSummary, event.getAmount());
            maybeTriggerAmethystWave(player, amethystSummary, event.getAmount());
            damageGemInlays(player, GemDamageSource.ON_PLAYER_HURT);
        }
    }

    @SubscribeEvent
    public void onLivingHurtByPlayer(LivingHurtEvent event) {
        if (event.getEntityLiving() == null || ObfCompat.isRemote(event.getEntityLiving())) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();

        boolean melee = event.getSource().getImmediateSource() == player;

        GemSummary amberSummary = getGemSummary(player, AmberGemDefinition.ID);
        if (amberSummary.count > 0 && isFocusDamage(event.getSource())) {
            float bonus = getTotalDamageBonus(amberSummary, AmberEffects::getDamageBonusPerGem);
            if (bonus > 0f) {
                event.setAmount(event.getAmount() * (1.0f + bonus));
            }
        }

        GemSummary diamondSummary = getGemSummary(player, DiamondGemDefinition.ID);
        if (melee) {
            if (diamondSummary.count > 0) {
                float bonus = getTotalDamageBonus(diamondSummary, DiamondEffects::getDamageBonusPerGem);
                if (bonus > 0f) {
                    event.setAmount(event.getAmount() * (1.0f + bonus));
                }
                damageGemInlays(player, GemDamageSource.ON_PLAYER_HIT);
            }
        }

        NBTTagCompound data = getPersistedData(player);
        if (diamondSummary.count < DiamondEffects.SET4_REQUIRED) {
            data.setInteger(NBT_DIAMOND_HITCOUNT, 0);
            clearDiamondStrikeSeries(data);
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0f) return;
        if (!melee) return;
        if (data.getBoolean(NBT_DIAMOND_INTERNAL_CAST)) return;

        int hits = data.getInteger(NBT_DIAMOND_HITCOUNT) + 1;
        if (hits >= DiamondEffects.SET4_HIT_THRESHOLD) {
            data.setInteger(NBT_DIAMOND_HITCOUNT, 0);
            if (event.getEntityLiving() instanceof EntityLivingBase) {
                startDiamondStrikeSeries(player, (EntityLivingBase) event.getEntityLiving());
            }
        } else {
            data.setInteger(NBT_DIAMOND_HITCOUNT, hits);
        }
    }

    @SubscribeEvent
    public void onFocusCast(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntityPlayer() == null) return;
        EntityPlayer player = event.getEntityPlayer();
        if (ObfCompat.isRemote(player)) return;

        ItemStack stack = event.getItemStack();
        if (!isCasterItem(stack)) return;

        GemSummary amberSummary = getGemSummary(player, AmberGemDefinition.ID);
        if (amberSummary.count <= 0) return;

        damageGemInlays(player, GemDamageSource.ON_FOCUS_CAST);
    }

    @SubscribeEvent
    public void onPlayerLogout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null || ObfCompat.isRemote(event.player)) return;
        amberCountCache.remove(event.player.getUniqueID());
    }


    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntityPlayer() == null || !event.isWasDeath()) return;
        clearAmethystData(event.getEntityPlayer());
        NBTTagCompound data = getPersistedData(event.getEntityPlayer());
        data.setInteger(NBT_DIAMOND_HITCOUNT, 0);
        data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, false);
        data.setInteger(NBT_DIAMOND_STRIKES_LEFT, 0);
        data.setLong(NBT_DIAMOND_STRIKE_NEXT, 0L);
        data.setInteger(NBT_DIAMOND_STRIKE_INTERVAL, DIAMOND_STRIKE_INTERVAL_TICKS);
        data.removeTag(NBT_DIAMOND_LAST_TARGET);
    }

    private void updateAmethystState(EntityPlayer player, long now, GemSummary summary) {
        NBTTagCompound data = getPersistedData(player);
        int stacks = data.getInteger(NBT_AMETHYST_STACKS);
        long expire = data.getLong(NBT_AMETHYST_EXPIRE);

        if (summary.count <= 0) {
            if (stacks > 0 || expire > 0) {
                data.setInteger(NBT_AMETHYST_STACKS, 0);
                data.setLong(NBT_AMETHYST_EXPIRE, 0L);
                TAHooks.setRunicWaitOverride(player, -1);
                TAHooks.setRunicRechargeOverride(player, -1);
                TAHooks.consumeForceRunicRechargeTick(player);
            }
            data.setBoolean(NBT_AMETHYST_WAVE_ACTIVE, false);
            data.setInteger(NBT_AMETHYST_RECHARGE_TICKER, 0);
            updateAmethystRunicBonus(player, 0, now);
            return;
        }
        if (stacks > 0 && now > expire) {
            stacks = 0;
            data.setInteger(NBT_AMETHYST_STACKS, 0);
            data.setLong(NBT_AMETHYST_EXPIRE, 0L);
        }
        updateAmethystRunicBonus(player, stacks, now);
        updateAmethystRecharge(player, summary, stacks, now);
        updateAmethystWave(player, now);
    }

    private void applyAmethystStacks(EntityPlayer player, GemSummary summary, float damageAmount) {
        if (damageAmount <= 0f) return;
        int cap = getAmethystStackCap(summary);
        if (cap <= 0) return;

        NBTTagCompound data = getPersistedData(player);
        int stacks = data.getInteger(NBT_AMETHYST_STACKS);
        boolean gained = stacks < cap;
        int newStacks = Math.min(cap, stacks + (gained ? 1 : 0));

        data.setInteger(NBT_AMETHYST_STACKS, newStacks);
        World world = ObfCompat.getWorld(player);
        if (world == null) return;
        data.setLong(NBT_AMETHYST_EXPIRE, world.getTotalWorldTime() + AmethystEffects.STACK_DURATION_TICKS);

        if (gained) {
            float currentAbs = TAHooks.getRunicCurrent(player) + 1.0f;
            float maxAbs = getRunicBaseFromGear(player) + newStacks;
            TAHooks.setRunicCurrent(player, Math.min(currentAbs, maxAbs));
        }
    }

    private void maybeTriggerAmethystWave(EntityPlayer player, GemSummary summary, float damageAmount) {
        if (summary.count < AmethystEffects.SET4_REQUIRED) return;
        float projected = player.getHealth() - damageAmount;
        if (projected > AmethystEffects.WAVE_HEALTH_THRESHOLD) return;

        NBTTagCompound data = getPersistedData(player);
        World world = ObfCompat.getWorld(player);
        if (world == null) return;
        long now = world.getTotalWorldTime();
        if (data.getBoolean(NBT_AMETHYST_WAVE_ACTIVE)) return;
        if (now < data.getLong(NBT_AMETHYST_TOTEM_CD)) return;

        data.setBoolean(NBT_AMETHYST_WAVE_ACTIVE, true);
        data.setLong(NBT_AMETHYST_WAVE_NEXT, now);
        data.setLong(NBT_AMETHYST_TOTEM_CD, now + AmethystEffects.WAVE_COOLDOWN_TICKS);

        PotionEffect regen = new PotionEffect(MobEffects.REGENERATION,
                AmethystEffects.WAVE_REGEN_DURATION_TICKS,
                AmethystEffects.WAVE_REGEN_AMPLIFIER,
                false,
                true);
        player.addPotionEffect(regen);
    }

    private void updateAmethystRecharge(EntityPlayer player, GemSummary summary, int stacks, long now) {
        int cap = getAmethystStackCap(summary);
        boolean canRecharge = cap > 0 && stacks >= cap && summary.count >= AmethystEffects.SET2_REQUIRED;
        NBTTagCompound data = getPersistedData(player);
        if (!canRecharge) {
            data.setInteger(NBT_AMETHYST_RECHARGE_TICKER, 0);
            TAHooks.setRunicWaitOverride(player, -1);
            TAHooks.setRunicRechargeOverride(player, -1);
            return;
        }

        boolean nearHostiles = hasNearbyHostiles(player);
        int interval = nearHostiles ? AmethystEffects.SET2_RECHARGE_INTERVAL_HOSTILE : AmethystEffects.SET2_RECHARGE_INTERVAL;
        int ticker = data.getInteger(NBT_AMETHYST_RECHARGE_TICKER) + 1;
        if (ticker >= interval) {
            ticker = 0;
            TAHooks.forceRunicRechargeNow(player);

        }
        data.setInteger(NBT_AMETHYST_RECHARGE_TICKER, ticker);
    }

    private void updateAmethystWave(EntityPlayer player, long now) {
        NBTTagCompound data = getPersistedData(player);
        if (!data.getBoolean(NBT_AMETHYST_WAVE_ACTIVE)) return;

        int maxAbs = getRunicMaxFromGear(player);
        float currentAbs = TAHooks.getRunicCurrent(player);
        if (currentAbs >= maxAbs) {
            data.setBoolean(NBT_AMETHYST_WAVE_ACTIVE, false);
            return;
        }

        long nextTick = data.getLong(NBT_AMETHYST_WAVE_NEXT);
        if (now >= nextTick) {
            TAHooks.forceRunicRechargeNow(player);
            data.setLong(NBT_AMETHYST_WAVE_NEXT, now + AmethystEffects.WAVE_RECHARGE_INTERVAL_TICKS);
        }

        PotionEffect resistance = new PotionEffect(MobEffects.RESISTANCE,
                AmethystEffects.WAVE_RESISTANCE_REFRESH_TICKS,
                AmethystEffects.WAVE_RESISTANCE_AMPLIFIER,
                false,
                true);
        player.addPotionEffect(resistance);
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
        boolean hasSet = summary.count >= DiamondEffects.SET2_REQUIRED;
        AttributeModifier attackSpeed = new AttributeModifier(DiamondEffects.ATTACK_SPEED_UUID,
                "ta_diamond_attack_speed", DiamondEffects.SET2_ATTACK_SPEED_BONUS, 1);
        AttributeModifier moveSpeed = new AttributeModifier(DiamondEffects.MOVE_SPEED_UUID,
                "ta_diamond_move_speed", DiamondEffects.SET2_MOVE_SPEED_BONUS, 1);

        if (hasSet) {
            if (player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getModifier(DiamondEffects.ATTACK_SPEED_UUID) == null) {
                player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).applyModifier(attackSpeed);
            }
            if (player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(DiamondEffects.MOVE_SPEED_UUID) == null) {
                player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(moveSpeed);
            }
        } else {
            if (player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getModifier(DiamondEffects.ATTACK_SPEED_UUID) != null) {
                player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).removeModifier(DiamondEffects.ATTACK_SPEED_UUID);
            }
            if (player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(DiamondEffects.MOVE_SPEED_UUID) != null) {
                player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeModifier(DiamondEffects.MOVE_SPEED_UUID);
            }
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
            if (tag != null && tag.hasKey(RUNIC_TMP_TAG, 1)) {
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

        int base;
        if (tag.hasKey(RUNIC_BASE_TAG, 1)) {
            base = tag.getByte(RUNIC_BASE_TAG);
        } else {
            base = tag.hasKey(RUNIC_TAG, 1) ? tag.getByte(RUNIC_TAG) : 0;
            tag.setByte(RUNIC_BASE_TAG, (byte) clampRunic(base));
        }
        int tmp = clampRunic(stacks);
        int total = clampRunic(base + tmp);
        tag.setByte(RUNIC_TMP_TAG, (byte) tmp);
        tag.setByte(RUNIC_TAG, (byte) total);
    }

    private void clearRunicBonus(ItemStack stack) {
        if (stack.isEmpty()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;
        if (!tag.hasKey(RUNIC_BASE_TAG, 1) && !tag.hasKey(RUNIC_TMP_TAG, 1)) return;

        int base = tag.hasKey(RUNIC_BASE_TAG, 1) ? tag.getByte(RUNIC_BASE_TAG) : (tag.hasKey(RUNIC_TAG, 1) ? tag.getByte(RUNIC_TAG) : 0);
        tag.setByte(RUNIC_TAG, (byte) clampRunic(base));
        tag.removeTag(RUNIC_BASE_TAG);
        tag.removeTag(RUNIC_TMP_TAG);
    }

    private int getRunicBaseFromGear(EntityPlayer player) {
        int total = 0;
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (stack.isEmpty()) continue;
            total += getRunicBase(stack);
        }
        return total;
    }

    private int getRunicBase(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            if (tag.hasKey(RUNIC_BASE_TAG, 1)) {
                return tag.getByte(RUNIC_BASE_TAG);
            }
            if (tag.hasKey(RUNIC_TAG, 1)) {
                return tag.getByte(RUNIC_TAG);
            }
        }
        return 0;
    }

    private int getRunicMaxFromGear(EntityPlayer player) {
        int total = 0;
        for (EntityEquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (stack.isEmpty()) continue;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey(RUNIC_TAG, 1)) {
                total += tag.getByte(RUNIC_TAG);
            }
        }
        return total;
    }

    private int clampRunic(int value) {
        if (value < 0) return 0;
        return Math.min(127, value);
    }

    private void clearAmethystData(EntityPlayer player) {
        NBTTagCompound data = getPersistedData(player);
        data.setInteger(NBT_AMETHYST_STACKS, 0);
        data.setLong(NBT_AMETHYST_EXPIRE, 0L);
        data.setInteger(NBT_AMETHYST_RECHARGE_TICKER, 0);
        data.setBoolean(NBT_AMETHYST_WAVE_ACTIVE, false);
        data.setLong(NBT_AMETHYST_WAVE_NEXT, 0L);
        TAHooks.setRunicWaitOverride(player, -1);
        TAHooks.setRunicRechargeOverride(player, -1);
        TAHooks.consumeForceRunicRechargeTick(player);
    }

    private boolean hasNearbyHostiles(EntityPlayer player) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(AmethystEffects.SET2_HOSTILE_RADIUS);
        World world = ObfCompat.getWorld(player);
        if (world == null) return false;
        List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, box, entity -> {
            if (entity == null || entity == player) return false;
            if (!entity.isEntityAlive()) return false;
            return entity instanceof IMob;
        });
        return !entities.isEmpty();
    }

    private void startDiamondStrikeSeries(EntityPlayer player, EntityLivingBase originalTarget) {
        NBTTagCompound data = getPersistedData(player);
        data.setInteger(NBT_DIAMOND_STRIKES_LEFT, DiamondEffects.SET4_STRIKE_COUNT);
        World world = ObfCompat.getWorld(player);
        if (world == null) return;
        data.setLong(NBT_DIAMOND_STRIKE_NEXT, world.getTotalWorldTime() + 1L);
        data.setInteger(NBT_DIAMOND_STRIKE_INTERVAL, DIAMOND_STRIKE_INTERVAL_TICKS);
        if (originalTarget != null) {
            data.setString(NBT_DIAMOND_LAST_TARGET, originalTarget.getUniqueID().toString());
        } else {
            data.removeTag(NBT_DIAMOND_LAST_TARGET);
        }
    }

    private void tickDiamondStrikeSeries(EntityPlayer player, long now) {
        NBTTagCompound data = getPersistedData(player);
        int strikesLeft = data.getInteger(NBT_DIAMOND_STRIKES_LEFT);
        if (strikesLeft <= 0) return;
        if (now < data.getLong(NBT_DIAMOND_STRIKE_NEXT)) return;

        GemSummary diamondSummary = getGemSummary(player, DiamondGemDefinition.ID);
        if (diamondSummary.count < DiamondEffects.SET4_REQUIRED) {
            clearDiamondStrikeSeries(data);
            return;
        }

        int interval = data.hasKey(NBT_DIAMOND_STRIKE_INTERVAL, 3)
                ? data.getInteger(NBT_DIAMOND_STRIKE_INTERVAL)
                : DIAMOND_STRIKE_INTERVAL_TICKS;
        int strikesPerformed = DiamondEffects.SET4_STRIKE_COUNT - strikesLeft;
        EntityLivingBase target = selectDiamondTarget(player, strikesPerformed);
        if (target == null) {
            clearDiamondStrikeSeries(data);
            return;
        }
        data.setString(NBT_DIAMOND_LAST_TARGET, target.getUniqueID().toString());
        data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, true);
        try {
            boolean cast = castFluxStrike(player, target);
            if (!cast) {
                target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(player, player), DiamondEffects.SET4_STRIKE_DAMAGE);
            }
        } finally {
            data.setBoolean(NBT_DIAMOND_INTERNAL_CAST, false);
        }
        strikesLeft--;
        data.setInteger(NBT_DIAMOND_STRIKES_LEFT, strikesLeft);
        data.setLong(NBT_DIAMOND_STRIKE_NEXT, now + Math.max(1, interval));
        if (strikesLeft <= 0) {
            clearDiamondStrikeSeries(data);
        }
    }

    private void clearDiamondStrikeSeries(NBTTagCompound data) {
        data.setInteger(NBT_DIAMOND_STRIKES_LEFT, 0);
        data.setLong(NBT_DIAMOND_STRIKE_NEXT, 0L);
        data.removeTag(NBT_DIAMOND_LAST_TARGET);
    }

    private EntityLivingBase selectDiamondTarget(EntityPlayer player, int strikeIndex) {
        List<EntityLivingBase> targets = findDiamondTargets(player);
        if (!targets.isEmpty()) {
            return targets.get(strikeIndex % targets.size());
        }
        EntityLivingBase fallback = getDiamondFallbackTarget(player);
        return fallback != null && fallback.isEntityAlive() ? fallback : null;
    }

    private EntityLivingBase getDiamondFallbackTarget(EntityPlayer player) {
        NBTTagCompound data = getPersistedData(player);
        if (!data.hasKey(NBT_DIAMOND_LAST_TARGET, 8)) return null;

        try {
            UUID id = UUID.fromString(data.getString(NBT_DIAMOND_LAST_TARGET));

            World world = ObfCompat.getWorld(player);
            if (world == null) return null;
            Entity entity = null;

            if (player instanceof EntityPlayerMP) {
                entity = ((EntityPlayerMP) player).getServerWorld().getEntityFromUuid(id);
            } else if (world instanceof WorldServer) {
                entity = ((WorldServer) world).getEntityFromUuid(id);
            }

            if (entity instanceof EntityLivingBase) {
                EntityLivingBase living = (EntityLivingBase) entity;
                return living.isEntityAlive() ? living : null;
            }
        } catch (IllegalArgumentException ignored) {
        }

        return null;
    }

    private List<EntityLivingBase> findDiamondTargets(EntityPlayer player) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(DiamondEffects.SET4_TARGET_RADIUS);
        World world = ObfCompat.getWorld(player);
        if (world == null) return new ArrayList<>();
        List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box, entity -> {
            if (entity == null || entity == player) return false;
            if (entity.isDead || !entity.isEntityAlive()) return false;
            return entity.getHealth() > 0f;
        });
        targets.sort(Comparator.<EntityLivingBase>comparingInt(entity -> entity.canBeAttackedWithItem() ? 0 : 1)
                .thenComparingDouble(player::getDistanceSq));
        return targets;
    }

    private boolean castFluxStrike(EntityPlayer player, EntityLivingBase target) {
        try {
            FocusPackage pack = new FocusPackage(player);
            FocusMediumRoot root = new FocusMediumRoot();
            root.initialize();
            root.setupFromCasterToTarget(player, target, player.getDistance(target));
            FocusEffectFlux flux = new FocusEffectFlux();
            flux.initialize();
            pack.addNode(root);
            pack.addNode(flux);
            pack.multiplyPower(DIAMOND_FOCUS_POWER);
            FocusEngine.castFocusPackage(player, pack, true);
            return true;
        } catch (Throwable ignored) {
            return false;
            }
    }

    private boolean isCasterItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ICaster;
    }

    private boolean isFocusDamage(DamageSource source) {
        if (source == null) return false;
        String type = source.getDamageType();
        if (type != null && type.toLowerCase(Locale.ROOT).contains("focus")) return true;
        String className = source.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("focus");
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
        return ObfCompat.getPersistedPlayerData(player);
    }

    private static class GemSummary {
        private final List<Integer> tiers = new ArrayList<>();
        private int count;
    }

    private interface TierBonusResolver {
        float getBonus(int tier);
    }
}
