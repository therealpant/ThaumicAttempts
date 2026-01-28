package therealpant.thaumicattempts.effects;

import java.util.UUID;

public final class DiamondEffects {
    public static final int MAX_TIER = 3;
    public static final float TIER1_DAMAGE_BONUS = 0.03f;
    public static final float TIER2_DAMAGE_BONUS = 0.04f;
    public static final float TIER3_DAMAGE_BONUS = 0.05f;
    public static final int SET2_REQUIRED = 2;
    public static final int SET4_REQUIRED = 4;
    public static final double SET2_ATTACK_SPEED_BONUS = 0.3d;
    public static final double SET2_MOVE_SPEED_BONUS = 0.2d;
    public static final int MARK_HIT_THRESHOLD = 4;
    public static final float ARCANE_MARK_DAMAGE = 8.0f;
    public static final int MARK_DURATION_TICKS = 200;

    public static final UUID ATTACK_SPEED_UUID = UUID.fromString("cdb09f60-4c9e-4c7f-bd2d-5e1b7e2c9253");
    public static final UUID MOVE_SPEED_UUID = UUID.fromString("4ce80902-3ce2-4d22-8f38-2c86e51a4c48");

    public static final String MARK_STAGE_TAG = "ta_arcane_mark_stage";
    public static final String MARK_EXPIRE_TAG = "ta_arcane_mark_expires";

    private DiamondEffects() {}

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
}