package therealpant.thaumicattempts.util;

import java.lang.reflect.Method;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

public final class RunicShieldAdapter {
    private static final String TT_HANDLER = "mod.emt.thaumictweaker.events.RunicShieldingHandler";

    private static boolean checked;
    private static boolean available;

    private static Method getMethod; // getRunicShielding(EntityLivingBase) -> double
    private static Method setMethod; // setRunicShielding(EntityLivingBase, double)

    private RunicShieldAdapter() {}

    public static boolean isTTActive() {
        if (!isThaumicTweakerLoaded()) return false;
        ensureMethods();
        return available;
    }

    public static float getCurrentShield(EntityPlayer player) {
        if (player == null) return 0f;

        if (isThaumicTweakerLoaded() && ensureMethods()) {
            try {
                Object v = getMethod.invoke(null, (EntityLivingBase) player);
                if (v instanceof Number) return ((Number) v).floatValue();
            } catch (Throwable ignored) {}
        }

        return player.getAbsorptionAmount();
    }

    public static void setCurrentShield(EntityPlayer player, float value) {
        if (player == null) return;

        if (isThaumicTweakerLoaded() && ensureMethods()) {
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
            available = (getMethod != null && setMethod != null);
        } catch (Throwable ignored) {
            getMethod = null;
            setMethod = null;
            available = false;
        }

        return available;
    }
}
