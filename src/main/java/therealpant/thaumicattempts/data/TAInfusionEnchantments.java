package therealpant.thaumicattempts.data;


import com.google.common.collect.Lists;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.config.ConfigRecipes;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.data.enchantments.InfusionVisChargeLevelRecipe;
import therealpant.thaumicattempts.data.enchantments.InfusionArcaneGuardLevelRecipe;
import therealpant.thaumicattempts.data.enchantments.InfusionRiftMomentumLevelRecipe;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;

import static therealpant.thaumicattempts.ThaumicAttempts.MODID;

public final class TAInfusionEnchantments {
    private TAInfusionEnchantments() {}

    public static void register() {

        ItemStack display = new ItemStack(ItemsTC.travellerBoots);
        Ingredient amuletOrStone = Ingredient.fromStacks(
                new ItemStack(ItemsTC.amuletVis,1,0),
                new ItemStack(ItemsTC.amuletVis,1,1)
        );

        // L1 (0 -> 1): базовые компоненты
        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "vischarge_lvl1"),
                new InfusionVisChargeLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        4,
                        new AspectList().add(Aspect.AURA, 50).add(Aspect.ENERGY, 30).add(Aspect.MAGIC, 40),
                        display,
                        1,
                        amuletOrStone,
                        new ItemStack(ItemsTC.plate, 1, 3),
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ItemsTC.plate, 1, 3)
                )
        );

        // L2 (1 -> 2): те же + доп предмет
        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "vischarge_lvl2"),
                new InfusionVisChargeLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        6,
                        new AspectList().add(Aspect.AURA, 80).add(Aspect.ENERGY, 50).add(Aspect.MAGIC, 60),
                        display,
                        2,
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ItemsTC.plate, 1, 3),
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ItemsTC.plate, 1, 3)
                )
        );

        // L3 (2 -> 3): те же + ещё доп предмет
        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "vischarge_lvl3"),
                new InfusionVisChargeLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        8,
                        new AspectList().add(Aspect.AURA, 110).add(Aspect.ENERGY, 80).add(Aspect.MAGIC, 90),
                        display,
                        3,
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ItemsTC.plate, 1, 3),
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ItemsTC.plate, 1, 3)
                )
        );

        ConfigRecipes.recipeGroups.put(
                MODID + ":vischarge", // ключ группы (его будем писать в research json)
                Lists.newArrayList(
                        new ResourceLocation(MODID, "vischarge_lvl1"),
                        new ResourceLocation(MODID, "vischarge_lvl2"),
                        new ResourceLocation(MODID, "vischarge_lvl3")
                )
        );
        ItemStack displayMomentum = new ItemStack(ItemsTC.voidSword);
        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "rift_momentum_lvl1"),
                new InfusionRiftMomentumLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        4,
                        new AspectList().add(Aspect.MOTION, 40).add(Aspect.ENERGY, 30).add(Aspect.MAGIC, 30),
                        displayMomentum,
                        1,
                        new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                        new ItemStack(ItemsTC.plate, 1, 3),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ItemsTC.visResonator)
                )
        );

        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "rift_momentum_lvl2"),
                new InfusionRiftMomentumLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        6,
                        new AspectList().add(Aspect.MOTION, 70).add(Aspect.ENERGY, 55).add(Aspect.MAGIC, 55),
                        displayMomentum,
                        2,
                        new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                        new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                        new ItemStack(ItemsTC.plate, 1, 3),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ItemsTC.visResonator),
                        new ItemStack(ItemsTC.voidSeed)
                )
        );

        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "rift_momentum_lvl3"),
                new InfusionRiftMomentumLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        8,
                        new AspectList().add(Aspect.MOTION, 100).add(Aspect.ENERGY, 80).add(Aspect.MAGIC, 80),
                        displayMomentum,
                        3,
                        new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                        new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                        new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                        new ItemStack(ItemsTC.plate, 1, 3),
                        new ItemStack(ModBlocksItems.RIFT_EMBER),
                        new ItemStack(ItemsTC.visResonator),
                        new ItemStack(ItemsTC.voidSeed),
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767)
                )
        );

        ConfigRecipes.recipeGroups.put(
                MODID + ":rift_momentum",
                Lists.newArrayList(
                        new ResourceLocation(MODID, "rift_momentum_lvl1"),
                        new ResourceLocation(MODID, "rift_momentum_lvl2"),
                        new ResourceLocation(MODID, "rift_momentum_lvl3")
                )
        );

        ItemStack displayArcaneGuard = new ItemStack(ItemsTC.crimsonPlateChest);
        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "arcane_guard_lvl1"),
                new InfusionArcaneGuardLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        4,
                        new AspectList().add(Aspect.PROTECT, 45).add(Aspect.MAGIC, 35).add(Aspect.CRYSTAL, 20),
                        displayArcaneGuard,
                        1,
                        new ItemStack(ModBlocksItems.RIFT_AMETIST),
                        new ItemStack(ItemsTC.voidSeed),
                        new ItemStack(ItemsTC.visResonator),
                        new ItemStack(ItemsTC.plate, 1, 3)
                )
        );

        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "arcane_guard_lvl2"),
                new InfusionArcaneGuardLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        6,
                        new AspectList().add(Aspect.PROTECT, 75).add(Aspect.MAGIC, 60).add(Aspect.CRYSTAL, 35),
                        displayArcaneGuard,
                        2,
                        new ItemStack(ModBlocksItems.RIFT_AMETIST),
                        new ItemStack(ModBlocksItems.RIFT_AMETIST),
                        new ItemStack(ItemsTC.voidSeed),
                        new ItemStack(ItemsTC.voidSeed),
                        new ItemStack(ItemsTC.visResonator),
                        new ItemStack(ItemsTC.plate, 1, 3)
                )
        );

        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(MODID, "arcane_guard_lvl3"),
                new InfusionArcaneGuardLevelRecipe(
                        "TA_GOLEM_MIRRORS",
                        8,
                        new AspectList().add(Aspect.PROTECT, 110).add(Aspect.MAGIC, 90).add(Aspect.CRYSTAL, 50),
                        displayArcaneGuard,
                        3,
                        new ItemStack(ModBlocksItems.RIFT_AMETIST),
                        new ItemStack(ModBlocksItems.RIFT_AMETIST),
                        new ItemStack(ModBlocksItems.RIFT_AMETIST),
                        new ItemStack(ItemsTC.voidSeed),
                        new ItemStack(ItemsTC.voidSeed),
                        new ItemStack(ItemsTC.voidSeed),
                        new ItemStack(ItemsTC.visResonator),
                        new ItemStack(ItemsTC.plate, 1, 3)
                )
        );

        ConfigRecipes.recipeGroups.put(
                MODID + ":arcane_guard",
                Lists.newArrayList(
                        new ResourceLocation(MODID, "arcane_guard_lvl1"),
                        new ResourceLocation(MODID, "arcane_guard_lvl2"),
                        new ResourceLocation(MODID, "arcane_guard_lvl3")
                )
        );
    }

}

