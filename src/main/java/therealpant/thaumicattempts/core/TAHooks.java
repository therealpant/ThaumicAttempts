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
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.ProvisionRequest;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.items.casters.CasterManager;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.items.casters.CasterManager;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.effects.AmberEffects;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.TAGemInlayUtil;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Map<UUID, Long> COOLDOWN_SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_CLIENT = new ConcurrentHashMap<>();

    private static Map<UUID, Long> cdMap(EntityPlayer p) {
        return p.world.isRemote ? COOLDOWN_CLIENT : COOLDOWN_SERVER;
    }
    private static final ThreadLocal<FocusContext> FOCUS_CONTEXT = new ThreadLocal<>();

    private static final ResourceLocation AMBER_ID =
            new ResourceLocation(ThaumicAttempts.MODID, "amber");

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

    public static void pushFocusCastingPlayer(EntityPlayer player, long expiresAt) {
        if (player == null) return;
        FOCUS_CONTEXT.set(new FocusContext(player, expiresAt));
    }

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

        for (ItemStack armor : player.getArmorInventoryList()) {
            if (armor.isEmpty()) continue;
            if (!TAGemInlayUtil.hasGem(armor)) continue;

            ResourceLocation id = TAGemInlayUtil.getGemId(armor);
            if (AMBER_ID.equals(id)) count++;
        }
        return  count;
    }

    public static float sumAmberDamageBonus(EntityPlayer player) {
        if (player == null) return 0f;
        float bonus = 0f;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (armor.isEmpty()) continue;
            if (!TAGemInlayUtil.hasGem(armor)) continue;
            ResourceLocation id = TAGemInlayUtil.getGemId(armor);
            if (!AMBER_ID.equals(id)) continue;
            int tier = TAGemInlayUtil.getTier(armor);
            bonus += AmberEffects.getDamageBonusPerGem(tier);
        }
        return bonus;
    }

    public static boolean isCasterOnCooldownWithAmber(EntityPlayer player, ItemStack focusStack, ItemFocus focus) {
        try {
            if (player == null) return false;
            Long until = cdMap(player).get(player.getUniqueID());
            return until != null && until > System.currentTimeMillis();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setCasterCooldownWithAmber(EntityPlayer player, int vanillaCdTicks, ItemStack focusStack, ItemFocus focus) {
        try {
            if (player == null) return;

            int amberCount = countAmber(player);
            long now = System.currentTimeMillis();

            int cdTicks = vanillaCdTicks;
            if (amberCount >= AmberEffects.SET4_REQUIRED) {
                // сет 4 янтаря: всегда 2 секунды
                cdTicks = AmberEffects.SET4_MIN_INTERVAL_TICKS; // 40
            }

            cdMap(player).put(player.getUniqueID(), now + cdTicks * 50L);
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

            int cdTicks = focus.getActivationTime(focusStack);
            int cdSeconds = (cdTicks + 19) / 20;
            int ignored = Math.max(0, cdSeconds - AmberEffects.SET4_BASE_SECONDS);
            int extraVis = ignored * AmberEffects.SET4_EXTRA_VIS_PER_SECOND;
            return base + extraVis;
        } catch (Throwable t) {
            return focus != null ? focus.getVisCost(focusStack) : 0f;
        }
    }

    private static class FocusContext {
        private final EntityPlayer player;
        private final long expiresAt;

        private FocusContext(EntityPlayer player, long expiresAt) {
            this.player = player;
            this.expiresAt = expiresAt;
        }
    }
}
