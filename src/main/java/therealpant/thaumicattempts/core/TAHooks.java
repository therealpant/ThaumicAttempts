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
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.ProvisionRequest;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.items.casters.CasterManager;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.effects.AmberEffects;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.TAGemArmorUtil;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;
import therealpant.thaumicattempts.world.data.TAWorldFluxData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            if (node == null || key == null) return original;

            // whitelist keys we allow to touch (only amber set2)
            String k = key.toLowerCase(Locale.ROOT);
            if (!isAmberSet2Key(k)) return original;

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
                int result = original + 1;
                System.out.println("[TA] Amber SET2 lens buff: key=" + k + " original=" + original
                        + " result=" + result);
                return result;
            }

            return original;
        } catch (Throwable t) {
            // NEVER break focus casting.
            return original;
        }
    }

    private static boolean isAmberSet2Key(String key) {
        if (key == null) return false;
        return "power".equals(key)
                || "duration".equals(key)
                || "radius".equals(key)
                || "fork".equals(key)
                || "forks".equals(key);
    }

    public static boolean hasAmberSet2(EntityPlayer player) {
        try {
            if (player == null) return false;
            return countAmber(player) >= AmberEffects.SET2_REQUIRED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getFocusSettingValueTextWithAmber(NodeSetting setting, EntityPlayer player) {
        if (setting == null) return null;
        String valueText;
        try {
            valueText = setting.getValueText();
        } catch (Throwable t) {
            return null;
        }
        try {
            if (!hasAmberSet2(player)) return valueText;

            String key = resolveFocusSettingKey(setting);
            if (key == null || !isAmberSet2Key(key)) return valueText;

            Integer value = getFocusSettingValue(setting);
            if (value == null) {
                return incrementNumberInText(valueText);
            }

            String updated = replaceNumberInText(valueText, value, value + 1);
            return updated != null ? updated : valueText;
        } catch (Throwable t) {
            return valueText;
        }
    }

    public static int getFocusSettingTextColorWithAmber(NodeSetting setting, EntityPlayer player) {
        try {
            if (!hasAmberSet2(player)) return 0xFFFFFF;
            String key = resolveFocusSettingKey(setting);
            if (key != null && isAmberSet2Key(key)) {
                return 0x55FF55;
            }
            return 0xFFFFFF;
        } catch (Throwable t) {
            return 0xFFFFFF;
        }
    }

    private static String resolveFocusSettingKey(NodeSetting setting) {
        if (setting == null) return null;
        try {
            Method method = setting.getClass().getMethod("getKey");
            Object result = method.invoke(setting);
            if (result instanceof String) {
                return ((String) result).toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = setting.getClass().getMethod("getName");
            Object result = method.invoke(setting);
            if (result instanceof String) {
                return ((String) result).toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        String[] fields = new String[]{"key", "name", "id", "identifier"};
        for (String fieldName : fields) {
            try {
                Field field = setting.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(setting);
                if (result instanceof String) {
                    return ((String) result).toLowerCase(Locale.ROOT);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Integer getFocusSettingValue(NodeSetting setting) {
        if (setting == null) return null;
        try {
            Method method = setting.getClass().getMethod("getValue");
            Object result = method.invoke(setting);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {
        }
        try {
            Field field = setting.getClass().getDeclaredField("value");
            field.setAccessible(true);
            Object result = field.get(setting);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String incrementNumberInText(String valueText) {
        if (valueText == null) return null;
        Matcher matcher = Pattern.compile("-?\\d+").matcher(valueText);
        if (!matcher.find()) return valueText;
        String number = matcher.group();
        try {
            int current = Integer.parseInt(number);
            int updated = current + 1;
            return valueText.substring(0, matcher.start()) + updated + valueText.substring(matcher.end());
        } catch (NumberFormatException ignored) {
            return valueText;
        }
    }

    private static String replaceNumberInText(String valueText, int oldValue, int newValue) {
        if (valueText == null) return null;
        String oldString = Integer.toString(oldValue);
        if (valueText.equals(oldString)) {
            return Integer.toString(newValue);
        }
        Matcher matcher = Pattern.compile("-?\\d+").matcher(valueText);
        if (!matcher.find()) return valueText;
        return valueText.substring(0, matcher.start()) + newValue + valueText.substring(matcher.end());
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
        return tcIsOnCooldown(player);
    }

    public static void setCasterCooldownWithAmber(EntityPlayer player, int vanillaCdTicks, ItemStack focusStack, ItemFocus focus) {
        if (player == null) return;

        int amberCount = countAmber(player);

        int cdTicks = vanillaCdTicks;
        if (amberCount >= AmberEffects.SET4_REQUIRED) {
            cdTicks = vanillaCdTicks > 40 ? 40 : vanillaCdTicks;
        }

        CasterManager.setCooldown(player, cdTicks);
    }

    public static float getVisCostWithAmber(EntityPlayer player, ItemFocus focus, ItemStack focusStack) {
        if (focus == null) return 0f;
        float base = focus.getVisCost(focusStack);
        if (player == null) return base;
        int amberCount = countAmber(player);
        if (amberCount < AmberEffects.SET4_REQUIRED) return base;

        int vanillaCd = focus.getActivationTime(focusStack);
        float extra;
        if (vanillaCd <= 40) {
            extra = 0f;
        } else {
            int reducedTicks = vanillaCd - 40;
            int reducedSeconds = (int) Math.ceil(reducedTicks / 20.0);
            extra = 4.0f * reducedSeconds;
        }
        return base + extra;
    }

}
