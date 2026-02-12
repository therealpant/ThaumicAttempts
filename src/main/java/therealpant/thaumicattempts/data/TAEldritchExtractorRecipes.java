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
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.EldritchExtractorApi;
import therealpant.thaumicattempts.api.EldritchExtractorRecipe;;
import therealpant.thaumicattempts.items.ItemTAGem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class TAEldritchExtractorRecipes {
    private static final String RECIPES_RESOURCE_PATH = "/assets/thaumicattempts/recipes/rift_extractor_recipes.json";

    private TAEldritchExtractorRecipes() {}

    public static void register() {
        EldritchExtractorApi.clear();

        loadFromJson();
    }

    private static void loadFromJson() {
        try (InputStream in = TAEldritchExtractorRecipes.class.getResourceAsStream(RECIPES_RESOURCE_PATH)) {
            if (in == null) {
                ThaumicAttempts.LOGGER.error("[TA] Eldritch extractor recipes file not found: {}", RECIPES_RESOURCE_PATH);
                return;
            }

            JsonObject root = new JsonParser()
                    .parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray recipes = JsonUtils.getJsonArray(root, "assets/thaumicattempts/recipes");

            for (JsonElement element : recipes) {
                JsonObject entry = element.getAsJsonObject();

                JsonObject crownSlot = JsonUtils.getJsonObject(entry, "crown_slot");
                String gemIdRaw = JsonUtils.getString(crownSlot, "gem_id");
                int crownTier = JsonUtils.getInt(crownSlot, "tier");

                ResourceLocation gemId = new ResourceLocation(gemIdRaw);
                ItemStack crown = ItemTAGem.makeGem(gemId, crownTier, 0);
                if (crown.isEmpty()) {
                    ThaumicAttempts.LOGGER.warn("[TA] Skipping eldritch recipe, invalid crown gem {} tier {}", gemIdRaw, crownTier);
                    continue;
                }

                int minCrownDamage = entry.has("min_crown_damage")
                        ? JsonUtils.getInt(entry, "min_crown_damage")
                        : JsonUtils.getInt(entry, "crown_damage");
                int maxCrownDamage = entry.has("max_crown_damage")
                        ? JsonUtils.getInt(entry, "max_crown_damage")
                        : JsonUtils.getInt(entry, "crown_damage");
                int impetusCostPerStage = JsonUtils.getInt(entry, "impetus_cost_per_stage");
                int stages = JsonUtils.getInt(entry, "stages");
                String outputItemIdRaw = JsonUtils.getString(entry, "output_item");
                int outputCount = JsonUtils.getInt(entry, "output_count", 1);

                Item outputItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(outputItemIdRaw));
                if (outputItem == null) {
                    ThaumicAttempts.LOGGER.warn("[TA] Skipping eldritch recipe, output item not found: {}", outputItemIdRaw);
                    continue;
                }

                EldritchExtractorApi.register(new EldritchExtractorRecipe(
                        crown,
                        minCrownDamage,
                        maxCrownDamage,
                        stages,
                        impetusCostPerStage,
                        new ItemStack(outputItem, outputCount)
                ));
            }

            ThaumicAttempts.LOGGER.info("[TA] Loaded {} eldritch extractor recipes from {}", EldritchExtractorApi.all().size(), RECIPES_RESOURCE_PATH);
        } catch (Exception e) {
            ThaumicAttempts.LOGGER.error("[TA] Failed to load eldritch extractor recipes from {}", RECIPES_RESOURCE_PATH, e);
        }
    }
}
