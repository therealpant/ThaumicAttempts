package therealpant.thaumicattempts.events;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import thaumcraft.api.casters.ICaster;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.gems.GemDamageSource;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.combat.ArcaneGuardData;
import therealpant.thaumicattempts.combat.ArcaneMarkData;
import therealpant.thaumicattempts.core.TAHooks;
import therealpant.thaumicattempts.effects.AmberEffects;
import therealpant.thaumicattempts.effects.AmethystEffects;
import therealpant.thaumicattempts.effects.DiamondEffects;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.util.TAGemInlayUtil;

public class TAGemEventHandler {
    private static final String TAG_AMBER_LAST_CAST = "ta_amber_last_cast";

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event) {
        if (event.player == null || event.player.world == null || event.player.world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.player.world.getTotalWorldTime();
        updateArcaneGuard(event.player, now);
        updateDiamondModifiers(event.player);
    }

    @SubscribeEvent
    public void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;

        GemSummary amethystSummary = getGemSummary(player, AmethystGemDefinition.ID);
        if (amethystSummary.count > 0) {
            applyAmethystGuard(player, amethystSummary, event);
            damageGemInlays(player, GemDamageSource.ON_PLAYER_HURT);
        }
    }

    @SubscribeEvent
    public void onLivingHurtByPlayer(LivingHurtEvent event) {
        if (event.getEntityLiving() == null || event.getEntityLiving().world.isRemote) return;
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

        if (melee) {
            GemSummary diamondSummary = getGemSummary(player, DiamondGemDefinition.ID);
            if (diamondSummary.count > 0) {
                float bonus = getTotalDamageBonus(diamondSummary, DiamondEffects::getDamageBonusPerGem);
                if (bonus > 0f) {
                    event.setAmount(event.getAmount() * (1.0f + bonus));
                }
                if (diamondSummary.count >= DiamondEffects.SET4_REQUIRED) {
                    handleArcaneMark(player, event.getEntityLiving());
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

        long now = player.world.getTotalWorldTime();
        TAHooks.pushFocusCastingPlayer(player, now + AmberEffects.FOCUS_CONTEXT_TICKS);

        if (amberSummary.count >= AmberEffects.SET4_REQUIRED) {
            if (!canAmberCast(player, now)) {
                event.setCanceled(true);
                return;
            }
            int cooldownTicks = getFocusCooldownTicks(player, stack);
            int cooldownSeconds = cooldownTicks / 20;
            int extraVis = Math.max(0, cooldownSeconds - AmberEffects.SET4_BASE_SECONDS) * AmberEffects.SET4_EXTRA_VIS_PER_SECOND;
            if (extraVis > 0 && !consumeExtraVis(player, stack, extraVis)) {
                event.setCanceled(true);
                return;
            }
            setAmberLastCast(player, now);
            clearFocusCooldown(player, stack, AmberEffects.SET4_MIN_INTERVAL_TICKS);
        }

        damageGemInlays(player, GemDamageSource.ON_FOCUS_CAST);
    }

    private void updateArcaneGuard(EntityPlayer player, long now) {
        ArcaneGuardData data = ArcaneGuardData.get(player.world);
        ArcaneGuardData.GuardState state = data.getState(player.getUniqueID());
        boolean changed = false;

        if (state.stacks > 0 && now > state.expireTime) {
            state.stacks = 0;
            changed = true;
        }
        if (state.empoweredUntil > 0 && now >= state.empoweredUntil) {
            state.stacks = Math.max(0, state.stacks - AmethystEffects.EMPOWERED_STACK_COST);
            state.empoweredUntil = 0;
            changed = true;
        }
        if (changed) {
            data.markDirty();
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

    private void applyAmethystGuard(EntityPlayer player, GemSummary summary, LivingHurtEvent event) {
        long now = player.world.getTotalWorldTime();
        ArcaneGuardData data = ArcaneGuardData.get(player.world);
        ArcaneGuardData.GuardState state = data.getState(player.getUniqueID());

        int maxStacks = 0;
        for (int tier : summary.tiers) {
            maxStacks += AmethystEffects.getStackBonusForTier(tier);
        }
        if (summary.count >= AmethystEffects.SET2_REQUIRED) {
            maxStacks += AmethystEffects.SET2_EXTRA_STACKS;
        }

        if (state.stacks > 0 && now > state.expireTime) {
            state.stacks = 0;
        }

        if (maxStacks > 0 && now - state.lastStackTime >= AmethystEffects.STACK_COOLDOWN_TICKS) {
            state.stacks = Math.min(maxStacks, state.stacks + 1);
            state.lastStackTime = now;
            state.expireTime = now + AmethystEffects.BASE_DURATION_TICKS
                    + (summary.count >= AmethystEffects.SET2_REQUIRED ? AmethystEffects.SET2_EXTRA_DURATION_TICKS : 0);
        }

        boolean empoweredActive = state.empoweredUntil > now;
        if (summary.count >= AmethystEffects.SET4_REQUIRED
                && state.stacks >= maxStacks
                && now >= state.cooldownUntil
                && !empoweredActive) {
            state.empoweredUntil = now + AmethystEffects.EMPOWERED_DURATION_TICKS;
            state.cooldownUntil = now + AmethystEffects.EMPOWERED_COOLDOWN_TICKS;
            empoweredActive = true;
        }

        float reduction = state.stacks * AmethystEffects.STACK_DAMAGE_REDUCTION;
        if (empoweredActive) {
            reduction *= AmethystEffects.EMPOWERED_MULTIPLIER;
        }
        float newAmount = event.getAmount() * Math.max(0f, 1.0f - reduction);
        event.setAmount(newAmount);
        data.markDirty();
    }

    private void handleArcaneMark(EntityPlayer player, EntityLivingBase target) {
        if (target == null) return;
        ArcaneMarkData data = ArcaneMarkData.get(player.world);
        ArcaneMarkData.MarkState state = data.getState(player.getUniqueID());
        state.hitCounter++;
        if (state.hitCounter < DiamondEffects.MARK_HIT_THRESHOLD) {
            data.markDirty();
            return;
        }
        state.hitCounter = 0;
        data.markDirty();

        long now = player.world.getTotalWorldTime();
        int stage = getArcaneMarkStage(target, now);
        int strikes;
        int nextStage;
        if (stage == 1) {
            strikes = 2;
            nextStage = 2;
        } else if (stage == 2) {
            strikes = 4;
            nextStage = 0;
        } else {
            strikes = 1;
            nextStage = 1;
        }

        for (int i = 0; i < strikes; i++) {
            target.attackEntityFrom(DamageSource.MAGIC, DiamondEffects.ARCANE_MARK_DAMAGE);
        }
        setArcaneMarkStage(target, nextStage, now);
    }

    private int getArcaneMarkStage(EntityLivingBase target, long now) {
        NBTTagCompound data = target.getEntityData();
        if (!data.hasKey(DiamondEffects.MARK_STAGE_TAG) || !data.hasKey(DiamondEffects.MARK_EXPIRE_TAG)) {
            return 0;
        }
        long expires = data.getLong(DiamondEffects.MARK_EXPIRE_TAG);
        if (expires <= now) {
            data.removeTag(DiamondEffects.MARK_STAGE_TAG);
            data.removeTag(DiamondEffects.MARK_EXPIRE_TAG);
            return 0;
        }
        return data.getInteger(DiamondEffects.MARK_STAGE_TAG);
    }

    private void setArcaneMarkStage(EntityLivingBase target, int stage, long now) {
        NBTTagCompound data = target.getEntityData();
        if (stage <= 0) {
            data.removeTag(DiamondEffects.MARK_STAGE_TAG);
            data.removeTag(DiamondEffects.MARK_EXPIRE_TAG);
            return;
        }
        data.setInteger(DiamondEffects.MARK_STAGE_TAG, stage);
        data.setLong(DiamondEffects.MARK_EXPIRE_TAG, now + DiamondEffects.MARK_DURATION_TICKS);
    }

    private boolean canAmberCast(EntityPlayer player, long now) {
        NBTTagCompound data = player.getEntityData();
        long last = data.getLong(TAG_AMBER_LAST_CAST);
        return now - last >= AmberEffects.SET4_MIN_INTERVAL_TICKS;
    }

    private void setAmberLastCast(EntityPlayer player, long now) {
        player.getEntityData().setLong(TAG_AMBER_LAST_CAST, now);
    }

    private boolean isCasterItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ICaster;
    }

    private int getFocusCooldownTicks(EntityPlayer player, ItemStack stack) {
        try {
            Class<?> casterManager = Class.forName("thaumcraft.api.casters.CasterManager");
            for (Method method : casterManager.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                if (!(method.getName().equals("getFocusCooldown") || method.getName().equals("getCooldown"))) continue;
                if (!int.class.equals(method.getReturnType())) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && EntityPlayer.class.isAssignableFrom(params[0])
                        && ItemStack.class.isAssignableFrom(params[1])) {
                    return (int) method.invoke(null, player, stack);
                }
                if (params.length == 1 && EntityPlayer.class.isAssignableFrom(params[0])) {
                    return (int) method.invoke(null, player);
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException ex) {
            ThaumicAttempts.LOGGER.error("[TA] Amber cooldown lookup failed", ex);
        }
        return 0;
    }

    private void clearFocusCooldown(EntityPlayer player, ItemStack stack, int minTicks) {
        try {
            Class<?> casterManager = Class.forName("thaumcraft.api.casters.CasterManager");
            for (Method method : casterManager.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                if (!(method.getName().equals("setFocusCooldown") || method.getName().equals("setCooldown"))) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && EntityPlayer.class.isAssignableFrom(params[0]) && params[1] == int.class) {
                    method.invoke(null, player, minTicks);
                    return;
                }
                if (params.length == 3 && EntityPlayer.class.isAssignableFrom(params[0])
                        && ItemStack.class.isAssignableFrom(params[1]) && params[2] == int.class) {
                    method.invoke(null, player, stack, minTicks);
                    return;
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException ex) {
            ThaumicAttempts.LOGGER.error("[TA] Amber cooldown override failed", ex);
        }
    }

    private boolean consumeExtraVis(EntityPlayer player, ItemStack stack, int extraVis) {
        try {
            Class<?> rechargeHelper = Class.forName("thaumcraft.api.items.RechargeHelper");
            for (Method method : rechargeHelper.getMethods()) {
                if (!method.getName().equals("consumeCharge")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 3
                        && ItemStack.class.isAssignableFrom(params[0])
                        && EntityLivingBase.class.isAssignableFrom(params[1])
                        && params[2] == float.class) {
                    Object result = method.invoke(null, stack, player, (float) extraVis);
                    return result instanceof Boolean && (Boolean) result;
                }
                if (params.length == 4
                        && ItemStack.class.isAssignableFrom(params[0])
                        && EntityLivingBase.class.isAssignableFrom(params[1])
                        && params[2] == float.class
                        && params[3] == boolean.class) {
                    Object result = method.invoke(null, stack, player, (float) extraVis, true);
                    return result instanceof Boolean && (Boolean) result;
                }
                if (params.length == 2
                        && EntityLivingBase.class.isAssignableFrom(params[0])
                        && params[1] == float.class) {
                    Object result = method.invoke(null, player, (float) extraVis);
                    return result instanceof Boolean && (Boolean) result;
                }
                if (params.length == 3
                        && EntityLivingBase.class.isAssignableFrom(params[0])
                        && params[1] == float.class
                        && params[2] == boolean.class) {
                    Object result = method.invoke(null, player, (float) extraVis, true);
                    return result instanceof Boolean && (Boolean) result;
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException ex) {
            ThaumicAttempts.LOGGER.error("[TA] Amber vis drain failed", ex);
        }
        return false;
    }

    private boolean isFocusDamage(DamageSource source) {
        if (source == null) return false;
        String type = source.getDamageType();
        if (type != null && type.toLowerCase(Locale.ROOT).contains("focus")) return true;
        String className = source.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("focus");
    }

    private void damageGemInlays(EntityPlayer player, GemDamageSource source) {
        for (GemInlay inlay : getGemInlays(player)) {
            ITAGemDefinition def = TAGemRegistry.get(inlay.id);
            if (def == null) {
                ThaumicAttempts.LOGGER.error("[TA] Gem inlay missing definition for {}", inlay.id);
                continue;
            }
            if (def.getDamageSource() != source) continue;
            int maxDurability = def.getBaseDurability(inlay.tier);
            if (maxDurability <= 0) {
                ThaumicAttempts.LOGGER.error("[TA] Gem inlay invalid durability for {} tier {}", inlay.id, inlay.tier);
                continue;
            }
            int newDamage = inlay.damage + 1;
            if (newDamage >= maxDurability) {
                TAGemInlayUtil.clearGem(inlay.stack);
            } else {
                TAGemInlayUtil.setDamage(inlay.stack, newDamage);
            }
        }
    }

    private List<GemInlay> getGemInlays(EntityPlayer player) {
        List<GemInlay> inlays = new ArrayList<>();
        for (ItemStack armor : player.inventory.armorInventory) {
            if (armor == null || armor.isEmpty()) continue;
            if (!TAGemInlayUtil.hasGem(armor)) continue;
            ResourceLocation id = TAGemInlayUtil.getGemId(armor);
            if (id == null) continue;
            int tier = TAGemInlayUtil.getTier(armor);
            int damage = TAGemInlayUtil.getDamage(armor);
            inlays.add(new GemInlay(armor, id, tier, damage));
        }
        return inlays;
    }

    private GemSummary getGemSummary(EntityPlayer player, ResourceLocation id) {
        GemSummary summary = new GemSummary();
        for (ItemStack armor : player.inventory.armorInventory) {
            if (armor == null || armor.isEmpty()) continue;
            if (!TAGemInlayUtil.hasGem(armor)) continue;
            ResourceLocation gemId = TAGemInlayUtil.getGemId(armor);
            if (gemId == null || !gemId.equals(id)) continue;
            int tier = TAGemInlayUtil.getTier(armor);
            summary.count++;
            summary.tiers.add(tier);
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

    private static class GemSummary {
        private final List<Integer> tiers = new ArrayList<>();
        private int count;
    }

    private static class GemInlay {
        private final ItemStack stack;
        private final ResourceLocation id;
        private final int tier;
        private final int damage;

        private GemInlay(ItemStack stack, ResourceLocation id, int tier, int damage) {
            this.stack = stack;
            this.id = id;
            this.tier = tier;
            this.damage = damage;
        }
    }

    private interface TierBonusResolver {
        float getBonus(int tier);
    }
}
