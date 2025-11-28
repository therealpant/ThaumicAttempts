// src/main/java/therealpant/thaumicattempts/data/TAResearchAddenda.java
package therealpant.thaumicattempts.data.research;

import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;

import java.util.Arrays;

public final class TAResearchAddenda {
    private TAResearchAddenda() {}

    public static void injectArcaneEarAddendum() {
        final String ENTRY_KEY = "ARCANEEAR";
        final String TEXT_KEY  = "research.ARCANEEAR.ta_addendum_ear_band";
        final ResourceLocation RECIPE_ID = new ResourceLocation("thaumicattempts","arcane_ear_band");

        ResearchEntry ear = ResearchCategories.getResearch(ENTRY_KEY);
        if (ear == null) return;

        ResearchAddendum[] addenda = ear.getAddenda();
        if (addenda != null) {
            for (ResearchAddendum a : addenda) {
                if (a != null && TEXT_KEY.equals(a.getText())) return; // уже добавили
            }
        }

        ThaumcraftApi.registerResearchLocation(
                new ResourceLocation("thaumicattempts","research/arcane_ear_add.json")
        );


        ResearchAddendum add = new ResearchAddendum();
        add.setText(TEXT_KEY);
        add.setRecipes(new ResourceLocation[] { RECIPE_ID });           // страница рецепта
        add.setResearch(new String[] { "ARCANEEAR", "MINDCLOCKWORK" }); // требуются оба

        if (addenda == null || addenda.length == 0) {
            ear.setAddenda(new ResearchAddendum[] { add });
        } else {
            ResearchAddendum[] merged = Arrays.copyOf(addenda, addenda.length + 1);
            merged[merged.length - 1] = add;
            ear.setAddenda(merged);
        }
    }

    public static void injectEldritchVoidTileAddendum() {
        final String ENTRY_KEY = "BASEELDRITCH";
        final ResourceLocation RECIPE_ID =
                new ResourceLocation("thaumicattempts","eldritch_tile_from_voidseed");
        final String TEXT_KEY  =
                "research.BASEELDRITCH.ta_addendum_voidtile_from_seed";

        ResearchEntry entry = ResearchCategories.getResearch(ENTRY_KEY);
        if (entry == null) return;

        ResearchAddendum[] addenda = entry.getAddenda();
        // Если уже есть addendum с этим рецептом — выходим
        if (addenda != null) {
            for (ResearchAddendum a : addenda) {
                if (a == null) continue;
                ResourceLocation[] recs = a.getRecipes();
                if (recs != null) {
                    for (ResourceLocation r : recs) {
                        if (RECIPE_ID.equals(r)) return;
                    }
                }
            }
        }

        ResearchAddendum add = new ResearchAddendum();
        add.setText(TEXT_KEY);
        add.setRecipes(new ResourceLocation[]{ RECIPE_ID });
        // Требование видимости: само базовое исследование
        add.setResearch(new String[]{ ENTRY_KEY });

        if (addenda == null || addenda.length == 0) {
            entry.setAddenda(new ResearchAddendum[]{ add });
        } else {
            ResearchAddendum[] merged = Arrays.copyOf(addenda, addenda.length + 1);
            merged[merged.length - 1] = add;
            entry.setAddenda(merged);
        }
    }
}
