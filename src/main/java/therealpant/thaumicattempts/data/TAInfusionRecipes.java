// src/main/java/therealpant/thaumicattempts/data/TAInfusionRecipes.java
package therealpant.thaumicattempts.data;

import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.BlockTC;
import thaumcraft.common.config.ConfigRecipes;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.common.crafting.infusion.RecipeExtractGem;
import therealpant.thaumicattempts.common.crafting.infusion.RecipeInlayGem;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;

import static org.apache.logging.log4j.core.util.Assert.isEmpty;
import static thaumcraft.api.blocks.BlocksTC.stoneEldritchTile;
import static therealpant.thaumicattempts.ThaumicAttempts.MODID;

public final class TAInfusionRecipes {

    private TAInfusionRecipes() {}

    public static void register() {
        // ===== GEM INLAY RECIPES =====
        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(ThaumicAttempts.MODID, "inlay_gem"),
                new RecipeInlayGem(
                        "TA_GOLEMCRAFT",
                        1,
                        new AspectList().add(Aspect.MAGIC, 1),
                        new ItemStack(ModBlocksItems.TA_GEM, 1, OreDictionary.WILDCARD_VALUE)
                )
        );
        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(ThaumicAttempts.MODID, "extract_gem"),
                new RecipeExtractGem(
                        "TA_GOLEMCRAFT",
                        1,
                        new AspectList().add(Aspect.MAGIC, 1),
                        new ItemStack(ItemsTC.salisMundus)
                )
        );
        // ===== 1) GOLEM_CRAFTER из ванильного верстака =====
        // Центральный: Crafting Table
        // Внешние: 2× Greatwood Planks, 1× Biothaumic Mind, 1× Morphic Resonator, 1× Seal: Store, 1× Seal: Use
        // Эссенция: 50 Vacuos, 100 Machina, 100 Fabrico
        // Нестабильность: высокая
        ItemStack OUT_CRAFTER = new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":golem_crafter"));
        Object CENTER1 = Blocks.CRAFTING_TABLE;

        Object[] COMPS1 = new Object[]{
                // 2× доски Великого дерева
                new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:plank_greatwood"))),

                // 1× Биотаумический разум
                new ItemStack(ItemsTC.mind, 1, 1), // meta 1 = Biothaumic Mind
                // 1× Morphic Resonator
                new ItemStack(ItemsTC.seals, 1, 3),

                new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:plank_greatwood"))),
                // 1× Seal: Store и 1× Seal: Use.
                // В TC6 все печати — один Item с субтипами. Чтобы не гадать меты, берём сам Item — это позволит ЛЮБУЮ печать.
                // Если хочешь зафиксировать строго Store/Use — подставь их мета-индексы сюда (new ItemStack(ItemsTC.seals,1,<meta>)).
                new ItemStack(ItemsTC.morphicResonator),

                new ItemStack(ItemsTC.seals, 1, 14),
        };

        AspectList AS1 = new AspectList()
                .add(Aspect.VOID, 50)
                .add(Aspect.MECHANISM, 100)
                .add(Aspect.CRAFT, 100);

        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(ThaumicAttempts.MODID, "golem_crafter_infusion"),
                new InfusionRecipe("TA_GOLEMCRAFT", OUT_CRAFTER, /*instab*/7, AS1, CENTER1, COMPS1)
        );

        // ===== 2) ARCANE_CRAFTER из GOLEM_CRAFTER =====
        // Центральный: Golem Crafter
        // Внешние: 2× Salis Mundus, 2× Vis Resonator, 1× Arcane Workbench, 1× Enchanted Fabric
        // Эссенция: 100 Auram, 100 Praecantatio, 50 Machina
        // Нестабильность: высокая
        ItemStack OUT_ARC = new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":arcane_crafter"));
        Object CENTER2 = new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":golem_crafter"));

        Object[] COMPS2 = new Object[]{
                new ItemStack(ItemsTC.salisMundus),
                new ItemStack(ItemsTC.visResonator),
                Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:arcane_workbench")),
                new ItemStack(ItemsTC.salisMundus),
                new ItemStack(ItemsTC.visResonator),
                new ItemStack(ItemsTC.fabric)
        };

        AspectList AS2 = new AspectList()
                .add(Aspect.AURA, 100)
                .add(Aspect.MAGIC, 100)
                .add(Aspect.MECHANISM, 50);

        ThaumcraftApi.addInfusionCraftingRecipe(
                new ResourceLocation(ThaumicAttempts.MODID, "arcane_crafter_infusion"),
                new InfusionRecipe("TA_GOLEMCRAFT", OUT_ARC, /*instab*/7, AS2, CENTER2, COMPS2)
        );

        // ===== MIRROR_MANAGER_CORE  =====
        {
            Item outItem = Item.getByNameOrId(ThaumicAttempts.MODID + ":mirror_manager_core");
            if (outItem != null) {
                ItemStack OUT = new ItemStack(outItem);

                // центр: мозг в банке (thaumcraft:jar_brain)
                Object CENTER = ItemsTC.primordialPearl;

                Object[] COMPS = new Object[]{
                        new ItemStack(ItemsTC.mind, 1, 1),
                        new ItemStack(Item.getItemFromBlock(stoneEldritchTile)),
                        new ItemStack(ItemsTC.mirroredGlass),
                        new ItemStack(Item.getItemFromBlock(stoneEldritchTile)),
                        new ItemStack(ItemsTC.mind, 1, 1),
                        new ItemStack(Item.getItemFromBlock(stoneEldritchTile)),
                        new ItemStack(ItemsTC.mirroredGlass),
                        new ItemStack(Item.getItemFromBlock(stoneEldritchTile))
                };

                AspectList AS = new AspectList()
                        .add(Aspect.ELDRITCH, 250)
                        .add(Aspect.MOTION, 150)
                        .add(Aspect.ORDER, 150);

                ThaumcraftApi.addInfusionCraftingRecipe(
                        new ResourceLocation(ThaumicAttempts.MODID, "mirror_manager_infusion"),
                        new InfusionRecipe("TA_GOLEM_MIRRORS", OUT, 8, AS, CENTER, COMPS)
                );
            }
        }
        // ------------ Зеркальный Моячек ------------ \\
         {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "requester_infusion"),
                    new InfusionRecipe(
                            "TA_GOLEM_INTEGRATION",
                            new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":pattern_requester")), // результат
                            6, // нестабильность — можно поправить
                            new AspectList()
                                    .add(Aspect.MOTION, 75)
                                    .add(Aspect.VOID, 75)
                                    .add(Aspect.ELDRITCH, 75),
                            // центр — магическое зеркало (блок)
                            new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:mirror"))),
                            // внешние пилоны: 4 void-пластины и 4 mirrored glass, чередуем
                            new ItemStack(ItemsTC.plate, 1, 3),
                            new ItemStack(ItemsTC.mirroredGlass),
                            new ItemStack(ItemsTC.plate, 1, 3),
                            new ItemStack(ItemsTC.mirroredGlass),
                            new ItemStack(ItemsTC.plate, 1, 3),
                            new ItemStack(ItemsTC.mirroredGlass),
                            new ItemStack(ItemsTC.plate, 1, 3),
                            new ItemStack(ItemsTC.mirroredGlass)
                    )
            );
        }
        // ------------ Распеределитель Задачь ------------ \\
         {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "dispatcher_infusion"),
                    new InfusionRecipe(
                            "TA_GOLEMCONTROLER",
                            new ItemStack(TABlocks.GOLEM_DISPATCHER_ITEM),
                            8,
                            new AspectList()
                                    .add(Aspect.MECHANISM, 125)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.MIND, 150),
                            new ItemStack(Item.getItemFromBlock(ModBlocksItems.MATH_CORE)),
                            new ItemStack(ItemsTC.plate, 1, 3),
                            new ItemStack(Item.getByNameOrId("thaumcraft:brain_box")),
                            new ItemStack(ItemsTC.mind, 1, 1),
                            new ItemStack(Item.getByNameOrId("thaumcraft:brain_box")),
                            new ItemStack(ItemsTC.plate, 1, 3),
                            new ItemStack(Item.getByNameOrId("thaumcraft:brain_box")),
                            new ItemStack(ItemsTC.mind, 1, 1),
                            new ItemStack(Item.getByNameOrId("thaumcraft:brain_box"))

                    )
            );
        }
        // ------------ Управляющий Наполнением ------------ \\
       {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "infusion_requester_infusion"),
                    new InfusionRecipe(
                            "TA_GOLEMINFUSION",
                            new ItemStack(TABlocks.INFUSION_REQUESTER_ITEM),
                            8,
                            new AspectList()
                                    .add(Aspect.MECHANISM, 125)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.MIND, 150),
                            new ItemStack(Item.getItemFromBlock(TABlocks.RESOURCE_REQUESTER)),
                            new ItemStack(Item.getItemFromBlock(BlocksTC.banners.get(EnumDyeColor.RED))),
                            new ItemStack(Item.getItemFromBlock(BlocksTC.stoneArcane)),
                            new ItemStack(ItemsTC.mind, 1, 1),
                            new ItemStack(Item.getItemFromBlock(BlocksTC.stoneArcane)),
                            new ItemStack(Item.getItemFromBlock(BlocksTC.banners.get(EnumDyeColor.RED))),
                            new ItemStack(Item.getItemFromBlock(BlocksTC.stoneArcane)),
                            Items.ENDER_PEARL,
                            new ItemStack(Item.getItemFromBlock(BlocksTC.stoneArcane))

                    )
            );
        }
        // ------------ Ядро Резонирующего Пилона  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "aura_booster_core_infusion"),
                    new InfusionRecipe(
                            "TA_AURA_BOOSTER",
                            new ItemStack(TABlocks.AURA_BOOSTER_CORE),
                            10,
                            new AspectList()
                                    .add(Aspect.MECHANISM, 125)
                                    .add(Aspect.ENERGY, 250)
                                    .add(Aspect.ELDRITCH, 125)
                                    .add(Aspect.CRYSTAL, 75)
                                    .add(Aspect.AURA, 250),
                            new ItemStack(Item.getItemFromBlock(TABlocks.RIFT_CRISTAL_BLOCK)),
                            new ItemStack(Item.getItemFromBlock(TABlocks.ELDRITCH_CONSTRUCTION)),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                            new ItemStack(Item.getItemFromBlock(TABlocks.ELDRITCH_CONSTRUCTION)),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                            new ItemStack(Item.getItemFromBlock(TABlocks.ELDRITCH_CONSTRUCTION)),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                            new ItemStack(Item.getItemFromBlock(TABlocks.ELDRITCH_CONSTRUCTION)),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL)

                    )
            );
        }
        // ------------ Янтарь Разлома  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "rift_ember_infusion"),
                    new InfusionRecipe(
                            "TA_ANOMALY",
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            8,
                            new AspectList()
                                    .add(Aspect.ENERGY, 75)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.CRYSTAL, 75)
                                    .add(Aspect.AURA, 75),
                            new ItemStack(ItemsTC.amber),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_FLOWER),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_FLOWER),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_FLOWER),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_FLOWER)

                    )
            );
        }
        // ------------ Аметист Разлома  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "rift_ametist_infusion"),
                    new InfusionRecipe(
                            "TA_ANOMALY",
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            9,
                            new AspectList()
                                    .add(Aspect.ENERGY, 75)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.CRYSTAL, 75)
                                    .add(Aspect.AURA, 75),
                            Items.QUARTZ,
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_STONE),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_STONE),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_STONE),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_STONE)

                    )
            );
        }
        // ------------ Бриллиант Разлома  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "rift_briliant_infusion"),
                    new InfusionRecipe(
                            "TA_ANOMALY",
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            10,
                            new AspectList()
                                    .add(Aspect.ENERGY, 75)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.CRYSTAL, 75)
                                    .add(Aspect.AURA, 75),
                            Items.DIAMOND,
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL),
                            new ItemStack(ItemsTC.voidSeed),
                            new ItemStack(ModBlocksItems.RIFT_CRISTAL)

                    )
            );
        }
        // ------------ Инкрустация: Слабый Янтарь  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "ember_I_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,0),
                            9,
                            new AspectList()
                                    .add(Aspect.ENERGY, 75)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.CRYSTAL, 75)
                                    .add(Aspect.AURA, 75),
                            new ItemStack(ItemsTC.primordialPearl, 1, OreDictionary.WILDCARD_VALUE),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER)
                    )
            );
        }
        // ------------ Инкрустация: Стабильный Янтарь  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "ember_II_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,1),
                            10,
                            new AspectList()
                                    .add(Aspect.ENERGY, 150)
                                    .add(Aspect.ELDRITCH, 150)
                                    .add(Aspect.CRYSTAL, 150)
                                    .add(Aspect.AURA, 150),
                            new ItemStack(ModBlocksItems.TA_GEM,1,0),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER)
                    )
            );
        }
        // ------------ Инкрустация: Изысканный Янтарь  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "ember_III_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,2),
                            11,
                            new AspectList()
                                    .add(Aspect.ENERGY, 225)
                                    .add(Aspect.ELDRITCH, 225)
                                    .add(Aspect.CRYSTAL, 225)
                                    .add(Aspect.AURA, 225),
                            new ItemStack(ModBlocksItems.TA_GEM,1,1),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_EMBER)
                    )
            );
        }
        ConfigRecipes.recipeGroups.put(
                MODID + ":ember_infusion",
                Lists.newArrayList(
                        new ResourceLocation(MODID, "ember_I_infusion"),
                        new ResourceLocation(MODID, "ember_II_infusion"),
                        new ResourceLocation(MODID, "ember_III_infusion")
                )
        );
        // ------------ Инкрустация: Слабый Аметист  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "ametist_I_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,3),
                            9,
                            new AspectList()
                                    .add(Aspect.ENERGY, 75)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.CRYSTAL, 75)
                                    .add(Aspect.AURA, 75),
                            new ItemStack(ItemsTC.primordialPearl, 1, OreDictionary.WILDCARD_VALUE),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST)
                    )
            );
        }
        // ------------ Инкрустация: Стабильный Аметист  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "ametist_II_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,4),
                            10,
                            new AspectList()
                                    .add(Aspect.ENERGY, 150)
                                    .add(Aspect.ELDRITCH, 150)
                                    .add(Aspect.CRYSTAL, 150)
                                    .add(Aspect.AURA, 150),
                            new ItemStack(ModBlocksItems.TA_GEM,1,3),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST)
                    )
            );
        }
        // ------------ Инкрустация: Изысканый Аметист  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "ametist_III_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,5),
                            11,
                            new AspectList()
                                    .add(Aspect.ENERGY, 225)
                                    .add(Aspect.ELDRITCH, 225)
                                    .add(Aspect.CRYSTAL, 225)
                                    .add(Aspect.AURA, 225),
                            new ItemStack(ModBlocksItems.TA_GEM,1,4),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_AMETIST)
                    )
            );
        }
        ConfigRecipes.recipeGroups.put(
                MODID + ":ametist_infusion",
                Lists.newArrayList(
                        new ResourceLocation(MODID, "ametist_I_infusion"),
                        new ResourceLocation(MODID, "ametist_II_infusion"),
                        new ResourceLocation(MODID, "ametist_III_infusion")
                )
        );
        // ------------ Инкрустация: Слабый Бриллиант  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "diamond_I_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,6),
                            9,
                            new AspectList()
                                    .add(Aspect.ENERGY, 75)
                                    .add(Aspect.ELDRITCH, 75)
                                    .add(Aspect.CRYSTAL, 75)
                                    .add(Aspect.AURA, 75),
                            new ItemStack(ItemsTC.primordialPearl, 1, OreDictionary.WILDCARD_VALUE),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT)
                    )
            );
        }
        // ------------ Инкрустация: Стабильный Бриллиант  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "diamond_II_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,7),
                            10,
                            new AspectList()
                                    .add(Aspect.ENERGY, 150)
                                    .add(Aspect.ELDRITCH, 150)
                                    .add(Aspect.CRYSTAL, 150)
                                    .add(Aspect.AURA, 150),
                            new ItemStack(ModBlocksItems.TA_GEM,1,6),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT)
                    )
            );
        }
        // ------------ Инкрустация: Изысканый Бриллиант  ------------ \\
        {
            ThaumcraftApi.addInfusionCraftingRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "diamond_III_infusion"),
                    new InfusionRecipe(
                            "TA_GEM",
                            new ItemStack(ModBlocksItems.TA_GEM,1,8),
                            11,
                            new AspectList()
                                    .add(Aspect.ENERGY, 225)
                                    .add(Aspect.ELDRITCH, 225)
                                    .add(Aspect.CRYSTAL, 225)
                                    .add(Aspect.AURA, 225),
                            new ItemStack(ModBlocksItems.TA_GEM,1,7),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT),
                            new ItemStack(ItemsTC.ingots,1,1),
                            new ItemStack(ModBlocksItems.RIFT_BRILIANT)
                    )
            );
        }
        ConfigRecipes.recipeGroups.put(
                MODID + ":diamond_infusion",
                Lists.newArrayList(
                        new ResourceLocation(MODID, "diamond_I_infusion"),
                        new ResourceLocation(MODID, "diamond_II_infusion"),
                        new ResourceLocation(MODID, "diamond_III_infusion")
                )
        );
    }
}



