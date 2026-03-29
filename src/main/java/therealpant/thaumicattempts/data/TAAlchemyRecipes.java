// src/main/java/therealpant/thaumicattempts/data/TAAlchemyRecipes.java
package therealpant.thaumicattempts.data;

import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import static thaumcraft.api.blocks.BlocksTC.stoneEldritchTile;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.config.ConfigRecipes;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;

import static therealpant.thaumicattempts.ThaumicAttempts.MODID;
import static therealpant.thaumicattempts.config.TAConfig.ENABLE_ELDRITCH_STONE_RECIPE;

public final class TAAlchemyRecipes {
    private TAAlchemyRecipes() {
    }

    public static void register() {
        // Катализатор: Void Seed
        ItemStack catalyst = new ItemStack(ItemsTC.voidSeed);
        if (catalyst.isEmpty()) return;

        // Результат: 4x stoneEldritchTile
        ItemStack result = new ItemStack(stoneEldritchTile, 2);

        // Аспекты: 20 EARTH + 20 ELDRITCH
        AspectList aspects = new AspectList()
                .add(Aspect.EARTH, 20)
                .add(Aspect.ELDRITCH, 20);

        // ВАЖНО: addCrucibleRecipe принимает (id, CrucibleRecipe)
        // researchKey ставим "BASEELDRITCH" — под это исследование и будем вешать addendum.

        CrucibleRecipe rec = new CrucibleRecipe("BASEELDRITCH", result, catalyst, aspects);

        //Only enable the recipes when config is enabled.
        if (ENABLE_ELDRITCH_STONE_RECIPE)
            ThaumcraftApi.addCrucibleRecipe(
                    new ResourceLocation("thaumicattempts", "eldritch_tile_from_voidseed"),
                    rec
            );


        ItemStack fru = new ItemStack(ModBlocksItems.MIND_FRUIT);
        if (fru.isEmpty()) return;

        ItemStack mun = new ItemStack(ItemsTC.salisMundus, 2);
        AspectList frumun = new AspectList()
        .add(Aspect.ENTROPY,5)
                .add(Aspect.CRYSTAL,5);

        CrucibleRecipe munfru = new CrucibleRecipe("TA_BOTANY", mun, fru, frumun);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","mundus_from_fruit"),
                munfru
                );

        ItemStack bfru = new ItemStack(ModBlocksItems.MATURE_MIND_FRUIT);
        if (bfru.isEmpty()) return;

        ItemStack po = new ItemStack(ModBlocksItems.MIND_POTION);
        AspectList bfrupo = new AspectList()
                .add(Aspect.ALCHEMY,15)
                .add(Aspect.LIFE,20)
                .add(Aspect.MIND,35);

