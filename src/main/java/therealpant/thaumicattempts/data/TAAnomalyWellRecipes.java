package therealpant.thaumicattempts.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import therealpant.thaumicattempts.ThaumicAttempts;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TAAnomalyWellRecipes {
    private static final String RESOURCE_PATH =
            "/assets/thaumicattempts/anomaly_well/anomaly_well_recipes.json";
    private static final int WELL_SLOT_COUNT = 3;
    private static List<AnomalyWellRecipe> recipes = Collections.emptyList();

    private TAAnomalyWellRecipes() {
    }

    public static void register() {
        List<AnomalyWellRecipe> loaded = new ArrayList<>();
        try (InputStream in = TAAnomalyWellRecipes.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                ThaumicAttempts.LOGGER.error("[TA] Anomaly well recipes file not found: {}", RESOURCE_PATH);
                recipes = Collections.emptyList();
                return;
            }

            JsonObject root = new JsonParser()
                    .parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray entries = JsonUtils.getJsonArray(root, "recipes");
            for (JsonElement element : entries) {
                try {
                    AnomalyWellRecipe recipe = parseRecipe(element.getAsJsonObject());
                    if (recipe.getInputCount() != WELL_SLOT_COUNT) {
                        ThaumicAttempts.LOGGER.warn(
                                "[TA] Skipping anomaly well recipe '{}': expected exactly {} input items, got {}",
                                recipe.getId(), WELL_SLOT_COUNT, recipe.getInputCount());
                        continue;
                    }
                    loaded.add(recipe);
                } catch (Exception recipeError) {
                    ThaumicAttempts.LOGGER.error(
                            "[TA] Skipping invalid anomaly well recipe: {}", element, recipeError);
                }
            }
        } catch (Exception e) {
            ThaumicAttempts.LOGGER.error(
                    "[TA] Failed to load anomaly well recipes from {}", RESOURCE_PATH, e);
        }

        recipes = Collections.unmodifiableList(loaded);
        ThaumicAttempts.LOGGER.info(
                "[TA] Loaded {} anomaly well recipes from {}", recipes.size(), RESOURCE_PATH);
    }

    private static AnomalyWellRecipe parseRecipe(JsonObject object) {
        String id = JsonUtils.getString(object, "id");
        JsonArray ingredientsJson = JsonUtils.getJsonArray(object, "ingredients");
        List<AnomalyWellRecipe.Ingredient> ingredients = new ArrayList<>();
        for (JsonElement element : ingredientsJson) {
            JsonObject ingredientJson = element.getAsJsonObject();
            Item item = getItem(JsonUtils.getString(ingredientJson, "item"));
            int metadata = ingredientJson.has("meta")
                    ? JsonUtils.getInt(ingredientJson, "meta")
                    : OreDictionary.WILDCARD_VALUE;
            int count = JsonUtils.getInt(ingredientJson, "count", 1);
            if (count <= 0) {
                throw new IllegalArgumentException("Ingredient count must be positive");
            }
            ingredients.add(new AnomalyWellRecipe.Ingredient(item, metadata, count));
        }

        JsonObject resultJson = JsonUtils.getJsonObject(object, "result");
        Item resultItem = getItem(JsonUtils.getString(resultJson, "item"));
        int resultCount = JsonUtils.getInt(resultJson, "count", 1);
        int resultMeta = JsonUtils.getInt(resultJson, "meta", 0);
        if (resultCount <= 0) {
            throw new IllegalArgumentException("Result count must be positive");
        }
        return new AnomalyWellRecipe(
                id, ingredients, new ItemStack(resultItem, resultCount, resultMeta));
    }

    private static Item getItem(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + id);
        }
        return item;
    }

    @Nullable
    public static AnomalyWellRecipe findMatch(List<ItemStack> inputs) {
        for (AnomalyWellRecipe recipe : recipes) {
            if (recipe.matches(inputs)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<AnomalyWellRecipe> all() {
        return recipes;
    }
}
