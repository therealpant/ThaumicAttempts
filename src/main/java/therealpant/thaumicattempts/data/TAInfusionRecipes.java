// src/main/java/therealpant/thaumicattempts/data/TAInfusionRecipes.java
package therealpant.thaumicattempts.data;

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
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.common.crafting.infusion.RecipeExtractGem;
import therealpant.thaumicattempts.common.crafting.infusion.RecipeInlayGem;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;

import static org.apache.logging.log4j.core.util.Assert.isEmpty;
import static thaumcraft.api.blocks.BlocksTC.stoneEldritchTile;

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
                System.out.println("[TA] Registered infusion: thaumicattempts:mirror_manager_infusion");
            } else {
                System.out.println("[TA] Skip mirror_manager_core: item not found");
            }
        }
        // ------------ Зеркальный Моячек ------------ \\
        try {
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
            System.out.println("[TA] Registered infusion: requester_infusion");
        } catch (Throwable t) {
            System.out.println("[TA] Skip requester_infusion: " + t);
        }
        // ------------ Распеределитель Задачь ------------ \\
        try {
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
            System.out.println("[TA] Registered infusion: dispatcher_infusion");
        } catch (Throwable t) {
            System.out.println("[TA] Skip dispatcher_infusion: " + t);
        }
        // ------------ Управляющий Наполнением ------------ \\
        try {
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
            System.out.println("[TA] Registered infusion: infusion_requester_infusion");
        } catch (Throwable t) {
            System.out.println("[TA] Skip infusion_requester_infusion: " + t);
        }
    }
}



