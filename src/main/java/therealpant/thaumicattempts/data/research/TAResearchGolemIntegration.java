// src/main/java/therealpant/thaumicattempts/data/research/TAResearchGolemIntegration.java
package therealpant.thaumicattempts.data.research;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import therealpant.thaumicattempts.ThaumicAttempts;

import static therealpant.thaumicattempts.data.research.TAResearchGolemcraft.buildObtainList;

public final class TAResearchGolemIntegration {
    public static final String KEY = "TA_GOLEM_INTEGRATION";

    private TAResearchGolemIntegration() {}

    public static void inject() {
        if (ResearchCategories.getResearch(KEY) != null) return;

        // Проверяем категории и наличие родителя
        ResearchCategory golemCat  = ResearchCategories.getResearchCategory("GOLEMANCY");
        ResearchCategory artifice  = ResearchCategories.getResearchCategory("ARTIFICE");
        ResearchCategory aura  = ResearchCategories.getResearchCategory("AUROMANCY");
        ResearchCategory eldritch  = ResearchCategories.getResearchCategory("ELDRITCH");

        // базовая позиция: правее двух узлов
        int col = 7, row = 4;
            ResearchEntry a = ResearchCategories.getResearch("TA_GOLEMCRAFT");
            ResearchEntry b = ResearchCategories.getResearch("TA_GOLEM_MIRRORS");


        ResearchEntry re = new ResearchEntry();
        re.setKey(KEY);
        re.setCategory("GOLEMANCY");
        re.setName("research." + KEY + ".title");
        re.setDisplayColumn(col);
        re.setDisplayRow(row);
        re.setParents(new String[] { "TA_GOLEMCRAFT", "TA_GOLEM_MIRRORS" });
        re.setSiblings(new String[0]);
        re.setIcons(new Object[] {
                new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":mirror_stabilizer")),
        });


        ResearchStage s0 = new ResearchStage();
        s0.setText("research." + KEY + ".text.0");
        s0.setKnow(new ResearchStage.Knowledge[]{
                new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, golemCat, 1),
                new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, artifice, 1),
                new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, eldritch, 1),
        });

        s0.setObtain(buildObtainList(
                new ItemStack(Item.getByNameOrId("thaumcraft:brain_box")),
                new ItemStack(ItemsTC.plate,1,3),
                new ItemStack(ItemsTC.mirroredGlass)

        ));

        ResearchStage s = new ResearchStage();
        s.setText("research." + KEY + ".text.1");
        // страницы с рецептами
        s.setRecipes(new ResourceLocation[] {
                new ResourceLocation(ThaumicAttempts.MODID, "math_core_arcane"),
                new ResourceLocation(ThaumicAttempts.MODID, "mirror_stabilizer_arcane"),
                new ResourceLocation(ThaumicAttempts.MODID, "requester_infusion")
        });
        re.setStages(new ResearchStage[] { s0, s });

        ResearchCategory cat = ResearchCategories.getResearchCategory("GOLEMANCY");
        if (cat != null) {
            cat.research.put(KEY, re);
            cat.maxDisplayColumn = Math.max(cat.maxDisplayColumn, col);
            cat.maxDisplayRow    = Math.max(cat.maxDisplayRow, row);
        }
        System.out.println("[TA] Injected research " + KEY + " at [" + col + "," + row + "]");
    }
}
