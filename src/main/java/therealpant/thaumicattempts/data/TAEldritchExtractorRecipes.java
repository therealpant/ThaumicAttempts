package therealpant.thaumicattempts.data;

import therealpant.thaumicattempts.api.EldritchExtractorApi;
import therealpant.thaumicattempts.api.EldritchExtractorRecipe;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.items.ItemTAGem;
import net.minecraft.item.ItemStack;

public final class TAEldritchExtractorRecipes {
    private static final int DEFAULT_MIN_DAMAGE = 1;
    private static final int DEFAULT_MAX_DAMAGE = 3;
    private static final int DEFAULT_STAGES = 10;
    private static final int DEFAULT_IMPETUS_COST = 10;

    private TAEldritchExtractorRecipes() {}

    public static void register() {
        EldritchExtractorApi.clear();

        registerGemRecipes(AmberGemDefinition.ID, new ItemStack(ModBlocksItems.RIFT_FLOWER));
        registerGemRecipes(AmethystGemDefinition.ID, new ItemStack(ModBlocksItems.RIFT_STONE));
        registerGemRecipes(DiamondGemDefinition.ID, new ItemStack(ModBlocksItems.RIFT_CRISTAL));
    }

    private static void registerGemRecipes(net.minecraft.util.ResourceLocation gemId, ItemStack result) {
        for (int tier = 1; tier <= 3; tier++) {
            ItemStack crown = ItemTAGem.makeGem(gemId, tier, 0);
            EldritchExtractorApi.register(new EldritchExtractorRecipe(
                    crown,
                    DEFAULT_MIN_DAMAGE,
                    DEFAULT_MAX_DAMAGE,
                    DEFAULT_STAGES,
                    DEFAULT_IMPETUS_COST,
                    result
            ));
        }
    }
}
