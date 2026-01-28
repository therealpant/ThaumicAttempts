package therealpant.thaumicattempts.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import therealpant.thaumicattempts.config.TAConfig;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class ThaumicAttemptsTransformer implements IClassTransformer {


    // ВАЖНО: путь к реальному TAHooks
    private static final String HOOKS = "therealpant/thaumicattempts/core/TAHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        try {
            switch (transformedName) {
                case "thaumcraft.common.blocks.basic.BlockPillar":
                    if (!TAConfig.ENABLE_PILLAR_MODEL_REPLACEMENT) {
                        System.out.println("[ThaumicAttempts] Pillar model replacement DISABLED in config – BlockPillar left unpatched.");
                        return basicClass;
                    }
                    return patchBlockPillar(basicClass);
                case "net.minecraft.item.crafting.IRecipe":
                    return patchIRecipe(basicClass);
                case "thaumcraft.api.golems.ProvisionRequest":
                    return patchProvisionRequest(basicClass);
                case "thaumcraft.api.golems.tasks.Task":
                    return patchTask(basicClass);
                case "thaumcraft.api.golems.GolemHelper":
                    return patchGolemHelper(basicClass);
                case "thaumcraft.common.golems.tasks.TaskHandler":
                    return patchTaskHandler(basicClass);
                case "thaumcraft.api.casters.FocusNode":
                    return patchFocusNode(basicClass);
                    default:
                    return basicClass;
            }
        } catch (Throwable t) {
            System.err.println("[ThaumicAttempts] Failed to transform " + transformedName);
            t.printStackTrace();
            return basicClass;
        }
    }

    private byte[] patchBlockPillar(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        boolean hasHasTE = false;
        boolean hasCreateTE = false;
        boolean hasGetRenderType = false;

        for (MethodNode m : cn.methods) {
            if ("hasTileEntity".equals(m.name)
                    && "(Lnet/minecraft/block/state/IBlockState;)Z".equals(m.desc)) {
                hasHasTE = true;
            }
            if ("createTileEntity".equals(m.name)
                    && "(Lnet/minecraft/world/World;Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/tileentity/TileEntity;".equals(m.desc)) {
                hasCreateTE = true;
            }
            if ("getRenderType".equals(m.name)
                    && "(Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/util/EnumBlockRenderType;".equals(m.desc)) {
                hasGetRenderType = true;
            }
        }

        // === public boolean hasTileEntity(IBlockState state) { return true; } ===
        if (!hasHasTE) {
            MethodNode mn = new MethodNode(
                    ACC_PUBLIC,
                    "hasTileEntity",
                    "(Lnet/minecraft/block/state/IBlockState;)Z",
                    null,
                    null
            );
            InsnList insn = new InsnList();
            insn.add(new InsnNode(ICONST_1)); // true
            insn.add(new InsnNode(IRETURN));
            mn.instructions = insn;
            cn.methods.add(mn);

            System.out.println("[ThaumicAttempts] Added hasTileEntity to BlockPillar");
        }

        // === public TileEntity createTileEntity(World world, IBlockState state) { return new TilePillar(); } ===
        if (!hasCreateTE) {
            MethodNode mn = new MethodNode(
                    ACC_PUBLIC,
                    "createTileEntity",
                    "(Lnet/minecraft/world/World;Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/tileentity/TileEntity;",
                    null,
                    null
            );

            InsnList insn = new InsnList();
            insn.add(new TypeInsnNode(NEW, "therealpant/thaumicattempts/tile/TilePillar"));
            insn.add(new InsnNode(DUP));
            insn.add(new MethodInsnNode(
                    INVOKESPECIAL,
                    "therealpant/thaumicattempts/tile/TilePillar",
                    "<init>",
                    "()V",
                    false
            ));
            insn.add(new InsnNode(ARETURN));
            mn.instructions = insn;
            cn.methods.add(mn);

            System.out.println("[ThaumicAttempts] Added createTileEntity to BlockPillar");
        }

        // === public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; } ===
        if (!hasGetRenderType) {
            MethodNode mn = new MethodNode(
                    ACC_PUBLIC,
                    "getRenderType",
                    "(Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/util/EnumBlockRenderType;",
                    null,
                    null
            );

            InsnList insn = new InsnList();
            insn.add(new FieldInsnNode(
                    GETSTATIC,
                    "net/minecraft/util/EnumBlockRenderType",
                    "INVISIBLE",
                    "Lnet/minecraft/util/EnumBlockRenderType;"
            ));
            insn.add(new InsnNode(ARETURN));

            mn.instructions = insn;
            cn.methods.add(mn);

            System.out.println("[ThaumicAttempts] Added getRenderType(INVISIBLE) to BlockPillar");
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }


    private byte[] patchIRecipe(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        boolean hasDynamic = false;
        boolean hasCanFit = false;

        for (MethodNode m : cn.methods) {
            if ("func_192399_d".equals(m.name) && "()Z".equals(m.desc)) {
                hasDynamic = true;
            } else if ("func_194133_a".equals(m.name) && "(II)Z".equals(m.desc)) {
                hasCanFit = true;
            }
        }

        if (!hasDynamic) {
            MethodNode shim = new MethodNode(ACC_PUBLIC, "func_192399_d", "()Z", null, null);
            shim.instructions.add(new InsnNode(ICONST_0));
            shim.instructions.add(new InsnNode(IRETURN));
            cn.methods.add(shim);
            System.out.println("[ThaumicAttempts] Added func_192399_d shim to IRecipe");
        }

        if (!hasCanFit) {
            MethodNode shim = new MethodNode(ACC_PUBLIC, "func_194133_a", "(II)Z", null, null);
            shim.instructions.add(new InsnNode(ICONST_1));
            shim.instructions.add(new InsnNode(IRETURN));
            cn.methods.add(shim);
            System.out.println("[ThaumicAttempts] Added func_194133_a shim to IRecipe");
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    // ---------------- ProvisionRequest ----------------
    private byte[] patchProvisionRequest(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        for (MethodNode m : cn.methods) {
            if ("<init>".equals(m.name)) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == RETURN) {
                        InsnList inject = new InsnList();
                        inject.add(new VarInsnNode(ALOAD, 0));
                        inject.add(new MethodInsnNode(
                                INVOKESTATIC,
                                HOOKS,
                                "onProvisionConstruct",
                                "(Lthaumcraft/api/golems/ProvisionRequest;)V",
                                false
                        ));
                        m.instructions.insertBefore(insn, inject);
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[ThaumicAttempts] Patched thaumcraft.api.golems.ProvisionRequest");
        return cw.toByteArray();
    }

    // ---------------- Task ----------------
    private byte[] patchTask(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        for (MethodNode m : cn.methods) {
            if ("setLinkedProvision".equals(m.name)
                    && "(Lthaumcraft/api/golems/ProvisionRequest;)V".equals(m.desc)) {

                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == RETURN) {
                        InsnList inject = new InsnList();
                        inject.add(new VarInsnNode(ALOAD, 0)); // this
                        inject.add(new VarInsnNode(ALOAD, 1)); // req
                        inject.add(new MethodInsnNode(INVOKESTATIC,
                                HOOKS,
                                "onTaskLinkedProvision",
                                "(Lthaumcraft/api/golems/tasks/Task;Lthaumcraft/api/golems/ProvisionRequest;)V",
                                false));
                        m.instructions.insertBefore(insn, inject);
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[ThaumicAttempts] Patched thaumcraft.api.golems.tasks.Task");
        return cw.toByteArray();
    }

    // ---------------- GolemHelper (обёртки с UUID) ----------------
    // (оставляю твою логику, только убеждаемся, что HOOKS верный и CW с FRAMES)
    private byte[] patchGolemHelper(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        String owner = "thaumcraft/api/golems/GolemHelper";

        addProvisionWrapper(cn,
                "(Lnet/minecraft/world/World;Lthaumcraft/api/golems/seals/ISealEntity;Lnet/minecraft/item/ItemStack;Ljava/util/UUID;)V",
                "(Lnet/minecraft/world/World;Lthaumcraft/api/golems/seals/ISealEntity;Lnet/minecraft/item/ItemStack;)V",
                3,
                owner);

        addProvisionWrapper(cn,
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/item/ItemStack;Ljava/util/UUID;)V",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/item/ItemStack;)V",
                4,
                owner);

        addProvisionWrapper(cn,
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;Ljava/util/UUID;)V",
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
                3,
                owner);

        addProvisionWrapper(cn,
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/item/ItemStack;ILjava/util/UUID;)V",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/item/ItemStack;I)V",
                5,
                owner);

        addProvisionWrapper(cn,
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;ILjava/util/UUID;)V",
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;I)V",
                4,
                owner);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[ThaumicAttempts] Patched thaumcraft.api.golems.GolemHelper");
        return cw.toByteArray();
    }

    private void addProvisionWrapper(ClassNode cn,
                                     String newDesc,
                                     String oldDesc,
                                     int uuidIndex,
                                     String owner) {

        for (MethodNode ex : (List<MethodNode>) cn.methods) {
            if ("requestProvisioning".equals(ex.name) && newDesc.equals(ex.desc)) {
                return;
            }
        }

        MethodNode m = new MethodNode(ACC_PUBLIC | ACC_STATIC,
                "requestProvisioning",
                newDesc,
                null,
                null);

        LabelNode labelFallback = new LabelNode();

        // if (uuid == null) goto fallback;
        m.instructions.add(new VarInsnNode(ALOAD, uuidIndex));
        m.instructions.add(new JumpInsnNode(IFNULL, labelFallback));

        // TAHooks.pushProvisionGolem(uuid);
        m.instructions.add(new VarInsnNode(ALOAD, uuidIndex));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,
                HOOKS,
                "pushProvisionGolem",
                "(Ljava/util/UUID;)V",
                false));

        // старый метод
        loadArgsWithoutUUID(m.instructions, newDesc, uuidIndex);
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,
                owner,
                "requestProvisioning",
                oldDesc,
                false));

        // TAHooks.popProvisionGolem();
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,
                HOOKS,
                "popProvisionGolem",
                "()V",
                false));
        m.instructions.add(new InsnNode(RETURN));

        // fallback:
        m.instructions.add(labelFallback);
        loadArgsWithoutUUID(m.instructions, newDesc, uuidIndex);
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,
                owner,
                "requestProvisioning",
                oldDesc,
                false));
        m.instructions.add(new InsnNode(RETURN));

        cn.methods.add(m);
    }

    private void loadArgsWithoutUUID(InsnList insns, String newDesc, int uuidIndex) {
        char[] chars = newDesc.toCharArray();
        int idx = 1;
        int local = 0;

        while (chars[idx] != ')' && local < uuidIndex) {
            char c = chars[idx];
            if (c == 'L') {
                while (chars[idx] != ';') idx++;
                idx++;
                insns.add(new VarInsnNode(ALOAD, local++));
            } else if (c == 'I') {
                insns.add(new VarInsnNode(ILOAD, local++));
                idx++;
            } else if (c == '[') {
                while (chars[idx] == '[') idx++;
                if (chars[idx] == 'L') {
                    while (chars[idx] != ';') idx++;
                    idx++;
                } else {
                    idx++;
                }
                insns.add(new VarInsnNode(ALOAD, local++));
            } else {
                idx++;
            }
        }
    }
    /**
     * Вставляет перед каждым ARETURN:
     *
     *   DUP
     *   ALOAD uuidIndex
     *   TAHooks.filterTasksForGolem(list, golemUUID);
     *
     * Ожидается, что ПЕРЕД ARETURN уже лежит список (оригинальный ALOAD ...).
     */
    private void injectTaskFilter(MethodNode m, int uuidIndex) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == ARETURN) {
                InsnList hook = new InsnList();
                hook.add(new InsnNode(DUP)); // дублируем возвращаемый список
                hook.add(new VarInsnNode(ALOAD, uuidIndex)); // подгружаем UUID голема
                hook.add(new MethodInsnNode(
                        INVOKESTATIC,
                        HOOKS,
                        "filterTasksForGolem",
                        "(Ljava/util/List;Ljava/util/UUID;)V",
                        false
                ));
                m.instructions.insertBefore(insn, hook);
            }
        }
    }

    // ---------------- FocusNode ----------------
    private byte[] patchFocusNode(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        for (MethodNode m : cn.methods) {
            if ("getSettingValue".equals(m.name) && "(Ljava/lang/String;)I".equals(m.desc)) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == IRETURN) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(ALOAD, 1));
                        hook.add(new MethodInsnNode(
                                INVOKESTATIC,
                                HOOKS,
                                "adjustFocusSetting",
                                "(ILjava/lang/String;)I",
                                false
                        ));
                        m.instructions.insertBefore(insn, hook);
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[ThaumicAttempts] Patched thaumcraft.api.casters.FocusNode");
        return cw.toByteArray();
    }


    // ---------------- TaskHandler ----------------
    private byte[] patchTaskHandler(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        for (MethodNode m : cn.methods) {
            // Оба метода в Thaumcraft: static (int, UUID, Entity) -> ArrayList
            if ("getBlockTasksSorted".equals(m.name)
                    && "(ILjava/util/UUID;Lnet/minecraft/entity/Entity;)Ljava/util/ArrayList;".equals(m.desc)) {
                injectTaskFilter(m, 1); // uuid в локале 1
            }

            if ("getEntityTasksSorted".equals(m.name)
                    && "(ILjava/util/UUID;Lnet/minecraft/entity/Entity;)Ljava/util/ArrayList;".equals(m.desc)) {
                injectTaskFilter(m, 1); // uuid в локале 1
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);

        System.out.println("[ThaumicAttempts] Patched thaumcraft.common.golems.tasks.TaskHandler");
        return cw.toByteArray();
    }

}
