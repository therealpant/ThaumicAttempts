package therealpant.thaumicattempts.world.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.data.research.TAAspects;

import javax.annotation.Nullable;

/**
 * An amethyst whose contained essentia is stored in Thaumcraft's standard
 * "Aspects" NBT list. This makes the charge belong to the individual stack.
 */
public class ItemRiftAmethyst extends Item
        implements IEssentiaContainerItem, IParadoxumContainer {
    public static final int MAX_PARADOXUM = 250;
    public ItemRiftAmethyst() {
        addPropertyOverride(
                new ResourceLocation(ThaumicAttempts.MODID, "paradoxum_level"),
                (stack, world, entity) -> getTextureStage(stack));
    }

    private float getTextureStage(ItemStack stack) {
        if (TAAspects.PARADOXUM == null) return 0.0F;
        AspectList aspects = getAspects(stack);
        int amount = aspects == null
                ? 0 : aspects.getAmount(TAAspects.PARADOXUM);
        if (amount >= 250) return 5.0F;
        if (amount >= 200) return 4.0F;
        if (amount >= 150) return 3.0F;
        if (amount >= 100) return 2.0F;
        if (amount >= 50) return 1.0F;
        return 0.0F;
    }

    public static AspectList createBaseAspects() {
        return new AspectList()
                .add(Aspect.ENERGY, 75)
                .add(Aspect.ELDRITCH, 75)
                .add(Aspect.CRYSTAL, 75)
                .add(Aspect.AURA, 75);
    }

    @Override
    public int getParadoxumCapacity(ItemStack stack) {
        return MAX_PARADOXUM;
    }

    @Nullable
    @Override
    public AspectList getAspects(ItemStack stack) {
        if (stack.isEmpty()) return null;

        AspectList result = createBaseAspects();
        if (stack.hasTagCompound()) {
            AspectList stored = new AspectList();
            stored.readFromNBT(stack.getTagCompound());
            for (Aspect aspect : stored.getAspects()) {
                if (aspect != null) {
                    // Keep the intrinsic amount and only raise it when the
                    // individual stack explicitly contains a larger value.
                    result.merge(aspect, stored.getAmount(aspect));
                }
            }
        }
        return result;
    }

    @Override
    public void setAspects(ItemStack stack, AspectList aspects) {
        if (stack.isEmpty()) return;
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        (aspects == null ? new AspectList() : aspects)
                .writeToNBT(stack.getTagCompound());
    }

    @Override
    public boolean ignoreContainedAspects() {
        return false;
    }
}
