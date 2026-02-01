package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import therealpant.thaumicattempts.items.ItemTAGem;

public class EldritchExtractorRecipe {
    private final ItemStack crown;
    private final int minDamage;
    private final int maxDamage;
    private final int stages;
    private final int impetusCost;
    private final ItemStack result;

    public EldritchExtractorRecipe(ItemStack crown, int minDamage, int maxDamage, int stages, int impetusCost, ItemStack result) {
        this.crown = crown == null ? ItemStack.EMPTY : crown.copy();
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
        this.stages = stages;
        this.impetusCost = impetusCost;
        this.result = result == null ? ItemStack.EMPTY : result.copy();
    }

    public ItemStack getCrown() {
        return crown.copy();
    }

    public int getMinDamage() {
        return minDamage;
    }

    public int getMaxDamage() {
        return maxDamage;
    }

    public int getStages() {
        return stages;
    }

    public int getImpetusCost() {
        return impetusCost;
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public boolean matchesCrown(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (crown.isEmpty()) return false;
        if (ItemTAGem.isGem(crown) && ItemTAGem.isGem(stack)) {
            net.minecraft.util.ResourceLocation crownId = ItemTAGem.getGemIdFromStack(crown);
            net.minecraft.util.ResourceLocation stackId = ItemTAGem.getGemIdFromStack(stack);
            if (crownId == null || stackId == null) {
                return false;
            }
            return crownId.equals(stackId)
                    && ItemTAGem.getTierFromStack(crown) == ItemTAGem.getTierFromStack(stack);
        }
        return ItemStack.areItemsEqual(crown, stack) && ItemStack.areItemStackTagsEqual(crown, stack);
    }
}
