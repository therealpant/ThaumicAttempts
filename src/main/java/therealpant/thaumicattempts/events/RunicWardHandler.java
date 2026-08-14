package therealpant.thaumicattempts.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.SoundsTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.effects.AmethystEffects;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.net.msg.S2C_RunicWardState;
import therealpant.thaumicattempts.util.RunicMaxCalculator;
import therealpant.thaumicattempts.util.RunicShieldAdapter;
import therealpant.thaumicattempts.util.TAGemArmorUtil;

/** A fully independent ward pool which consumes TC6 runic shielding. */
public final class RunicWardHandler {
    private static final String ROOT = "ta_runic_wards";
    private static final String ACTIVE = "Active";
    private static final String CURRENT = "Current";
    private static final String FORTIFICATION = "Fortification";
    private static final String NEXT_REGEN = "NextRegen";
    private static final String TICK_MASKED = "TickMasked";
    private static final String EMERGENCY_REMAINING = "EmergencyRemaining";
    private static final String EMERGENCY_NEXT = "EmergencyNext";
    private static final String EMERGENCY_COOLDOWN = "EmergencyCooldown";
    private static final String SET4_REDUCTION_EXPIRES = "Set4ReductionExpires";
    private static final String LEGACY_ADAPTATIONS = "Adaptations";

    private static final String RUNIC_TAG = "TC.RUNIC";
    private static final String LEGACY_BASE = "ta_am_base_runic";
    private static final String LEGACY_BONUS = "ta_am_bonus_runic";
    private static final String LEGACY_BASE_OLD = "TA.RUNIC_BASE";
    private static final String LEGACY_TMP_OLD = "TA.RUNIC_TMP";

    private final Map<UUID, WardSnapshot> lastSync = new HashMap<>();

