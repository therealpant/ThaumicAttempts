package therealpant.thaumicattempts.integration.thaumicaugmentation;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ImpetusCompat {

    public static final String MODID = "thaumicaugmentation";

    // TAUG 2.1.14
    private static final String CLS_CAP_NODE   = "thecodex6824.thaumicaugmentation.api.impetus.node.CapabilityImpetusNode";
    private static final String CLS_I_STORAGE  = "thecodex6824.thaumicaugmentation.api.impetus.IImpetusStorage";
    private static final String CLS_STORAGE    = "thecodex6824.thaumicaugmentation.api.impetus.ImpetusStorage";
    private static final String CLS_DIM_POS    = "thecodex6824.thaumicaugmentation.api.util.DimensionalBlockPos";
    private static final String CLS_BUF_CONS   = "thecodex6824.thaumicaugmentation.api.impetus.node.prefab.BufferedImpetusConsumer";
    private static final String CLS_RESULT     = "thecodex6824.thaumicaugmentation.api.impetus.node.ConsumeResult";
    private static final String CLS_NODE_HELPER = "thecodex6824.thaumicaugmentation.api.impetus.node.NodeHelper";
    private static final String CLS_I_NODE = "thecodex6824.thaumicaugmentation.api.impetus.node.IImpetusNode";

    private static boolean inited = false;
    private static Capability<?> IMPETUS_NODE_CAP;

    private static Class<?> CLZ_DIMPOS;
    private static Class<?> CLZ_I_STORAGE;

    private static Constructor<?> CTOR_DIMPOS_BlockPos_Int;
    private static Constructor<?> CTOR_STORAGE_Long;

    // ВАЖНО: тут параметр IImpetusStorage, а не ImpetusStorage
    private static Constructor<?> CTOR_BUFCONS_Int_Int_IStorage;
    private static Constructor<?> CTOR_BUFCONS_Int_Int_DimPos_IStorage;

    private static Method M_NODE_init_World;
    private static Method M_NODE_unload;
    private static Method M_NODE_destroy;
    private static Method M_NODE_setLocation_DimPos;
    private static Method M_NODE_getNumInputs;

    private static Method M_CONS_consume_long_bool;
    private static Field  F_RESULT_energyConsumed;
    private static Field  F_RESULT_paths;
    private static Method M_HELPER_validate;
    private static Method M_HELPER_syncAllTransactions;

    private static Method M_HELPER_sync;
    private static boolean SYNC_NEEDS_WORLD;
    private static boolean SYNC_WORLD_FIRST;

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

            CLZ_DIMPOS = Class.forName(CLS_DIM_POS);
            CLZ_I_STORAGE = Class.forName(CLS_I_STORAGE);

            CTOR_DIMPOS_BlockPos_Int = CLZ_DIMPOS.getConstructor(BlockPos.class, int.class);

            Class<?> storageClz = Class.forName(CLS_STORAGE);
            CTOR_STORAGE_Long = storageClz.getConstructor(long.class);

            Class<?> bufConsClz = Class.forName(CLS_BUF_CONS);

            // два реальных конструктора в TAUG 2.1.14:
            // (int,int,IImpetusStorage) и (int,int,DimensionalBlockPos,IImpetusStorage)
            CTOR_BUFCONS_Int_Int_IStorage = bufConsClz.getConstructor(int.class, int.class, CLZ_I_STORAGE);
            CTOR_BUFCONS_Int_Int_DimPos_IStorage = bufConsClz.getConstructor(int.class, int.class, CLZ_DIMPOS, CLZ_I_STORAGE);

            // public методы унаследованы от ImpetusNode, getMethod их найдет
            M_NODE_init_World = bufConsClz.getMethod("init", World.class);
            M_NODE_unload = bufConsClz.getMethod("unload");
            M_NODE_destroy = bufConsClz.getMethod("destroy");
            M_NODE_setLocation_DimPos = bufConsClz.getMethod("setLocation", CLZ_DIMPOS);
            M_NODE_getNumInputs = bufConsClz.getMethod("getNumInputs");

            // consumer consume()
            M_CONS_consume_long_bool = bufConsClz.getMethod("consume", long.class, boolean.class);

            Class<?> resClz = Class.forName(CLS_RESULT);
            // в 2.1.14 это public final long energyConsumed;
            F_RESULT_energyConsumed = resClz.getField("energyConsumed");
            F_RESULT_paths = resClz.getField("paths");

            Class<?> helperClz = Class.forName(CLS_NODE_HELPER);
            Class<?> iNodeClz = Class.forName(CLS_I_NODE);
            M_HELPER_validate = helperClz.getMethod("validate", iNodeClz, World.class);
            Method found = null;
            boolean needsWorld = false;
            boolean worldFirst = false;

            for (Method m : helperClz.getMethods()) {
                String n = m.getName();
                if (!n.equals("syncAllImpetusTransactions") && !n.equals("syncAllTransactions")) continue;

                Class<?>[] p = m.getParameterTypes();

                // TAUG 2.1.14: syncAllImpetusTransactions(Collection<Deque<IImpetusNode>>)
                if (p.length == 1 && Collection.class.isAssignableFrom(p[0])) {
                    found = m;
                    needsWorld = false;
                    worldFirst = false;
                    break;
                }

                // на всякий (другие сборки): sync*(World, Collection) или sync*(Collection, World)
                if (p.length == 2) {
                    boolean p0World = World.class.isAssignableFrom(p[0]);
                    boolean p1World = World.class.isAssignableFrom(p[1]);
                    boolean p0Col   = Collection.class.isAssignableFrom(p[0]);
                    boolean p1Col   = Collection.class.isAssignableFrom(p[1]);

                    if (p0World && p1Col) {
                        found = m;
                        needsWorld = true;
                        worldFirst = true;
                        break;
                    }
                    if (p0Col && p1World) {
                        found = m;
                        needsWorld = true;
                        worldFirst = false;
                        break;
                    }
                }
            }

            M_HELPER_sync = found;
            SYNC_NEEDS_WORLD = needsWorld;
            SYNC_WORLD_FIRST = worldFirst;

// Это поле больше не нужно. Можно оставить null или удалить совсем:
            M_HELPER_syncAllTransactions = null;


        } catch (Throwable t) {
            IMPETUS_NODE_CAP = null;
            // Если хочешь — залогируй t здесь, но не обязательно
        }
    }

    @Nullable
    public static Capability<?> getImpetusNodeCapability() {
        init();
        return IMPETUS_NODE_CAP;
    }

    @Nullable
    public static Object createBufferedConsumerNode(TileEntity te, int maxInputs, int maxOutputs, long bufferCap) {
        init();
        if (IMPETUS_NODE_CAP == null || te == null) return null;

        try {
            World w = te.getWorld();
            int dim = (w == null) ? 0 : w.provider.getDimension();

            Object storage = CTOR_STORAGE_Long.newInstance(bufferCap);

            // пробуем конструктор с DimPos (лучше, т.к. сразу корректная позиция)
            Object dimPos = CTOR_DIMPOS_BlockPos_Int.newInstance(te.getPos(), dim);
            return CTOR_BUFCONS_Int_Int_DimPos_IStorage.newInstance(maxInputs, maxOutputs, dimPos, storage);

        } catch (Throwable t1) {
            // fallback: конструктор без DimPos
            try {
                Object storage = CTOR_STORAGE_Long.newInstance(bufferCap);
                return CTOR_BUFCONS_Int_Int_IStorage.newInstance(maxInputs, maxOutputs, storage);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    public static void initNode(@Nullable Object node, @Nullable World world) {
        if (node == null || world == null) return;
        init();
        try { M_NODE_init_World.invoke(node, world); } catch (Throwable ignored) {}
    }

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

    public static long consumeFromNode(@Nullable Object node, @Nullable World world, long amount, boolean simulate) {
        if (node == null || amount <= 0) return 0L;
        init();

        try {
            Object res = M_CONS_consume_long_bool.invoke(node, amount, simulate);
            long consumed = (long) F_RESULT_energyConsumed.get(res);

            // Визуал (яркие лучи) должен приходить только на сервере и только при реальной транзакции
            if (!simulate && consumed > 0L && world != null && !world.isRemote && M_HELPER_sync != null && F_RESULT_paths != null) {
                Object raw = F_RESULT_paths.get(res);

                if (raw instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) raw;
                    if (!m.isEmpty()) {
                        // TAUG: paths = Map<Deque<IImpetusNode>, Long>
                        // ТРАНЗАКЦИИ = ключи (Deque), значения — просто числа
                        Collection<?> paths = m.keySet();
                        trySync(world, paths);
                    }
                }
            }

            return consumed;
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static void trySync(World world, @Nullable Collection<?> c) {
        if (c == null || c.isEmpty() || M_HELPER_sync == null) return;
        try {
            if (!SYNC_NEEDS_WORLD) {
                M_HELPER_sync.invoke(null, c);
            } else {
                if (SYNC_WORLD_FIRST) M_HELPER_sync.invoke(null, world, c);
                else                  M_HELPER_sync.invoke(null, c, world);
            }
        } catch (Throwable ignored) {}
    }


    public static void validateNode(@Nullable Object node, @Nullable World world) {
        if (node == null || world == null) return;
        init();
        try {
            M_HELPER_validate.invoke(null, node, world);
        } catch (Throwable ignored) {}
    }

    public static boolean hasAnyInputConnection(@Nullable Object node, @Nullable World world) {
        if (node == null) return false;
        init();
        if (world != null) {
            validateNode(node, world);
        }
        try {
            Object value = M_NODE_getNumInputs.invoke(node);
            if (value instanceof Integer) {
                return ((Integer) value) > 0;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
