package therealpant.thaumicattempts.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import thaumcraft.api.casters.ICaster;
import therealpant.thaumicattempts.api.gems.GemDamageSource;
import therealpant.thaumicattempts.api.gems.ITAGemDefinition;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.capability.AmberCasterCapability;
import therealpant.thaumicattempts.capability.IAmberCasterData;
import therealpant.thaumicattempts.combat.ArcaneGuardData;
import therealpant.thaumicattempts.combat.ArcaneMarkData;
import therealpant.thaumicattempts.effects.AmberEffects;
import therealpant.thaumicattempts.effects.AmethystEffects;
import therealpant.thaumicattempts.effects.DiamondEffects;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.util.TAGemArmorUtil;
import therealpant.thaumicattempts.util.TAGemInlayUtil;

public class TAGemEventHandler {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent event) {
        if (event.player == null || event.player.world == null || event.player.world.isRemote) return;
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.player.world.getTotalWorldTime();
        updateArcaneGuard(event.player, now);
        updateDiamondModifiers(event.player);
        updateAmberFrequency(event.player, now);
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

    private void updateAmberFrequency(EntityPlayer player, long now) {
        IAmberCasterData data = AmberCasterCapability.get(player);
        if (data == null) return;
        GemSummary summary = getGemSummary(player, AmberGemDefinition.ID);
        data.tick(now, summary.count >= AmberEffects.SET4_REQUIRED);
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

    private static class GemSummary {
        private final List<Integer> tiers = new ArrayList<>();
        private int count;
    }

    private interface TierBonusResolver {
        float getBonus(int tier);
    }
}
