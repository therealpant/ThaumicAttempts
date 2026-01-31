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
                case "thaumcraft.common.items.casters.ItemCaster":
                    return patchItemCaster(basicClass);
                case "thaumcraft.api.casters.FocusEngine":
                    return patchFocusEngine(basicClass);
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
            if (!"getSettingValue".equals(m.name)
                    || !"(Ljava/lang/String;)I".equals(m.desc)) {
                continue;
            }
            if ((m.access & ACC_ABSTRACT) != 0) {
                continue;
            }
            if (m.instructions == null || m.instructions.size() == 0) {
                continue;
            }
            final int tmp = m.maxLocals;
            m.maxLocals += 1;

        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == IRETURN) {
                InsnList hook = new InsnList();

                hook.add(new VarInsnNode(ISTORE, tmp));
                hook.add(new VarInsnNode(ALOAD, 0));
                hook.add(new VarInsnNode(ILOAD, tmp));
                hook.add(new VarInsnNode(ALOAD, 1));

                hook.add(new MethodInsnNode(
                        INVOKESTATIC,
                        HOOKS,
                        "adjustFocusSetting",
                        "(Lthaumcraft/api/casters/FocusNode;ILjava/lang/String;)I",
                        false
                ));

                m.instructions.insertBefore(insn, hook);
                }
            }
            System.out.println("[ThaumicAttempts] Patched FocusNode (method=" + m.name + m.desc + ") for SET2 lens buffs");
            break;
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[ThaumicAttempts] Patched thaumcraft.api.casters.FocusNode");
        return cw.toByteArray();
    }


    // ---------------- FocusEngine ----------------
    private byte[] patchFocusEngine(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        for (MethodNode m : cn.methods) {
            if ("runFocusPackage".equals(m.name)
                    && m.desc.startsWith("(Lthaumcraft/api/casters/FocusPackage;")) {
                int fpIndex = ((m.access & ACC_STATIC) != 0) ? 0 : 1;
                int tmpPower = -1;
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getType() != AbstractInsnNode.METHOD_INSN) continue;
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if ("thaumcraft/api/casters/FocusEffect".equals(min.owner)
                            && "execute".equals(min.name)
                            && "(Lnet/minecraft/util/math/RayTraceResult;Lthaumcraft/api/casters/Trajectory;FI)Z".equals(min.desc)) {
                        if (tmpPower < 0) {
                            tmpPower = m.maxLocals;
                            m.maxLocals += 1;
                        }
                        InsnList hook = new InsnList();
                        hook.add(new InsnNode(SWAP));
                        hook.add(new VarInsnNode(FSTORE, tmpPower));
                        hook.add(new VarInsnNode(ALOAD, fpIndex));
                        hook.add(new VarInsnNode(FLOAD, tmpPower));
                        hook.add(new MethodInsnNode(
                                INVOKESTATIC,
                                HOOKS,
                                "adjustFocusPower",
                                "(Lthaumcraft/api/casters/FocusPackage;F)F",
                                false
                        ));
                        hook.add(new InsnNode(SWAP));
                        m.instructions.insertBefore(min, hook);
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[ThaumicAttempts] Patched thaumcraft.api.casters.FocusEngine");
        return cw.toByteArray();
    }

    // ---------------- ItemCaster ----------------
    private byte[] patchItemCaster(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode(ASM5);
        cr.accept(cn, 0);

        for (MethodNode m : cn.methods) {
            if (!"onItemRightClick".equals(m.name)
                    || !"(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;".equals(m.desc)) {
                continue;
            }

            int focusIndex = -1;
            int focusStackIndex = -1;

            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getType() != AbstractInsnNode.METHOD_INSN) continue;
                MethodInsnNode min = (MethodInsnNode) insn;
                if ("thaumcraft/common/items/casters/ItemCaster".equals(min.owner)
                        && "getFocus".equals(min.name)
                        && "(Lnet/minecraft/item/ItemStack;)Lthaumcraft/common/items/casters/ItemFocus;".equals(min.desc)) {
                    AbstractInsnNode next = getNextReal(insn);
                    if (next instanceof VarInsnNode && next.getOpcode() == ASTORE) {
                        focusIndex = ((VarInsnNode) next).var;
                    }
                }
                if ("thaumcraft/common/items/casters/ItemCaster".equals(min.owner)
                        && "getFocusStack".equals(min.name)
                        && "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;".equals(min.desc)) {
                    AbstractInsnNode next = getNextReal(insn);
                    if (next instanceof VarInsnNode && next.getOpcode() == ASTORE) {
                        focusStackIndex = ((VarInsnNode) next).var;
                    }
                }
            }

            int playerIndex = ((m.access & ACC_STATIC) != 0) ? 1 : 2;

            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode min = (MethodInsnNode) insn;

                    if ("thaumcraft/common/items/casters/CasterManager".equals(min.owner)
                            && "isOnCooldown".equals(min.name)
                            && "(Lnet/minecraft/entity/EntityLivingBase;)Z".equals(min.desc)
                            && focusIndex >= 0 && focusStackIndex >= 0) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(ALOAD, focusStackIndex));
                        hook.add(new VarInsnNode(ALOAD, focusIndex));
                        m.instructions.insertBefore(min, hook);
                        m.instructions.set(min, new MethodInsnNode(
                                INVOKESTATIC,
                                HOOKS,
                                "isCasterOnCooldownWithAmber",
                                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;Lthaumcraft/common/items/casters/ItemFocus;)Z",
                                false
                        ));
                    }

                    if ("thaumcraft/common/items/casters/CasterManager".equals(min.owner)
                            && "setCooldown".equals(min.name)
                            && "(Lnet/minecraft/entity/EntityLivingBase;I)V".equals(min.desc)
                            && focusIndex >= 0 && focusStackIndex >= 0) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(ALOAD, focusStackIndex));
                        hook.add(new VarInsnNode(ALOAD, focusIndex));
                        m.instructions.insertBefore(min, hook);
                        m.instructions.set(min, new MethodInsnNode(
                                INVOKESTATIC,
                                HOOKS,
                                "setCasterCooldownWithAmber",
                                "(Lnet/minecraft/entity/player/EntityPlayer;ILnet/minecraft/item/ItemStack;Lthaumcraft/common/items/casters/ItemFocus;)V",
                                false
                        ));
                    }

                    if ("thaumcraft/common/items/casters/ItemFocus".equals(min.owner)
                            && "getVisCost".equals(min.name)
                            && "(Lnet/minecraft/item/ItemStack;)F".equals(min.desc)
                            && focusIndex >= 0 && focusStackIndex >= 0) {
                        AbstractInsnNode cursor = getPreviousReal(insn);
                        AbstractInsnNode focusStackLoad = null;
                        AbstractInsnNode focusLoad = null;
                        while (cursor != null && (focusStackLoad == null || focusLoad == null)) {
                            if (cursor instanceof VarInsnNode && cursor.getOpcode() == ALOAD) {
                                int var = ((VarInsnNode) cursor).var;
                                if (focusStackLoad == null && var == focusStackIndex) {
                                    focusStackLoad = cursor;
                                } else if (focusLoad == null && var == focusIndex) {
                                    focusLoad = cursor;
                                }
                            }
                            cursor = getPreviousReal(cursor);
                        }
                        if (focusLoad != null && focusStackLoad != null) {
                            InsnList hook = new InsnList();
                            hook.add(new VarInsnNode(ALOAD, playerIndex));
                            hook.add(new VarInsnNode(ALOAD, focusIndex));
                            hook.add(new VarInsnNode(ALOAD, focusStackIndex));
                            hook.add(new MethodInsnNode(
                                    INVOKESTATIC,
                                    HOOKS,
                                    "getVisCostWithAmber",
                                    "(Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/common/items/casters/ItemFocus;Lnet/minecraft/item/ItemStack;)F",
                                    false
                            ));
                            m.instructions.insertBefore(focusLoad, hook);
                            m.instructions.remove(focusLoad);
                            m.instructions.remove(focusStackLoad);
                            m.instructions.remove(insn);
                        }
                    }
                }
                insn = next;
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[ThaumicAttempts] Patched thaumcraft.common.items.casters.ItemCaster");
        return cw.toByteArray();
    }

    private AbstractInsnNode getNextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode)) {
            next = next.getNext();
        }
        return next;
    }

    private AbstractInsnNode getPreviousReal(AbstractInsnNode insn) {
        AbstractInsnNode prev = insn != null ? insn.getPrevious() : null;
        while (prev != null && (prev instanceof LabelNode || prev instanceof LineNumberNode || prev instanceof FrameNode)) {
            prev = prev.getPrevious();
        }
        return prev;
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
