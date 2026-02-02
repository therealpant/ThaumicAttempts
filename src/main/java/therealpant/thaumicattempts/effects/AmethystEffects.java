package therealpant.thaumicattempts.effects;

public final class AmethystEffects {
    public static final int MAX_TIER = 3;
    public static final int SET2_REQUIRED = 2;
    public static final int SET4_REQUIRED = 4;

    public static final int TIER1_STACK_BONUS = 3;
    public static final int TIER2_STACK_BONUS = 4;
    public static final int TIER3_STACK_BONUS = 5;

    public static final int STACK_DURATION_TICKS = 20 * 60;
    public static final int RUNIC_UPDATE_INTERVAL_TICKS = 10;

    public static final int SET2_RECHARGE_INTERVAL = 10;
    public static final int SET2_RECHARGE_INTERVAL_HOSTILE = 5;
    public static final double SET2_HOSTILE_RADIUS = 4.0d;

    public static final float WAVE_HEALTH_THRESHOLD = 10.0f;
    public static final int WAVE_COOLDOWN_TICKS = 20 * 60;
    public static final int WAVE_RECHARGE_INTERVAL_TICKS = 10;
    public static final int WAVE_REGEN_DURATION_TICKS = 200;
    public static final int WAVE_REGEN_AMPLIFIER = 2;
    public static final int WAVE_RESISTANCE_REFRESH_TICKS = 20;
    public static final int WAVE_RESISTANCE_AMPLIFIER = 1;

    private AmethystEffects() {}

    public static int getStackBonusForTier(int tier) {
        switch (tier) {
            case 1:
                return TIER1_STACK_BONUS;
            case 2:
                return TIER2_STACK_BONUS;
            case 3:
                return TIER3_STACK_BONUS;
            default:
                return 0;
        }
    }
}
