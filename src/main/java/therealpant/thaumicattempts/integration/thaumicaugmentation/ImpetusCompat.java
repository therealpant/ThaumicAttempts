package therealpant.thaumicattempts.integration.thaumicaugmentation;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ImpetusCompat {

    private static final String MODID = "thaumicaugmentation";
    private static final String API_CLASS = "thecodex6824.thaumicaugmentation.api.impetus.ImpetusAPI";
    private static final String CAP_CLASS = "thecodex6824.thaumicaugmentation.api.impetus.ImpetusCapabilities";
    private static final String CONSUMER_CLASS = "thecodex6824.thaumicaugmentation.api.impetus.IImpetusConsumer";
    private static final String NODE_CLASS = "thecodex6824.thaumicaugmentation.api.impetus.IImpetusNode";

    private static final String CONSUME_METHOD = "consumeImpetusFromConnectedProviders";

    private static Capability<?> impetusCapability;
    private static boolean capabilityChecked;

    private ImpetusCompat() {}

    public static boolean isAvailable() {
        return Loader.isModLoaded(MODID);
    }

    public static boolean canConsumeImpetus(TileEntity tile, int cost) {
        return consumeImpetus(tile, cost, true) >= cost;
    }

    public static boolean consumeImpetus(TileEntity tile, int cost) {
        return consumeImpetus(tile, cost, false) >= cost;
    }

    private static int consumeImpetus(TileEntity tile, int cost, boolean simulate) {
        if (!isAvailable() || tile == null) return 0;
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Method[] methods = apiClass.getMethods();
            for (Method method : methods) {
                if (!method.getName().equals(CONSUME_METHOD)) continue;
                Class<?>[] params = method.getParameterTypes();
                Object result = invokeConsume(tile, cost, simulate, method, params);
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    public static boolean isImpetusCapability(Capability<?> capability) {
        return capability != null && capability == getImpetusCapability();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getImpetusCapabilityInstance(Capability<T> capability, TileEntity tile) {
        if (capability == null || tile == null) return null;
        if (!isImpetusCapability(capability)) return null;
        Object node = createProxy(tile, NODE_CLASS);
        if (node == null) return null;
        return (T) node;
    }

    @Nullable
    private static Capability<?> getImpetusCapability() {
        if (capabilityChecked) return impetusCapability;
        capabilityChecked = true;
        if (!isAvailable()) return null;
        try {
            Class<?> capClass = Class.forName(CAP_CLASS);
            Field field = capClass.getDeclaredField("IMPETUS_NODE");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Capability) {
                impetusCapability = (Capability<?>) value;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return impetusCapability;
    }

    @Nullable
    private static Object invokeConsume(TileEntity tile, int cost, boolean simulate, Method method, Class<?>[] params) {
        try {
            if (params.length == 3) {
                Object consumer = null;
                if (params[1].isInstance(tile)) {
                    consumer = tile;
                } else {
                    consumer = createProxy(tile, CONSUMER_CLASS);
                }
                if (consumer == null) return null;
                Object amount = convertNumber(cost, params[0]);
                if (amount == null) return null;
                if (params[2] == boolean.class || params[2] == Boolean.class) {
                    return method.invoke(null, amount, consumer, simulate);
                }
            }
            if (params.length == 4) {
                if (!params[0].isInstance(tile.getWorld()) || !params[1].isInstance(tile.getPos())) {
                    return null;
                }
                Object amount = convertNumber(cost, params[2]);
                if (amount == null) return null;
                return method.invoke(null, tile.getWorld(), tile.getPos(), amount, simulate);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    @Nullable
    private static Object convertNumber(int cost, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) return cost;
        if (targetType == float.class || targetType == Float.class) return (float) cost;
        if (targetType == double.class || targetType == Double.class) return (double) cost;
        if (targetType == long.class || targetType == Long.class) return (long) cost;
        return null;
    }

    @Nullable
    private static Object createProxy(TileEntity tile, String ifaceName) {
        try {
            Class<?> iface = Class.forName(ifaceName);
            return Proxy.newProxyInstance(
                    iface.getClassLoader(),
                    new Class<?>[]{iface},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getWorld".equals(name) || "getWorldObj".equals(name)) {
                            return tile.getWorld();
                        }
                        if ("getPos".equals(name) || "getPosition".equals(name)) {
                            return tile.getPos();
                        }
                        if ("getBlockPos".equals(name)) {
                            return tile.getPos();
                        }
                        if ("isInvalid".equals(name) || "isDisabled".equals(name)) {
                            return false;
                        }
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class || returnType == Boolean.class) {
                            return false;
                        }
                        if (returnType == int.class || returnType == Integer.class) {
                            return 0;
                        }
                        if (returnType == float.class || returnType == Float.class) {
                            return 0.0F;
                        }
                        if (returnType == double.class || returnType == Double.class) {
                            return 0.0D;
                        }
                        if (returnType == long.class || returnType == Long.class) {
                            return 0L;
                        }
                        return null;
                    }
            );
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}