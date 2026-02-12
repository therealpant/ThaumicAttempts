package therealpant.thaumicattempts.integration.thaumicaugmentation;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Safe TAUG (Thaumic Augmentation) compat via reflection.
 * Exposes CapabilityImpetusNode.IMPETUS_NODE and a persistent BufferedImpetusConsumer node.
 */
public final class ImpetusCompat {

    public static final String MODID = "thaumicaugmentation";

    // class names from ThaumicAugmentation-1.12.2-2.1.14.jar
    private static final String CLS_CAP_NODE = "thecodex6824.thaumicaugmentation.api.impetus.node.CapabilityImpetusNode";
    private static final String CLS_DIM_POS  = "thecodex6824.thaumicaugmentation.api.util.DimensionalBlockPos";
    private static final String CLS_STORAGE  = "thecodex6824.thaumicaugmentation.api.impetus.ImpetusStorage";
    private static final String CLS_BUF_CONS = "thecodex6824.thaumicaugmentation.api.impetus.node.prefab.BufferedImpetusConsumer";
    private static final String CLS_RESULT   = "thecodex6824.thaumicaugmentation.api.impetus.node.ConsumeResult";

    private static boolean inited = false;
    private static Capability<?> IMPETUS_NODE_CAP = null;

    private static Constructor<?> CTOR_DIMPOS_BlockPos_Int;
    private static Constructor<?> CTOR_STORAGE_Long;
    private static Constructor<?> CTOR_BUFCONS_Int_Int_DimPos_Storage;

    private static Method M_NODE_init_World;
    private static Method M_NODE_unload;
    private static Method M_NODE_destroy;
    private static Method M_NODE_setLocation_DimPos;

    private static Method M_CONS_consume_long_bool;
    private static Field  F_RESULT_energyConsumed;

    private ImpetusCompat() {}

    public static boolean isLoaded() {
        return Loader.isModLoaded(MODID);
    }

    private static void init() {
        if (inited) return;
        inited = true;

        if (!isLoaded()) return;

        try {
            // CapabilityImpetusNode.IMPETUS_NODE
            Class<?> capClz = Class.forName(CLS_CAP_NODE);
            Field f = capClz.getDeclaredField("IMPETUS_NODE");
            IMPETUS_NODE_CAP = (Capability<?>) f.get(null);

            Class<?> dimPosClz = Class.forName(CLS_DIM_POS);
            CTOR_DIMPOS_BlockPos_Int = dimPosClz.getConstructor(BlockPos.class, int.class);

            Class<?> storageClz = Class.forName(CLS_STORAGE);
            CTOR_STORAGE_Long = storageClz.getConstructor(long.class);

            Class<?> bufConsClz = Class.forName(CLS_BUF_CONS);
            CTOR_BUFCONS_Int_Int_DimPos_Storage = bufConsClz.getConstructor(int.class, int.class, dimPosClz, storageClz);

            // methods on ImpetusNode base class (BufferedImpetusConsumer extends ImpetusNode)
            M_NODE_init_World = bufConsClz.getMethod("init", World.class);
            M_NODE_unload = bufConsClz.getMethod("unload");
            M_NODE_destroy = bufConsClz.getMethod("destroy");
            M_NODE_setLocation_DimPos = bufConsClz.getMethod("setLocation", dimPosClz);

            // consumer consume()
            M_CONS_consume_long_bool = bufConsClz.getMethod("consume", long.class, boolean.class);

            Class<?> resClz = Class.forName(CLS_RESULT);
            F_RESULT_energyConsumed = resClz.getDeclaredField("energyConsumed");
            F_RESULT_energyConsumed.setAccessible(true);

        } catch (Throwable t) {
            // if anything fails, disable compat silently (but you can add your logger here)
            IMPETUS_NODE_CAP = null;
        }
    }

    @Nullable
    public static Capability<?> getImpetusNodeCapability() {
        init();
        return IMPETUS_NODE_CAP;
    }

    public static boolean isImpetusCapability(@Nullable Capability<?> cap) {
        init();
        return cap != null && IMPETUS_NODE_CAP != null && cap == IMPETUS_NODE_CAP;
    }

    /**
     * Create persistent BufferedImpetusConsumer node.
     * @param maxInputs/maxOutputs - limits for linking
     * @param bufferCap - max buffer size inside node
     */
    @Nullable
    public static Object createBufferedConsumerNode(TileEntity te, int maxInputs, int maxOutputs, long bufferCap) {
        init();
        if (IMPETUS_NODE_CAP == null) return null;

        try {
            int dim = te.getWorld() == null ? 0 : te.getWorld().provider.getDimension();
            Object dimPos = CTOR_DIMPOS_BlockPos_Int.newInstance(te.getPos(), dim);
            Object storage = CTOR_STORAGE_Long.newInstance(bufferCap);
            return CTOR_BUFCONS_Int_Int_DimPos_Storage.newInstance(maxInputs, maxOutputs, dimPos, storage);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Call onLoad / after world is available */
    public static void initNode(@Nullable Object node, @Nullable World world) {
        if (node == null || world == null) return;
        init();
        try {
            M_NODE_init_World.invoke(node, world);
        } catch (Throwable ignored) {}
    }

    /** Keep node location correct (important for linking + graphs) */
    public static void updateNodeLocation(@Nullable Object node, @Nullable World world, @Nullable BlockPos pos) {
        if (node == null || world == null || pos == null) return;
        init();
        try {
            Object dimPos = CTOR_DIMPOS_BlockPos_Int.newInstance(pos, world.provider.getDimension());
            M_NODE_setLocation_DimPos.invoke(node, dimPos);
        } catch (Throwable ignored) {}
    }

    public static void unloadNode(@Nullable Object node) {
        if (node == null) return;
        init();
        try { M_NODE_unload.invoke(node); } catch (Throwable ignored) {}
    }

    public static void destroyNode(@Nullable Object node) {
        if (node == null) return;
        init();
        try { M_NODE_destroy.invoke(node); } catch (Throwable ignored) {}
    }

    /** Returns amount actually consumed */
    public static long consumeFromNode(@Nullable Object node, long amount, boolean simulate) {
        if (node == null || amount <= 0) return 0L;
        init();
        try {
            Object res = M_CONS_consume_long_bool.invoke(node, amount, simulate);
            Object v = F_RESULT_energyConsumed.get(res);
            return (v instanceof Long) ? (Long) v : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }
}
