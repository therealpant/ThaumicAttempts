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

    public static final int SET2_RESISTANCE_DURATION_TICKS = 2;
    public static final float SET2_MIN_PERCENT = 0.40f;
    public static final float SET2_MAX_PERCENT = 1.0f;

    public static final int OVERLOAD_RESTORE_AMOUNT = 3;
    public static final int OVERLOAD_INTERVAL_TICKS = 20;
    public static final int OVERLOAD_RESET_TICKS = 20 * 20;
    public static final int OVERLOAD_COOLDOWN_TICKS = 20 * 60;

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
