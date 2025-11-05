// src/main/java/therealpant/thaumicattempts/data/research/TAResearchGolemcraft.java
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
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;

import java.util.ArrayList;
import java.util.List;

public final class TAResearchGolemcraft {
    private TAResearchGolemcraft() {}

    public static void inject() {
        final String KEY = "TA_GOLEMCRAFT";
        final String CAT = "GOLEMANCY";
        final int COL = 5, ROW = 6;
        final String PARENT = "GOLEMLOGISTICS";

        // Не дублируем
        if (ResearchCategories.getResearch(KEY) != null) return;

        // Проверяем категории и наличие родителя
        ResearchCategory golemCat  = ResearchCategories.getResearchCategory("GOLEMANCY");
        ResearchCategory artifice  = ResearchCategories.getResearchCategory("ARTIFICE");
        ResearchCategory targetCat = ResearchCategories.getResearchCategory(CAT);
        if (golemCat == null || artifice == null || targetCat == null) {
            System.out.println("[TA] TA_GOLEMCRAFT: categories not ready, skipping");
            return;
        }
        if (ResearchCategories.getResearch(PARENT) == null) {
            System.out.println("[TA] TA_GOLEMCRAFT: parent " + PARENT + " not found, skipping");
            return;
        }

        // Иконка: крафтер или колокольчик
        ItemStack icon;
        Item crafterItem = Item.getItemFromBlock(ModBlocksItems.GOLEM_CRAFTER);
        icon = (crafterItem != null) ? new ItemStack(crafterItem) : new ItemStack(ItemsTC.golemBell);

        ResearchEntry re = new ResearchEntry();
        re.setKey(KEY);
        re.setCategory(CAT);
        re.setName("research.TA_GOLEMCRAFT.title"); // локализация
        re.setDisplayColumn(COL);
        re.setDisplayRow(ROW);
        re.setParents(new String[]{ PARENT });
        re.setIcons(new Object[]{ icon });

        /* ===== Stage 1: ВВОДНЫЙ ТЕКСТ + требования ===== */
        ResearchStage sIntro = new ResearchStage();
        sIntro.setText("research.TA_GOLEMCRAFT.text.intro"); // показывается ДО открытия
        sIntro.setKnow(new ResearchStage.Knowledge[]{
                new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, golemCat, 2),
                new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, artifice, 2)
        });
        // «Сдача» предметов (они изымаются)
        sIntro.setObtain(buildObtainList(
                new ItemStack(Item.getItemFromBlock(Blocks.CRAFTING_TABLE)),
                new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:arcane_workbench")))
                // можно добавить "oredict:ingotIron" или "thaumcraft:thaumometer"
        ));

        /* ===== Stage 2: ОСНОВНОЙ ТЕКСТ ПОСЛЕ ЗАВЕРШЕНИЯ ===== */
        ResearchStage sMain = new ResearchStage();
        sMain.setText("research.TA_GOLEMCRAFT.text.main"); // показывается ПОСЛЕ завершения
        // Рецепты разблокируются при завершении исследования
        sMain.setRecipes(new ResourceLocation[]{
                new ResourceLocation(ThaumicAttempts.MODID, "pattern_craft"),
                new ResourceLocation(ThaumicAttempts.MODID, "golem_crafter_infusion"),
        });
        // ВАЖНО: у Stage 2 нет know/obtain/craft/research — TC автоматически «проскочит» её к complete,
        // но после завершения при открытии будет отображаться именно её текст.

        re.setStages(new ResearchStage[]{ sIntro, sMain });

        // Регистрация
        targetCat.research.put(KEY, re);
        targetCat.minDisplayColumn = Math.min(targetCat.minDisplayColumn, COL);
        targetCat.maxDisplayColumn = Math.max(targetCat.maxDisplayColumn, COL);
        targetCat.minDisplayRow    = Math.min(targetCat.minDisplayRow, ROW);
        targetCat.maxDisplayRow    = Math.max(targetCat.maxDisplayRow, ROW);

        System.out.println("[TA] TA_GOLEMCRAFT injected (intro+main stages)");
    }

    /** Хелпер для setObtain(...): ItemStack | Item | "oredict:NAME" | "modid:item" */
    static Object[] buildObtainList(Object... entries) {
        List<Object> out = new ArrayList<>();
        for (Object e : entries) {
            if (e == null) continue;

            if (e instanceof ItemStack) {
                ItemStack st = ((ItemStack) e).copy();
                if (!st.isEmpty()) {
                    st.setCount(1);
                    out.add(st);
                }
                continue;
            }
            if (e instanceof Item) {
                out.add(new ItemStack((Item) e));
                continue;
            }
            if (e instanceof String) {
                String s = (String) e;
                if (s.startsWith("oredict:")) {
                    String name = s.substring("oredict:".length());
                    if (!name.isEmpty()) out.add(name);
                } else {
                    Item it = Item.getByNameOrId(s);
                    if (it != null) out.add(new ItemStack(it));
                    else System.out.println("[TA] Missing obtain item id: " + s);
                }
            }
        }
        return out.toArray(new Object[0]);
    }
}
