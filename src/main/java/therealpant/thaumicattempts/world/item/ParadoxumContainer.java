package therealpant.thaumicattempts.world.item;

import net.minecraft.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import therealpant.thaumicattempts.data.research.TAAspects;

public final class ParadoxumContainer {
    private static final String EXACT_PARADOXUM_TAG = "TAExactParadoxum";
    private static final int ASPECT_LIST_LIMIT = 500;

    private ParadoxumContainer() {}

    public static boolean isContainer(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof IParadoxumContainer
                && stack.getItem() instanceof IEssentiaContainerItem;
    }

    public static int getCapacity(ItemStack stack) {
        return isContainer(stack)
                ? Math.max(0, ((IParadoxumContainer) stack.getItem())
                .getParadoxumCapacity(stack)) : 0;
    }

    public static int getStored(ItemStack stack) {
        if (!isContainer(stack) || TAAspects.PARADOXUM == null) return 0;
        if (usesExtendedStorage(stack)) {
            if (stack.hasTagCompound()
                    && stack.getTagCompound().hasKey(EXACT_PARADOXUM_TAG, 3)) {
                return Math.max(0, Math.min(getCapacity(stack),
                        stack.getTagCompound().getInteger(EXACT_PARADOXUM_TAG)));
            }
            // Migration path for cores charged before extended storage existed.
            AspectList legacy = new AspectList();
            if (stack.hasTagCompound()) legacy.readFromNBT(stack.getTagCompound());
            return Math.max(0, legacy.getAmount(TAAspects.PARADOXUM));
        }
        AspectList aspects = ((IEssentiaContainerItem) stack.getItem()).getAspects(stack);
        return aspects == null ? 0 : Math.max(0, aspects.getAmount(TAAspects.PARADOXUM));
    }

    public static int add(ItemStack stack, int amount) {
        if (amount <= 0 || !isContainer(stack) || TAAspects.PARADOXUM == null) return 0;
        int accepted = Math.min(amount, getCapacity(stack) - getStored(stack));
        if (accepted <= 0) return 0;
        int target = getStored(stack) + accepted;
        writeStored(stack, target);
        return accepted;
    }

    public static int take(ItemStack stack, int amount) {
        if (amount <= 0 || !isContainer(stack) || TAAspects.PARADOXUM == null) return 0;
        int current = getStored(stack);
        int removed = Math.min(amount, current);
        if (removed <= 0) return 0;
        writeStored(stack, current - removed);
        return removed;
    }

    public static void normalize(ItemStack stack) {
        if (isContainer(stack) && usesExtendedStorage(stack)) {
            writeStored(stack, getStored(stack));
        }
    }

    private static void writeStored(ItemStack stack, int amount) {
        int target = Math.max(0, Math.min(getCapacity(stack), amount));
        boolean extended = usesExtendedStorage(stack);
        IEssentiaContainerItem container = (IEssentiaContainerItem) stack.getItem();
        AspectList current = container.getAspects(stack);
        AspectList visible = new AspectList();
        if (current != null) {
            for (thaumcraft.api.aspects.Aspect aspect : current.getAspects()) {
                if (aspect != null && aspect != TAAspects.PARADOXUM) {
                    visible.add(aspect, current.getAmount(aspect));
                }
            }
        }
        if (!extended && target > 0) {
            visible.add(TAAspects.PARADOXUM, Math.min(target, ASPECT_LIST_LIMIT));
        }
        container.setAspects(stack, visible);
        if (extended) {
            if (!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
            stack.getTagCompound().setInteger(EXACT_PARADOXUM_TAG, target);
        }
    }

    public static boolean isFull(ItemStack stack) {
        int capacity = getCapacity(stack);
        return capacity > 0 && getStored(stack) >= capacity;
    }

    private static boolean usesExtendedStorage(ItemStack stack) {
        return getCapacity(stack) > ASPECT_LIST_LIMIT;
    }
}
