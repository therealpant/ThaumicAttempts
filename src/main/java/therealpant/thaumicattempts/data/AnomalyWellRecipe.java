package therealpant.thaumicattempts.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnomalyWellRecipe {
    private final String id;
    private final List<Ingredient> ingredients;
    private final ItemStack result;
    private final int inputCount;

    AnomalyWellRecipe(String id, List<Ingredient> ingredients, ItemStack result) {
        this.id = id;
        this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
        this.result = result.copy();
        int total = 0;
        for (Ingredient ingredient : ingredients) {
            total += ingredient.count;
        }
        this.inputCount = total;
    }

    public String getId() {
        return id;
    }

    public int getInputCount() {
        return inputCount;
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public boolean matches(List<ItemStack> inputs) {
        int total = 0;
        for (ItemStack stack : inputs) {
            if (stack != null && !stack.isEmpty()) {
                total += stack.getCount();
            }
        }
        if (total != inputCount) {
            return false;
        }

        int[] remaining = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = inputs.get(i);
            remaining[i] = stack == null || stack.isEmpty() ? 0 : stack.getCount();
        }

        for (Ingredient ingredient : ingredients) {
            int needed = ingredient.count;
            for (int i = 0; i < inputs.size() && needed > 0; i++) {
                ItemStack stack = inputs.get(i);
                if (remaining[i] <= 0 || !ingredient.matches(stack)) {
                    continue;
                }
                int consumed = Math.min(needed, remaining[i]);
                remaining[i] -= consumed;
                needed -= consumed;
            }
            if (needed > 0) {
                return false;
            }
        }

        for (int count : remaining) {
            if (count != 0) {
                return false;
            }
        }
        return true;
    }

    static final class Ingredient {
        private final Item item;
        private final int metadata;
        private final int count;

        Ingredient(Item item, int metadata, int count) {
            this.item = item;
            this.metadata = metadata;
            this.count = count;
        }

        private boolean matches(ItemStack stack) {
            return stack != null
                    && !stack.isEmpty()
                    && stack.getItem() == item
                    && (metadata == OreDictionary.WILDCARD_VALUE
                    || stack.getMetadata() == metadata);
        }
    }
}