    /** Makes TC6 believe its runic shield is full only during its own tick. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        WardSummary summary = getSummary(player);
        NBTTagCompound state = getState(player);
        if (summary.count <= 0) {
            deactivate(player, state);
            sync(player, false, 0f, 0, 0f);
            return;
        }

        clearLegacyRunicBonuses(player);
        int runicMax = RunicMaxCalculator.getRunicMax(player);
        int visibleCap = Math.min(AmethystEffects.MAX_VISIBLE_SHIELD, runicMax);
        int fortificationCap = Math.max(0, runicMax - visibleCap);

        if (!state.getBoolean(ACTIVE)) {
            float consumedShield = Math.max(0f, RunicShieldAdapter.getCurrentShield(player));
            state.setBoolean(ACTIVE, true);
            state.setFloat(CURRENT, Math.min(visibleCap, consumedShield));
            state.setFloat(FORTIFICATION,
                    Math.min(fortificationCap, Math.max(0f, consumedShield - visibleCap)));
            state.setLong(NEXT_REGEN, System.currentTimeMillis());
        }

        state.setFloat(CURRENT, Math.min(visibleCap, Math.max(0f, state.getFloat(CURRENT))));
        state.setFloat(FORTIFICATION,
                Math.min(fortificationCap, Math.max(0f, state.getFloat(FORTIFICATION))));
        state.setBoolean(TICK_MASKED, true);

        // This value exists only while TC6 executes handleRunicArmor. Outside
        // this event the native pool is always zero and cannot absorb damage.
        RunicShieldAdapter.setCurrentShield(player, runicMax);
    }

    /** Deletes the temporary TC6 pool and advances only our custom state. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        NBTTagCompound state = getState(player);
        if (!state.getBoolean(ACTIVE) || !state.getBoolean(TICK_MASKED)) return;
        state.setBoolean(TICK_MASKED, false);
        RunicShieldAdapter.setCurrentShield(player, 0f);
        player.setAbsorptionAmount(0f);

        WardSummary summary = getSummary(player);
        tickWardRecovery(player, state, summary);
        int max = Math.min(AmethystEffects.MAX_VISIBLE_SHIELD, RunicMaxCalculator.getRunicMax(player));
        sync(player, true, state.getFloat(CURRENT), max, state.getFloat(FORTIFICATION));
    }

    /** Absorbs final post-armour damage from the independent ward pool. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFinalDamage(LivingDamageEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote || event.isCanceled() || event.getAmount() <= 0f) return;

        WardSummary summary = getSummary(player);
        NBTTagCompound state = getState(player);
        if (summary.count <= 0 || !state.getBoolean(ACTIVE)) return;

        long tick = player.world.getTotalWorldTime();
        float damage = event.getAmount();
        boolean hasSet4 = summary.count >= AmethystEffects.SET4_REQUIRED;
        if (!hasSet4) state.removeTag(SET4_REDUCTION_EXPIRES);

        float before = Math.max(0f, state.getFloat(CURRENT));
        float efficiencyFactor = Math.max(0.05f, 1.0f - Math.min(0.95f, summary.efficiency));
        float absorbableDamage = before / efficiencyFactor;
        float absorbedDamage = Math.min(damage, absorbableDamage);
        float spent = absorbedDamage * efficiencyFactor;
        float current = Math.max(0f, before - spent);
        state.setFloat(CURRENT, current);

        int wardsBefore = (int) Math.ceil(before / AmethystEffects.WARD_UNIT);
        int wardsAfter = (int) Math.ceil(current / AmethystEffects.WARD_UNIT);
        if (hasSet4 && wardsAfter < wardsBefore) {
            state.setLong(SET4_REDUCTION_EXPIRES,
                    tick + AmethystEffects.SET4_DAMAGE_REDUCTION_DURATION_TICKS);
        }

        float passedDamage = Math.max(0f, damage - absorbedDamage);
        if (hasSet4 && tick < state.getLong(SET4_REDUCTION_EXPIRES)) {
            passedDamage *= 1.0f - AmethystEffects.SET4_DAMAGE_REDUCTION;
        }
        event.setAmount(passedDamage);

        if (spent <= 1.0e-4f) {
            syncCurrent(player, state);
            return;
        }

        long nowMs = System.currentTimeMillis();
        long delay = current <= 1.0e-4f
                ? ModConfig.CONFIG_MISC.shieldWait
                : ModConfig.CONFIG_MISC.shieldRecharge;
        state.setLong(NEXT_REGEN, Math.max(state.getLong(NEXT_REGEN), nowMs + delay));

        if (summary.count >= AmethystEffects.SET2_REQUIRED
                && spent > AmethystEffects.SET2_TRIGGER_LOSS
                && state.getInteger(EMERGENCY_REMAINING) <= 0
                && tick >= state.getLong(EMERGENCY_COOLDOWN)) {
            state.setInteger(EMERGENCY_REMAINING, AmethystEffects.SET2_TOTAL_RESTORES);
            state.setLong(EMERGENCY_NEXT, tick + AmethystEffects.SET2_RESTORE_INTERVAL_TICKS);
            state.setLong(EMERGENCY_COOLDOWN, tick + AmethystEffects.SET2_COOLDOWN_TICKS);
        }

        syncCurrent(player, state);
    }

    private void tickWardRecovery(EntityPlayer player, NBTTagCompound state, WardSummary summary) {
        if (summary.count <= 0) return;
        int runicMax = RunicMaxCalculator.getRunicMax(player);
        float visibleCap = Math.min(AmethystEffects.MAX_VISIBLE_SHIELD, runicMax);
        float fortificationCap = Math.max(0, runicMax - visibleCap);
        float current = Math.min(visibleCap, Math.max(0f, state.getFloat(CURRENT)));
        float fortification = Math.min(fortificationCap, Math.max(0f, state.getFloat(FORTIFICATION)));
        long tick = player.world.getTotalWorldTime();

        int emergency = state.getInteger(EMERGENCY_REMAINING);
        if (emergency > 0 && tick >= state.getLong(EMERGENCY_NEXT)) {
            if (current >= visibleCap || !drainAura(player, AmethystEffects.SET2_VIS_PER_RESTORE)) {
                state.setInteger(EMERGENCY_REMAINING, 0);
            } else {
                current = Math.min(visibleCap, current + 1f);
                state.setInteger(EMERGENCY_REMAINING, emergency - 1);
                state.setLong(EMERGENCY_NEXT, tick + AmethystEffects.SET2_RESTORE_INTERVAL_TICKS);
                playRechargeSound(player);
            }
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs >= state.getLong(NEXT_REGEN)
                && (current < visibleCap || fortification < fortificationCap)) {
            boolean restored = false;
            if (current < visibleCap && fortification > 0f) {
                float amount = Math.min(1f, Math.min(visibleCap - current, fortification));
                current += amount;
                fortification -= amount;
                restored = true;
            } else if (drainAura(player, ModConfig.CONFIG_MISC.shieldCost)) {
                if (current < visibleCap) current = Math.min(visibleCap, current + 1f);
                else fortification = Math.min(fortificationCap, fortification + 1f);
                restored = true;
            }
            state.setLong(NEXT_REGEN, nowMs + ModConfig.CONFIG_MISC.shieldRecharge);
            if (restored) playRechargeSound(player);
        }

        state.setFloat(CURRENT, current);
        state.setFloat(FORTIFICATION, fortification);
        if (summary.count < AmethystEffects.SET4_REQUIRED
                || tick >= state.getLong(SET4_REDUCTION_EXPIRES)) {
            state.removeTag(SET4_REDUCTION_EXPIRES);
        }
    }

    private void syncCurrent(EntityPlayer player, NBTTagCompound state) {
        int max = Math.min(AmethystEffects.MAX_VISIBLE_SHIELD, RunicMaxCalculator.getRunicMax(player));
        sync(player, true, state.getFloat(CURRENT), max, state.getFloat(FORTIFICATION));
    }

    private void sync(EntityPlayer player, boolean active, float current, int max, float fortification) {
        if (!(player instanceof EntityPlayerMP)) return;
        WardSnapshot next = new WardSnapshot(active, current, max, fortification);
        WardSnapshot previous = lastSync.get(player.getUniqueID());
        if (next.equals(previous)) return;
        lastSync.put(player.getUniqueID(), next);
        ThaumicAttempts.NET.sendTo(new S2C_RunicWardState(active, current, max, fortification),
                (EntityPlayerMP) player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) lastSync.remove(event.player.getUniqueID());
    }

    private static boolean drainAura(EntityPlayer player, float amount) {
        if (amount <= 0f) return true;
        BlockPos pos = player.getPosition();
        if (AuraHelper.getVis(player.world, pos) + 1.0e-4f < amount) return false;
        return AuraHelper.drainVis(player.world, pos, amount, false) + 1.0e-4f >= amount;
    }

    private static void playRechargeSound(EntityPlayer player) {
        player.playSound(SoundsTC.runicShieldCharge, 0.55f,
                1.1f + player.getRNG().nextFloat() * 0.1f);
    }

    private static WardSummary getSummary(EntityPlayer player) {
        WardSummary result = new WardSummary();
        for (TAGemArmorUtil.GemInlay inlay : TAGemArmorUtil.getEquippedGemInlays(player)) {
            if (!AmethystGemDefinition.ID.equals(inlay.getId())) continue;
            result.count++;
            result.efficiency += AmethystEffects.getWardEfficiencyForTier(inlay.getTier());
        }
        return result;
    }

    private void deactivate(EntityPlayer player, NBTTagCompound state) {
        if (!state.getBoolean(ACTIVE)) return;
        float combined = Math.max(0f, state.getFloat(CURRENT))
                + Math.max(0f, state.getFloat(FORTIFICATION));
        state.setBoolean(ACTIVE, false);
        state.setBoolean(TICK_MASKED, false);
        state.setInteger(EMERGENCY_REMAINING, 0);
        state.removeTag(SET4_REDUCTION_EXPIRES);
        state.removeTag(LEGACY_ADAPTATIONS);
        RunicShieldAdapter.setCurrentShield(player,
                Math.min(RunicMaxCalculator.getRunicMax(player), combined));
    }

    private static void clearLegacyRunicBonuses(EntityPlayer player) {
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack.isEmpty() || !stack.hasTagCompound()) continue;
            NBTTagCompound tag = stack.getTagCompound();
            if (!tag.hasKey(LEGACY_BASE) && !tag.hasKey(LEGACY_BONUS)
                    && !tag.hasKey(LEGACY_BASE_OLD) && !tag.hasKey(LEGACY_TMP_OLD)) continue;
            int base = tag.hasKey(LEGACY_BASE)
                    ? tag.getInteger(LEGACY_BASE)
                    : tag.hasKey(LEGACY_BASE_OLD)
                    ? tag.getInteger(LEGACY_BASE_OLD)
                    : Math.max(0, tag.getInteger(RUNIC_TAG) - tag.getInteger(LEGACY_BONUS));
            tag.setInteger(RUNIC_TAG, Math.max(0, base));
            tag.removeTag(LEGACY_BASE);
            tag.removeTag(LEGACY_BONUS);
            tag.removeTag(LEGACY_BASE_OLD);
            tag.removeTag(LEGACY_TMP_OLD);
        }
    }

    private static NBTTagCompound getState(EntityPlayer player) {
        NBTTagCompound persisted = player.getEntityData();
        if (!persisted.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            persisted.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        NBTTagCompound root = persisted.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!root.hasKey(ROOT, 10)) root.setTag(ROOT, new NBTTagCompound());
        return root.getCompoundTag(ROOT);
    }

    private static final class WardSummary {
        private int count;
        private float efficiency;
    }

    private static final class WardSnapshot {
        private final boolean active;
        private final int currentBits;
        private final int max;
        private final int fortificationBits;

        private WardSnapshot(boolean active, float current, int max, float fortification) {
            this.active = active;
            this.currentBits = Float.floatToIntBits(current);
            this.max = max;
            this.fortificationBits = Float.floatToIntBits(fortification);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WardSnapshot)) return false;
            WardSnapshot other = (WardSnapshot) obj;
            return active == other.active && currentBits == other.currentBits
                    && max == other.max && fortificationBits == other.fortificationBits;
        }

        @Override
        public int hashCode() {
            int result = active ? 1 : 0;
            result = 31 * result + currentBits;
            result = 31 * result + max;
            return 31 * result + fortificationBits;
        }
    }
}
