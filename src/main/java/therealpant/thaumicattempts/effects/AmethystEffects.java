package therealpant.thaumicattempts.effects;

public final class AmethystEffects {
    public static final int MAX_TIER = 3;
    public static final int SET2_REQUIRED = 2;
    public static final int SET4_REQUIRED = 4;
    public static final int TIER1_STACK_BONUS = 3;
    public static final int TIER2_STACK_BONUS = 4;
    public static final int TIER3_STACK_BONUS = 5;
    public static final int SET2_EXTRA_STACKS = 5;
    public static final int BASE_DURATION_TICKS = 100;
    public static final int SET2_EXTRA_DURATION_TICKS = 60;
    public static final int STACK_COOLDOWN_TICKS = 20;
    public static final float STACK_DAMAGE_REDUCTION = 0.01f;
    public static final float EMPOWERED_MULTIPLIER = 1.5f;
    public static final int EMPOWERED_DURATION_TICKS = 100;
    public static final int EMPOWERED_STACK_COST = 10;
    public static final int EMPOWERED_COOLDOWN_TICKS = 300;

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
