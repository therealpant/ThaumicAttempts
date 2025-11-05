// src/main/java/therealpant/thaumicattempts/data/research/TAResearchGolemMirrors.java
package therealpant.thaumicattempts.data.research;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import therealpant.thaumicattempts.init.TABlocks;

import java.util.ArrayList;
import java.util.List;

import static thaumcraft.api.blocks.BlocksTC.stoneEldritchTile;

public final class TAResearchGolemMirrors {
    private TAResearchGolemMirrors() {}

    public static void inject() {
        final String KEY = "TA_GOLEM_MIRRORS";
        final String CAT = "GOLEMANCY";

        if (ResearchCategories.getResearch(KEY) != null) return; // уже добавлено

        // Категории (нельзя пихать null в Knowledge — это ловушка для NPE)
        ResearchCategory golemCat  = ResearchCategories.getResearchCategory("GOLEMANCY");
        ResearchCategory artifice  = ResearchCategories.getResearchCategory("ARTIFICE");
        ResearchCategory eldritch  = ResearchCategories.getResearchCategory("ELDRITCH");

        // Позиция рядом с GOLEMLOGISTICS (fallback 5,2)
        int col = 5, row = 2;
        ResearchEntry parent = ResearchCategories.getResearch("GOLEMLOGISTICS");
        if (parent != null) {
            col = Math.max(parent.getDisplayColumn() + 2, 5);
            row = Math.max(parent.getDisplayRow() - 1, 2);
        }

        // Иконка — Order Terminal
        ItemStack iconStack = new ItemStack(Item.getItemFromBlock(TABlocks.ORDER_TERMINAL));

        ResearchEntry re = new ResearchEntry();
        re.setKey(KEY);
        re.setCategory(CAT);
        re.setName("research." + KEY + ".title");
        re.setDisplayColumn(col);
        re.setDisplayRow(row);
        re.setParents(new String[] { "GOLEMLOGISTICS" });
        re.setSiblings(new String[0]);
        re.setIcons(new Object[] { iconStack });
        setParentsHiddenCompat(re, new String[0]); // защитимся от NPE в старых деобфах

        /* === Stage 0: интро + требования === */
        ResearchStage s0 = new ResearchStage();
        s0.setText("research.TA_GOLEM_MIRRORS.text.0");

        // Требования по теориям — добавляем только существующие категории
        List<ResearchStage.Knowledge> knowList = new ArrayList<>();
        if (golemCat  != null) knowList.add(new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, golemCat, 1));
        if (artifice  != null) knowList.add(new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, artifice, 2));
        if (eldritch  != null) knowList.add(new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, eldritch, 1));
        if (!knowList.isEmpty()) s0.setKnow(knowList.toArray(new ResearchStage.Knowledge[0]));

        // Требуемые предметы (required_item / obtain). Фильтруем mirror на случай отсутствия.
        List<Object> obtain = new ArrayList<>();
        obtain.add(new ItemStack(ItemsTC.mind, 1, 1));                         // Clockwork Mind (мета 1)
        obtain.add(new ItemStack(Item.getItemFromBlock(stoneEldritchTile)));   // Eldritch tile
        Block mirrorBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("thaumcraft", "mirror"));
        if (mirrorBlock != null) {
            obtain.add(new ItemStack(mirrorBlock));
        }
        if (!obtain.isEmpty()) s0.setObtain(obtain.toArray(new Object[0]));

        /* === Stage 1: основной текст + рецепты === */
        ResearchStage s1 = new ResearchStage();
        s1.setText("research.TA_GOLEM_MIRRORS.text.1");
        s1.setRecipes(new ResourceLocation[] {
                new ResourceLocation("thaumicattempts", "order_terminal"),
                new ResourceLocation("thaumicattempts", "mirror_manager_infusion")
        });

        // ВАЖНО: регистрируем ОБЕ стадии, иначе текст никогда не переключится на text.1
        re.setStages(new ResearchStage[] { s0, s1 });

        // Регистрация в категории
        ResearchCategory cat = ResearchCategories.getResearchCategory(CAT);
        if (cat != null) {
            cat.research.put(KEY, re);
            cat.minDisplayColumn = Math.min(cat.minDisplayColumn, col);
            cat.maxDisplayColumn = Math.max(cat.maxDisplayColumn, col);
            cat.minDisplayRow    = Math.min(cat.minDisplayRow, row);
            cat.maxDisplayRow    = Math.max(cat.maxDisplayRow, row);
        }

        System.out.println("[TA] Injected research " + KEY + " at [" + col + "," + row + "], icon=" + iconStack);
    }

    // Совместимая установка parentsHidden (метода может не быть в твоём деобфе)
    private static void setParentsHiddenCompat(ResearchEntry re, String[] parentsHidden) {
        try {
            ResearchEntry.class.getMethod("setParentsHidden", String[].class)
                    .invoke(re, (Object) parentsHidden);
        } catch (Throwable ignored) {
            try {
                java.lang.reflect.Field f = ResearchEntry.class.getDeclaredField("parentsHidden");
                f.setAccessible(true);
                f.set(re, parentsHidden != null ? parentsHidden : new String[0]);
            } catch (Throwable t2) {
                System.out.println("[TA] Unable to set parentsHidden: " + t2);
            }
        }
    }
}
