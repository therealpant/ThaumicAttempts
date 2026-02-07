package therealpant.thaumicattempts.util;

import java.lang.reflect.Method;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

public final class RunicShieldAdapter {
    private static final String TT_HANDLER = "mod.emt.thaumictweaker.events.RunicShieldingHandler";

    private static boolean checked;
    private static boolean available;
    private static boolean customShieldingEnabled;

    private static Method getMethod; // getRunicShielding(EntityLivingBase) -> double
    private static Method setMethod; // setRunicShielding(EntityLivingBase, double)

    private RunicShieldAdapter() {}

    public static boolean isTTActive() {
        if (!isThaumicTweakerLoaded()) return false;
        ensureMethods();
        return available && customShieldingEnabled;
    }

    public static boolean isCustomShieldingEnabled() {
        if (!isThaumicTweakerLoaded()) return false;
        if (!ensureMethods()) return false;
        return customShieldingEnabled;
    }

    public static float getCurrentShield(EntityPlayer player) {
        if (player == null) return 0f;

        if (isCustomShieldingEnabled()) {
            try {
                Object v = getMethod.invoke(null, (EntityLivingBase) player);
                if (v instanceof Number) return ((Number) v).floatValue();
            } catch (Throwable ignored) {}
        }

        return player.getAbsorptionAmount();
    }

    public static void setCurrentShield(EntityPlayer player, float value) {
        if (player == null) return;

        if (isCustomShieldingEnabled()) {
            try {
                setMethod.invoke(null, (EntityLivingBase) player, (double) value);
                return;
            } catch (Throwable ignored) {}
        }

        player.setAbsorptionAmount(value);
    }

    private static boolean isThaumicTweakerLoaded() {
        try {
            return Loader.isModLoaded("thaumictweaker");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean ensureMethods() {
        if (checked) return available;
        checked = true;

        try {
            Class<?> clazz = Class.forName(TT_HANDLER);
            getMethod = clazz.getMethod("getRunicShielding", EntityLivingBase.class);
            setMethod = clazz.getMethod("setRunicShielding", EntityLivingBase.class, double.class);
            customShieldingEnabled = clazz.getField("ENABLE_NEW_RUNIC_SHIELDING").getBoolean(null);
            available = (getMethod != null && setMethod != null);
        } catch (Throwable ignored) {
            getMethod = null;
            setMethod = null;
            available = false;
            customShieldingEnabled = false;
        }

        return available;
    }
}