        CrucibleRecipe pobfru = new CrucibleRecipe("TA_BOTANY", po, bfru, bfrupo);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","fruit_from_potion"),
                pobfru
        );

        ItemStack cr = new ItemStack(ModBlocksItems.RIFT_CRISTAL);
        if (cr.isEmpty()) return;

        ItemStack cur = new ItemStack(ItemsTC.curio,1,0);
        AspectList curcr = new AspectList()
                .add(Aspect.DARKNESS,15)
                .add(Aspect.MIND,25);

        CrucibleRecipe crcur = new CrucibleRecipe("TA_ANOMALY", cur, cr, curcr);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","cristal_from_curio"),
                crcur
        );

        ItemStack fla = new ItemStack(ModBlocksItems.RIFT_FLOWER);
        if (fla.isEmpty()) return;

        ItemStack curi = new ItemStack(ItemsTC.curio,1,5);
        AspectList curifla = new AspectList()
                .add(Aspect.FLUX,15)
                .add(Aspect.MIND,25);

        CrucibleRecipe flaccuri = new CrucibleRecipe("TA_ANOMALY", curi, fla, curifla);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","flover_from_curio"),
                flaccuri
        );

        ItemStack sto = new ItemStack(ModBlocksItems.RIFT_STONE);
        if (fla.isEmpty()) return;

        ItemStack ore0 = new ItemStack(ItemsTC.clusters,1,0);
        ItemStack ore1 = new ItemStack(ItemsTC.clusters,1,1);
        ItemStack ore2 = new ItemStack(ItemsTC.clusters,1,2);
        ItemStack ore3 = new ItemStack(ItemsTC.clusters,1,3);
        ItemStack ore4 = new ItemStack(ItemsTC.clusters,1,4);
        ItemStack ore5 = new ItemStack(ItemsTC.clusters,1,5);
        ItemStack ore6 = new ItemStack(ItemsTC.clusters,1,6);
        ItemStack ore7 = new ItemStack(ItemsTC.clusters,1,7);
        ItemStack ore8 = new ItemStack(ItemsTC.clusters,1,8);
        ItemStack ore9 = new ItemStack(ItemsTC.clusters,1,8);
        ItemStack ore10 = new ItemStack(ItemsTC.nuggets,1,10);

        AspectList stoore0 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.METAL,20)
                .add(Aspect.EARTH,10);
        AspectList stoore1 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.METAL,20)
                .add(Aspect.EARTH,10)
                .add(Aspect.DESIRE,15);
        AspectList stoore2 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.METAL,20)
                .add(Aspect.EARTH,10)
                .add(Aspect.EXCHANGE,15);
        AspectList stoore3 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.METAL,20)
                .add(Aspect.EARTH,10)
                .add(Aspect.CRYSTAL,15);
        AspectList stoore4 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.METAL,20)
                .add(Aspect.EARTH,10)
                .add(Aspect.DESIRE,15)
                .add(Aspect.CRYSTAL,15);
        AspectList stoore5 = new AspectList()
                .add(Aspect.ORDER,20)
                .add(Aspect.METAL,20)
                .add(Aspect.EARTH,10);
        AspectList stoore6 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.METAL,20)
                .add(Aspect.EARTH,10)
                .add(Aspect.ALCHEMY,10)
                .add(Aspect.DEATH,10);
        AspectList stoore7 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.CRYSTAL,20);
        AspectList stoore10 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.METAL,10)
                .add(Aspect.EARTH,10);

        CrucibleRecipe oresto0 = new CrucibleRecipe("TA_ANOMALY", ore0, sto, stoore0);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore0"),
                oresto0
        );
        CrucibleRecipe oresto1 = new CrucibleRecipe("TA_ANOMALY", ore1, sto, stoore1);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore1"),
                oresto1
        );
        CrucibleRecipe oresto2 = new CrucibleRecipe("TA_ANOMALY", ore2, sto, stoore2);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore2"),
                oresto2
        );
        CrucibleRecipe oresto3 = new CrucibleRecipe("TA_ANOMALY", ore3, sto, stoore3);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore3"),
                oresto3
        );
        CrucibleRecipe oresto4 = new CrucibleRecipe("TA_ANOMALY", ore4, sto, stoore4);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore4"),
                oresto4
        );
        CrucibleRecipe oresto5 = new CrucibleRecipe("TA_ANOMALY", ore5, sto, stoore5);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore5"),
                oresto5
        );
        CrucibleRecipe oresto6 = new CrucibleRecipe("TA_ANOMALY", ore6, sto, stoore6);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore6"),
                oresto6
        );
        CrucibleRecipe oresto7 = new CrucibleRecipe("TA_ANOMALY", ore7, sto, stoore7);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore7"),
                oresto7
        );
        CrucibleRecipe oresto10 = new CrucibleRecipe("TA_ANOMALY", ore10, sto, stoore10);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","stone_from_ore10"),
                oresto10
        );

        ConfigRecipes.recipeGroups.put(
                MODID + ":riftstone_alchemy",
                Lists.newArrayList(
                        new ResourceLocation(MODID, "stone_from_ore0"),
                        new ResourceLocation(MODID, "stone_from_ore1"),
                        new ResourceLocation(MODID, "stone_from_ore2"),
                        new ResourceLocation(MODID, "stone_from_ore3"),
                        new ResourceLocation(MODID, "stone_from_ore4"),
                        new ResourceLocation(MODID, "stone_from_ore5"),
                        new ResourceLocation(MODID, "stone_from_ore6"),
                        new ResourceLocation(MODID, "stone_from_ore7"),
                        new ResourceLocation(MODID, "stone_from_ore10")
                )
        );

        ItemStack tain = new ItemStack(ModBlocksItems.TAINTED_MIND_FRUIT);
        if (tain.isEmpty()) return;

        ItemStack seed = new ItemStack(ItemsTC.voidSeed, 2);
        ItemStack cur3 = new ItemStack(ItemsTC.curio,1,3);

        AspectList taincur3 = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.ELDRITCH,10)
                .add(Aspect.EXCHANGE,5);
        AspectList tainseed = new AspectList()
                .add(Aspect.ORDER,10)
                .add(Aspect.ELDRITCH,10)
                .add(Aspect.EXCHANGE,5);

        CrucibleRecipe seedcur3 = new CrucibleRecipe("TA_BOTANY", cur3, tain, taincur3);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","frut_from_cur3"),
                seedcur3
        );
        CrucibleRecipe seedtain = new CrucibleRecipe("TA_BOTANY", seed, tain, tainseed);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","frut_from_stone"),
                seedtain
        );

        ConfigRecipes.recipeGroups.put(
                MODID + ":flux_frut_alchemy",
                Lists.newArrayList(
                        new ResourceLocation(MODID, "frut_from_cur3"),
                        new ResourceLocation(MODID, "frut_from_stone")
                )
        );

        ItemStack sand = new ItemStack(Blocks.SOUL_SAND);
        if (tain.isEmpty()) return;

        ItemStack soil = new ItemStack(TABlocks.ANOMALY_BED_ITEM, 1);

        AspectList sandsoil = new AspectList()
                .add(Aspect.ALCHEMY,25)
                .add(Aspect.ELDRITCH,15)
                .add(Aspect.LIFE,20);

        CrucibleRecipe soilsand = new CrucibleRecipe("TA_BOTANY@2", soil, sand, sandsoil);
        ThaumcraftApi.addCrucibleRecipe(
                new ResourceLocation("thaumicattempts","sand_from_soil"),
                soilsand
        );
    }
}
