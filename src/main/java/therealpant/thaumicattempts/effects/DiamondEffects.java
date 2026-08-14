package therealpant.thaumicattempts.effects;

import java.util.UUID;

public final class DiamondEffects {
    public static final int MAX_TIER = 3;
    public static final float TIER1_ATTACK_SPEED_BONUS = 0.03f;
    public static final float TIER2_ATTACK_SPEED_BONUS = 0.04f;
    public static final float TIER3_ATTACK_SPEED_BONUS = 0.05f;
    public static final float MAX_ATTACK_SPEED_BONUS = 0.20f;
    public static final int SET2_REQUIRED = 2;
    public static final int SET4_REQUIRED = 4;

    public static final int SET2_HIT_THRESHOLD = 3;
    public static final int SET4_VIS_PER_HIT = 6;
    public static final int SET4_FIREFLY_VIS_THRESHOLD = 36;
    public static final int SET4_FIREFLY_DURATION_SECONDS = 5;
    public static final int SET4_FIREFLY_PROJECTILES = 3;
    public static final int SET4_FIREFLY_FLUX_POWER = 8;
    public static final UUID ATTACK_SPEED_UUID = UUID.fromString("cdb09f60-4c9e-4c7f-bd2d-5e1b7e2c9253");


    private DiamondEffects() {}

    public static float getAttackSpeedBonusPerGem(int tier) {
        switch (tier) {
            case 1:
                return TIER1_ATTACK_SPEED_BONUS;
            case 2:
                return TIER2_ATTACK_SPEED_BONUS;
            case 3:
                return TIER3_ATTACK_SPEED_BONUS;
            default:
                return 0f;
        }
    }
}
