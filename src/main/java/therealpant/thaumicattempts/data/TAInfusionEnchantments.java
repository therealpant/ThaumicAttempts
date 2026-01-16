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
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
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
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
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
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ItemsTC.primordialPearl, 1, 32767),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
                        new ItemStack(ModBlocksItems.RIFT_FLOWER),
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
    }

}

