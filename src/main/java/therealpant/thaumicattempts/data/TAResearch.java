// src/main/java/therealpant/thaumicattempts/data/TAResearch.java
package therealpant.thaumicattempts.data;

import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import therealpant.thaumicattempts.ThaumicAttempts;

public final class TAResearch {

    public static final String CAT = "THAUMICATTEMPTS"; // id категории

    public static void init() {
        // Регистрируем категорию (можешь поставить свои текстуры)
        ResearchCategory cat = ResearchCategories.registerCategory(
                CAT,
                "RESEARCH_"+CAT, // ключ «формулы», можно произвольно
                new AspectList().add(Aspect.ORDER,1).add(Aspect.MAGIC,1),
                new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/research/icon_ta.png"),
                new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/research/bg_ta.png")
        );

        // Говорим TC загрузить наши JSON-исследования
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(
                ThaumicAttempts.MODID, "research/thaumicattempts.json"
        ));
    }

    private TAResearch() {}
}
