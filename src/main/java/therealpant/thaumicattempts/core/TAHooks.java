package therealpant.thaumicattempts.core;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.FocusModSplit;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.ProvisionRequest;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.items.casters.CasterManager;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.items.casters.foci.FocusModSplitTrajectory;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.capability.AmberCasterCapability;
import therealpant.thaumicattempts.capability.IAmberCasterData;
import therealpant.thaumicattempts.effects.AmberEffects;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.TAGemArmorUtil;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
/**
 * Хуки для интеграции с Thaumcraft-голевами.
 *
 * Здесь:
 * - ThreadLocal для "выбранного" диспетчерского голема,
 * - Проброс UUID из ProvisionRequest в Task (форсированные курьеры),
 * - Фильтрация списка тасков под конкретного голема.
 */
public final class TAHooks {

    // Контекст "этот provisioning создаётся под конкретного диспетчерского голема"
    private static final ThreadLocal<UUID> DISPATCH_GOLEM_CTX = new ThreadLocal<>();

    // ProvisionRequest -> golemUUID, пока не будет создан Task.setLinkedProvision(...)
    private static final Map<ProvisionRequest, UUID> PROVISION_GOLEM =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final ResourceLocation AMBER_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "amber");

    private static Method TC_IS_ON_COOLDOWN;

    private TAHooks() {}

    /* ===================== Контекст от MirrorManager ===================== */

    /**
     * Вызывается из ThaumcraftProvisionHelper перед вызовом GolemHelper.requestProvisioning(...)
     * если MirrorManager нашёл свободного курьера.
     */
    public static void pushDispatchGolem(UUID golemId) {
        if (golemId != null) {
            DISPATCH_GOLEM_CTX.set(golemId);
        }
    }

    /**
     * Вызывается из ThaumcraftProvisionHelper в finally после requestProvisioning(...).
     */
    public static void popDispatchGolem() {
        DISPATCH_GOLEM_CTX.remove();
    }

    /* ===================== Хуки из пропатченных TC классов ===================== */

    /**
     * Хук из пропатченного конструктора thaumcraft.api.golems.ProvisionRequest.
     * Должен вызываться ровно один раз на создание req.
     */
    public static void onProvisionConstruct(ProvisionRequest req) {
        if (req == null) return;
        UUID gid = DISPATCH_GOLEM_CTX.get();
        if (gid != null) {
            PROVISION_GOLEM.put(req, gid);
        }
    }

    /**
     * Хук из пропатченного Task.setLinkedProvision(...)
     * Здесь мы, зная ProvisionRequest, дотягиваемся до заранее сохранённого golemUUID.
     */
    public static void onTaskLinkedProvision(Task task, ProvisionRequest req) {
        if (task == null || req == null) return;

        UUID gid = PROVISION_GOLEM.remove(req);
        if (gid != null) {
            // У самого Task уже есть setGolemUUID (TC6).
            task.setGolemUUID(gid);
        }
    }

    // ==== Backwards compat для ASM-обёрток GolemHelper ====
    // Трансформер вызывает pushProvisionGolem/popProvisionGolem.
    // Делаем их делегатами к актуальным именам, чтобы не ловить NoSuchMethodError.

    @Deprecated
    public static void pushProvisionGolem(UUID golemId) {
        pushDispatchGolem(golemId);
    }

    @Deprecated
    public static void popProvisionGolem() {
        popDispatchGolem();
    }

    /**
     * Хук из пропатченного TaskHandler.getEntityTasksSorted(...)
     * Сигнатура строго: (Ljava/util/List;Ljava/util/UUID;)V
     *
     * list — уже отсортированный список кандидатов.
     * golemId — UUID текущего голема.
     *
     * Логика:
     * - если у таска golemUUID == null — он свободный, его могут брать все,
     * - если golemUUID != null — его может брать только соответствующий голем,
     *   остальные этот таск даже не видят.
     */
    public static void filterTasksForGolem(List<Task> list, UUID golemId) {
        if (list == null) return;

        // Для обычных големов (не закреплённых MirrorManager’ом) —
        // пусть видят всё, кроме явно закреплённых "чужих" задач.
        if (golemId == null) {
            Iterator<Task> it = list.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t == null) {
                    it.remove();
                    continue;
                }
                UUID forced = t.getGolemUUID();
                // если задача закреплена за конкретным големом — скрываем для "безымянных"
                if (forced != null) {
                    it.remove();
                }
            }
            return;
        }

        // Нормальный случай: конкретный голем проходит по списку.
        Iterator<Task> it = list.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t == null) {
                it.remove();
                continue;
            }

            UUID forced = t.getGolemUUID();
            if (forced != null && !forced.equals(golemId)) {
                // задача закреплена за другим големом — выкидываем
                it.remove();
            }
        }
    }

    /* ===================== Focus cast hooks ===================== */
    public static int adjustFocusSetting(FocusNode node, int original, String key) {
        try {
            // fail-open basics
            if (node == null || key == null) return original;

            // whitelist keys we allow to touch (only amber set2)
            String k = key.toLowerCase(Locale.ROOT);
            if (!AmberEffects.isSettingKey(k)) return original;

            // find caster
            FocusPackage fp = node.getPackage();
            if (fp == null) return original;

            EntityLivingBase caster = fp.getCaster();
            if (!(caster instanceof EntityPlayer)) return original;

            EntityPlayer player = (EntityPlayer) caster;

            // count amber gems on armor (sum across 4 pieces)
            int amberCount = countAmber(player);

            // Set of 2 ambers => +1 to selected focus settings
            if (amberCount >= AmberEffects.SET2_REQUIRED) {
                return original + 1;
            }

            return original;
        } catch (Throwable t) {
            // NEVER break focus casting.
            return original;
        }
    }

    public static float adjustFocusPower(FocusPackage focusPackage, float originalPower) {
        try {
            if (focusPackage == null) return originalPower;
            EntityLivingBase caster = focusPackage.getCaster();
            if (!(caster instanceof EntityPlayer)) return originalPower;

            EntityPlayer player = (EntityPlayer) caster;
            float bonus = sumAmberDamageBonus(player);
            if (bonus <= 0f) return originalPower;

            return originalPower * (1.0f + bonus);
        } catch (Throwable t) {
            return originalPower;
        }
    }

    public static int countAmber(EntityPlayer player) {
        if (player == null) return 0;
        int count = 0;

        for (TAGemArmorUtil.GemInlay inlay : TAGemArmorUtil.getEquippedGemInlays(player)) {
            if (AMBER_ID.equals(inlay.getId())) count++;
        }
        return count;
    }

    public static float sumAmberDamageBonus(EntityPlayer player) {
        if (player == null) return 0f;
        float bonus = 0f;
        for (TAGemArmorUtil.GemInlay inlay : TAGemArmorUtil.getEquippedGemInlays(player)) {
            if (!AMBER_ID.equals(inlay.getId())) continue;
            bonus += AmberEffects.getDamageBonusPerGem(inlay.getTier());
        }
        return bonus;
    }

    private static boolean tcIsOnCooldown(EntityLivingBase entity) {
        try {
            if (entity == null) return false;
            if (TC_IS_ON_COOLDOWN == null) {
                TC_IS_ON_COOLDOWN = CasterManager.class.getDeclaredMethod("isOnCooldown", EntityLivingBase.class);
                TC_IS_ON_COOLDOWN.setAccessible(true);
            }
            return (boolean) TC_IS_ON_COOLDOWN.invoke(null, entity);
        } catch (Throwable t) {
            return false; // fail-open, но спам уже не будет, если setCooldown работает
        }
    }

    public static boolean isCasterOnCooldownWithAmber(EntityPlayer player, ItemStack focusStack, ItemFocus focus) {
        try {
            if (player == null) return false;
            int amberCount = countAmber(player);
            if (amberCount < AmberEffects.SET4_REQUIRED) {
                return tcIsOnCooldown(player);
            }
            return tcIsOnCooldown(player);
        } catch (Throwable t) {
            return tcIsOnCooldown(player);
        }
    }

    public static void setCasterCooldownWithAmber(EntityPlayer player, int vanillaCdTicks, ItemStack focusStack, ItemFocus focus) {
        try {
            if (player == null) return;

            int amberCount = countAmber(player);

            int cdTicks = vanillaCdTicks;
            if (amberCount >= AmberEffects.SET4_REQUIRED) {
                cdTicks = Math.max(cdTicks, AmberEffects.SET4_MIN_INTERVAL_TICKS);
                IAmberCasterData data = AmberCasterCapability.get(player);
                if (data != null && player.world != null) {
                    data.recordCast(player.world.getTotalWorldTime());
                }
            }

            CasterManager.setCooldown(player, cdTicks);
        } catch (Throwable t) {
            // fail-open: ничего
        }
    }

    public static float getVisCostWithAmber(EntityPlayer player, ItemFocus focus, ItemStack focusStack) {
        try {
            if (focus == null) return 0f;
            float base = focus.getVisCost(focusStack);
            if (player == null) return base;
            int amberCount = countAmber(player);
            if (amberCount < AmberEffects.SET4_REQUIRED) return base;

            IAmberCasterData data = AmberCasterCapability.get(player);
            if (data == null || player.world == null) return base;
            long now = player.world.getTotalWorldTime();
            data.tick(now, true);
            float extraVis = data.getFrequency() * AmberEffects.SET4_EXTRA_VIS_PER_FREQUENCY;
            return base + extraVis;
        } catch (Throwable t) {
            return focus != null ? focus.getVisCost(focusStack) : 0f;
        }
    }

    public static ArrayList<FocusPackage> getSplitPackagesWithAmber(FocusModSplit split) {
        ArrayList<FocusPackage> packages = getSplitPackagesWithAmberInternal(split);
        return packages;
    }

    public static List<FocusPackage> getSplitPackagesWithAmberList(FocusModSplit split) {
        return getSplitPackagesWithAmberInternal(split);
    }

    private static ArrayList<FocusPackage> getSplitPackagesWithAmberInternal(FocusModSplit split) {
        try {
            if (split == null) return null;
            ArrayList<FocusPackage> packages = split.getSplitPackages();
            if (packages == null || packages.isEmpty()) return packages;
            if (!(split instanceof FocusModSplitTrajectory)) return packages;

            FocusPackage focusPackage = split.getPackage();
            if (focusPackage == null) return packages;

            EntityLivingBase caster = focusPackage.getCaster();
            if (!(caster instanceof EntityPlayer)) return packages;

            if (countAmber((EntityPlayer) caster) < AmberEffects.SET2_REQUIRED) return packages;

            FocusPackage extra = copyFocusPackage(packages.get(0));
            if (extra == null) return packages;

            ArrayList<FocusPackage> withExtra = new ArrayList<>(packages.size() + 1);
            withExtra.addAll(packages);
            withExtra.add(extra);
            return withExtra;
        } catch (Throwable t) {
            try {
                return split != null ? split.getSplitPackages() : null;
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static FocusPackage copyFocusPackage(FocusPackage base) {
        if (base == null) return null;
        try {
            Method copyMethod = base.getClass().getMethod("copy");
            Object copied = copyMethod.invoke(base);
            if (copied instanceof FocusPackage) {
                return (FocusPackage) copied;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            Method cloneMethod = base.getClass().getMethod("clone");
            Object cloned = cloneMethod.invoke(base);
            if (cloned instanceof FocusPackage) {
                return (FocusPackage) cloned;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            Constructor<? extends FocusPackage> ctor = base.getClass().getConstructor(FocusPackage.class);
            return ctor.newInstance(base);
        } catch (Throwable ignored) {
            return base;
        }
    }
}
