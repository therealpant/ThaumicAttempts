package therealpant.thaumicattempts.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ObfCompat {
    private static final Logger LOGGER = ThaumicAttempts.LOGGER;

    private static Field ENTITY_WORLD;
    private static Field ENTITY_WORLD_SRG;
    private static Method ENTITY_GET_WORLD_MCP;
    private static Method ENTITY_GET_WORLD_SRG;

    private static Method NBT_HAS_KEY_MCP;
    private static Method NBT_HAS_KEY_SRG;
    private static Method NBT_HAS_KEY_TYPED_MCP;
    private static Method NBT_HAS_KEY_TYPED_SRG;
    private static Method NBT_SET_TAG_MCP;
    private static Method NBT_SET_TAG_SRG;
    private static Method NBT_GET_COMPOUND_MCP;
    private static Method NBT_GET_COMPOUND_SRG;

    private static Method ITEMSTACK_IS_EMPTY_SRG;

    private static boolean CHECKED;
    private static boolean LOGGED;

    private ObfCompat() {}

    private static void ensureChecked() {
        if (CHECKED) {
            return;
        }
        CHECKED = true;

        ENTITY_WORLD = resolveField(Entity.class, "world");
        ENTITY_WORLD_SRG = resolveField(Entity.class, "field_70170_p");
        ENTITY_GET_WORLD_MCP = resolveMethod(Entity.class, "getEntityWorld");
        ENTITY_GET_WORLD_SRG = resolveMethod(Entity.class, "func_130014_f_");

        NBT_HAS_KEY_MCP = resolveMethod(NBTTagCompound.class, "hasKey", String.class);
        NBT_HAS_KEY_SRG = resolveMethod(NBTTagCompound.class, "func_74764_b", String.class);
        NBT_HAS_KEY_TYPED_MCP = resolveMethod(NBTTagCompound.class, "hasKey", String.class, int.class);
        NBT_HAS_KEY_TYPED_SRG = resolveMethod(NBTTagCompound.class, "func_150297_b", String.class, int.class);
        NBT_SET_TAG_MCP = resolveMethod(NBTTagCompound.class, "setTag", String.class, NBTBase.class);
        NBT_SET_TAG_SRG = resolveMethod(NBTTagCompound.class, "func_74782_a", String.class, NBTBase.class);
        NBT_GET_COMPOUND_MCP = resolveMethod(NBTTagCompound.class, "getCompoundTag", String.class);
        NBT_GET_COMPOUND_SRG = resolveMethod(NBTTagCompound.class, "func_74775_l", String.class);

        ITEMSTACK_IS_EMPTY_SRG = resolveMethod(ItemStack.class, "func_190926_b");

        logCompatibility();
    }

    private static Field resolveField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logCompatibility() {
        if (LOGGED) {
            return;
        }
        LOGGED = true;
        LOGGER.info("[ObfCompat] Entity.world field: {}, Entity.field_70170_p: {}, getEntityWorld: {}, func_130014_f_: {}",
                ENTITY_WORLD != null, ENTITY_WORLD_SRG != null, ENTITY_GET_WORLD_MCP != null, ENTITY_GET_WORLD_SRG != null);
        LOGGER.info("[ObfCompat] NBT hasKey MCP/SRG: {}/{}, hasKeyTyped MCP/SRG: {}/{}, setTag MCP/SRG: {}/{}, getCompoundTag MCP/SRG: {}/{}",
                NBT_HAS_KEY_MCP != null, NBT_HAS_KEY_SRG != null,
                NBT_HAS_KEY_TYPED_MCP != null, NBT_HAS_KEY_TYPED_SRG != null,
                NBT_SET_TAG_MCP != null, NBT_SET_TAG_SRG != null,
                NBT_GET_COMPOUND_MCP != null, NBT_GET_COMPOUND_SRG != null);
    }

    public static World getWorld(Entity entity) {
        if (entity == null) {
            return null;
        }
        ensureChecked();
        try {
            return entity.world;
        } catch (Throwable ignored) {
        }
        World world = readWorldField(entity, ENTITY_WORLD);
        if (world != null) {
            return world;
        }
        world = readWorldField(entity, ENTITY_WORLD_SRG);
        if (world != null) {
            return world;
        }
        world = invokeWorldMethod(entity, ENTITY_GET_WORLD_MCP);
        if (world != null) {
            return world;
        }
        return invokeWorldMethod(entity, ENTITY_GET_WORLD_SRG);
    }

    public static boolean isRemote(Entity entity) {
        World world = getWorld(entity);
        return world != null && world.isRemote;
    }

    public static boolean isRemote(World world) {
        return world != null && world.isRemote;
    }

    public static NBTTagCompound getEntityDataSafe(Entity entity) {
        if (entity == null) {
            return new NBTTagCompound();
        }
        try {
            NBTTagCompound data = entity.getEntityData();
            return data != null ? data : new NBTTagCompound();
        } catch (Throwable ignored) {
        }
        Method method = resolveMethod(Entity.class, "getEntityData");
        if (method == null) {
            method = resolveMethod(Entity.class, "func_189511_e");
        }
        if (method != null) {
            try {
                Object value = method.invoke(entity);
                if (value instanceof NBTTagCompound) {
                    return (NBTTagCompound) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return new NBTTagCompound();
    }

    public static NBTTagCompound getPersistedPlayerData(EntityPlayer player) {
        if (player == null) {
            return new NBTTagCompound();
        }
        NBTTagCompound data = getEntityDataSafe(player);
        if (!safeHasKeyTyped(data, EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            safeSetTag(data, EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return safeGetCompoundTag(data, EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static boolean safeHasKey(NBTTagCompound tag, String key) {
        if (tag == null) {
            return false;
        }
        try {
            return tag.hasKey(key);
        } catch (NoSuchMethodError ignored) {
        } catch (Throwable ignored) {
        }
        ensureChecked();
        if (NBT_HAS_KEY_MCP != null) {
            try {
                Object value = NBT_HAS_KEY_MCP.invoke(tag, key);
                return value instanceof Boolean && (Boolean) value;
            } catch (Throwable ignored) {
            }
        }
        if (NBT_HAS_KEY_SRG != null) {
            try {
                Object value = NBT_HAS_KEY_SRG.invoke(tag, key);
                return value instanceof Boolean && (Boolean) value;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean safeHasKeyTyped(NBTTagCompound tag, String key, int type) {
        if (tag == null) {
            return false;
        }
        try {
            return tag.hasKey(key, type);
        } catch (NoSuchMethodError ignored) {
        } catch (Throwable ignored) {
        }
        ensureChecked();
        if (NBT_HAS_KEY_TYPED_MCP != null) {
            try {
                Object value = NBT_HAS_KEY_TYPED_MCP.invoke(tag, key, type);
                return value instanceof Boolean && (Boolean) value;
            } catch (Throwable ignored) {
            }
        }
        if (NBT_HAS_KEY_TYPED_SRG != null) {
            try {
                Object value = NBT_HAS_KEY_TYPED_SRG.invoke(tag, key, type);
                return value instanceof Boolean && (Boolean) value;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static void safeSetTag(NBTTagCompound tag, String key, NBTBase value) {
        if (tag == null) {
            return;
        }
        try {
            tag.setTag(key, value);
            return;
        } catch (NoSuchMethodError ignored) {
        } catch (Throwable ignored) {
        }
        ensureChecked();
        if (NBT_SET_TAG_MCP != null) {
            try {
                NBT_SET_TAG_MCP.invoke(tag, key, value);
                return;
            } catch (Throwable ignored) {
            }
        }
        if (NBT_SET_TAG_SRG != null) {
            try {
                NBT_SET_TAG_SRG.invoke(tag, key, value);
            } catch (Throwable ignored) {
            }
        }
    }

    public static NBTTagCompound safeGetCompoundTag(NBTTagCompound tag, String key) {
        if (tag == null) {
            return new NBTTagCompound();
        }
        try {
            return tag.getCompoundTag(key);
        } catch (NoSuchMethodError ignored) {
        } catch (Throwable ignored) {
        }
        ensureChecked();
        if (NBT_GET_COMPOUND_MCP != null) {
            try {
                Object value = NBT_GET_COMPOUND_MCP.invoke(tag, key);
                if (value instanceof NBTTagCompound) {
                    return (NBTTagCompound) value;
                }
            } catch (Throwable ignored) {
            }
        }
        if (NBT_GET_COMPOUND_SRG != null) {
            try {
                Object value = NBT_GET_COMPOUND_SRG.invoke(tag, key);
                if (value instanceof NBTTagCompound) {
                    return (NBTTagCompound) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return new NBTTagCompound();
    }

    public static boolean isEmpty(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        try {
            return stack.isEmpty();
        } catch (NoSuchMethodError ignored) {
        } catch (Throwable ignored) {
        }
        ensureChecked();
        if (ITEMSTACK_IS_EMPTY_SRG != null) {
            try {
                Object value = ITEMSTACK_IS_EMPTY_SRG.invoke(stack);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return stack.getCount() <= 0 || stack.getItem() == null;
    }

    private static World readWorldField(Entity entity, Field field) {
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(entity);
            return value instanceof World ? (World) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static World invokeWorldMethod(Entity entity, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(entity);
            return value instanceof World ? (World) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}