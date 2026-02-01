package therealpant.thaumicattempts.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import therealpant.thaumicattempts.ThaumicAttempts;

public final class EldritchExtractorApi {
    private static final List<EldritchExtractorRecipe> RECIPES = new ArrayList<>();

    private EldritchExtractorApi() {}

    public static void register(EldritchExtractorRecipe recipe) {
        if (recipe == null) {
            ThaumicAttempts.LOGGER.error("[TA] Eldritch extractor recipe rejected: null recipe");
            return;
        }
        RECIPES.add(recipe);
    }

    public static void clear() {
        RECIPES.clear();
    }

    public static List<EldritchExtractorRecipe> all() {
        return Collections.unmodifiableList(RECIPES);
    }

    public static EldritchExtractorRecipe findRecipe(ItemStack crown) {
        if (crown == null || crown.isEmpty()) return null;
        for (EldritchExtractorRecipe recipe : RECIPES) {
            if (recipe.matchesCrown(crown)) {
                return recipe;
            }
        }
        return null;
    }
}
