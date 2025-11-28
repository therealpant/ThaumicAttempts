// src/main/java/therealpant/thaumicattempts/data/TAAlchemyRecipes.java
package therealpant.thaumicattempts.data;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.items.ItemsTC;

import static thaumcraft.api.blocks.BlocksTC.stoneEldritchTile;

public final class TAAlchemyRecipes {
    private TAAlchemyRecipes() {}

    public static void register() {
        // Катализатор: Void Seed
        ItemStack catalyst = new ItemStack(ItemsTC.voidSeed);
        if (catalyst.isEmpty()) return;

        // Результат: 4x stoneEldritchTile
        ItemStack result = new ItemStack(stoneEldritchTile, 4);

        // Аспекты: 20 EARTH + 20 ELDRITCH
        AspectList aspects = new AspectList()
                .add(Aspect.EARTH, 20)
                .add(Aspect.ELDRITCH, 20);

        // ВАЖНО: addCrucibleRecipe принимает (id, CrucibleRecipe)
        // researchKey ставим "BASEELDRITCH" — под это исследование и будем вешать addendum.

        CrucibleRecipe rec = new CrucibleRecipe("BASEELDRITCH", result, catalyst, aspects);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","eldritch_tile_from_voidseed"),
                rec
        );


    }


}
