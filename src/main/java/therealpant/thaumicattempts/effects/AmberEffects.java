package therealpant.thaumicattempts.effects;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AmberEffects {
    public static final int MAX_TIER = 3;
    public static final float TIER1_DAMAGE_BONUS = 0.05f;
    public static final float TIER2_DAMAGE_BONUS = 0.06f;
    public static final float TIER3_DAMAGE_BONUS = 0.07f;
    public static final int SET2_REQUIRED = 2;
    public static final int SET4_REQUIRED = 4;
    public static final int SET4_MIN_INTERVAL_TICKS = 40;
    public static final int SET4_BASE_SECONDS = 2;
    public static final int SET4_EXTRA_VIS_PER_SECOND = 3;
    public static final int FOCUS_CONTEXT_TICKS = 2;

    public static final Set<String> SETTING_KEYS = new HashSet<>(Arrays.asList(
            "power",
            "duration",
            "projectile",
            "radius"
    ));

    private AmberEffects() {}

    public static float getDamageBonusPerGem(int tier) {
        switch (tier) {
            case 1:
                return TIER1_DAMAGE_BONUS;
            case 2:
                return TIER2_DAMAGE_BONUS;
            case 3:
                return TIER3_DAMAGE_BONUS;
            default:
                return 0f;
        }
    }

    public static boolean isSettingKey(String key) {
        if (key == null) return false;
        return SETTING_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }
}
