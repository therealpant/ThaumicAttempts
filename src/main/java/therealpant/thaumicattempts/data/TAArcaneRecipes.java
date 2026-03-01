// src/main/java/therealpant/thaumicattempts/data/TAArcaneRecipes.java
package therealpant.thaumicattempts.data;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.item.EnumDyeColor;

import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.items.ItemsTC;

import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;

import static thaumcraft.api.blocks.BlocksTC.*;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class TAArcaneRecipes {

    @SubscribeEvent
    public static void onRegisterRecipes(RegistryEvent.Register<IRecipe> e) {

        /* ---------- Improved Arcane Ear (как у тебя) ---------- */
        {
            ShapedArcaneRecipe ear = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "arcane_ear_band"),
                    "MINDCLOCKWORK",
                    25,
                    new AspectList().add(Aspect.AIR, 1).add(Aspect.ORDER, 1),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":arcane_ear_band")),
                    " C ",
                    " E ",
                    " M ",
                    'C', new ItemStack(ItemsTC.mind, 1, 0),
                    'E', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:arcane_ear"))),
                    'M', new ItemStack(ItemsTC.mechanismSimple)
            );
            ear.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "arcane_ear_band"));
            e.getRegistry().register(ear);
        }

        /* ---------- Паттерны ---------- */
        {
            ShapedArcaneRecipe pat1 = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "patterns"),
                    "TA_GOLEMCRAFT",
                    150,
                    new AspectList().add(Aspect.ORDER, 2).add(Aspect.EARTH, 2).add(Aspect.ENTROPY, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":craft_pattern")),
                    "GSG",
                    "GBG",
                    "GAG",
                    'A', Blocks.CRAFTING_TABLE,
                    'G', "ingotGold",
                    'B', net.minecraft.init.Items.BOOK,
                    'S', new ItemStack(ItemsTC.seals, 1, 15)
            );
            pat1.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "pattern_craft"));
            e.getRegistry().register(pat1);
        }
        /* ---------- Паттерны Аркейн ---------- */
        {
            ShapedArcaneRecipe pat2 = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "patterns"),
                    "TA_GOLEMCRAFT",
                    150,
                    new AspectList().add(Aspect.AIR, 2).add(Aspect.ORDER, 2).add(Aspect.EARTH, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":arcane_pattern")),
                    "GSG",
                    "GBG",
                    "GAG",
                    'A', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:arcane_workbench"))),
                    'G', "ingotGold",
                    'B', net.minecraft.init.Items.BOOK,
                    'S', new ItemStack(ItemsTC.seals, 1, 15)

            );
            pat2.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "pattern_arcane"));
            e.getRegistry().register(pat2);
        }
        /* ---------- Паттерны Инфузия ---------- */
        {
            ShapedArcaneRecipe infusion_pattern = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "patterns"),
                    "TA_GOLEMINFUSION",
                    150,
                    new AspectList().add(Aspect.ENTROPY, 2).add(Aspect.ORDER, 2).add(Aspect.EARTH, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":infusion_pattern")),
                    "GSG",
                    "GBG",
                    "GAG",
                    'A', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:infusion_matrix"))),
                    'G', "ingotGold",
                    'B', net.minecraft.init.Items.BOOK,
                    'S', new ItemStack(ItemsTC.seals, 1, 15)

            );
            infusion_pattern.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "pattern_infusion_arcane"));
            e.getRegistry().register(infusion_pattern);
        }

        /* ---------- Order Terminal (как у тебя) ---------- */
        {
            ShapedArcaneRecipe r = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "order_terminal"),
                    "TA_GOLEM_MIRRORS",
                    200,
                    new AspectList().add(Aspect.AIR, 2).add(Aspect.FIRE, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":order_terminal")),
                    "BDA",
                    " T ",
                    "CSC",
                    'B', net.minecraft.init.Items.BOOK,
                    'D', new ItemStack(Item.getItemFromBlock(BlocksTC.banners.get(EnumDyeColor.RED))),
                    'A', new ItemStack(ItemsTC.scribingTools),
                    'T', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:table_wood"))),
                    'S', Blocks.BOOKSHELF,
                    'C', new ItemStack(Item.getItemFromBlock(slabGreatwood))
            );
            r.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "order_terminal"));
            e.getRegistry().register(r);
        }

        /* ---------- Математическое ядро (Arcane Workbench, 500 vis; AER/FIRE/ORDO x3) ---------- */
        {
            ShapedArcaneRecipe mathCore = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "math_core_arcane"),
                    "TA_GOLEM_INTEGRATION",
                    500,
                    new AspectList().add(Aspect.AIR, 3).add(Aspect.FIRE, 3).add(Aspect.ORDER, 3),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":math_core")),
                    // 1,3,7,9 — Eldritch Stone; 5 — Primordial Pearl; 4,6 — Mnemonic Matrix; 2,8 — Void Plate
                    "EVE",
                    "MPM",
                    "EVE",
                    'E', new ItemStack(Item.getItemFromBlock(stoneEldritchTile)),
                    'V', new ItemStack(ItemsTC.plate, 1, 3),
                    'M', new ItemStack(Item.getByNameOrId("thaumcraft:brain_box")),
                    'P', new ItemStack(ItemsTC.primordialPearl, 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
            );
            mathCore.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "math_core_arcane"));
            e.getRegistry().register(mathCore);
        }

        /* ---------- Стабилизатор зеркал (Arcane Workbench, 500 vis; AER/ENTROPY/ORDO x3) ---------- */
        {
            ShapedArcaneRecipe stabilizer = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "mirror_stabilizer_arcane"),
                    "TA_GOLEM_INTEGRATION",
                    500,
                    new AspectList().add(Aspect.AIR, 3).add(Aspect.ENTROPY, 3).add(Aspect.ORDER, 3),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":mirror_stabilizer")),
                    // 1,3,7,9 — Eldritch Stone; 5 — Primordial Pearl; 4,6 — Mirrored Glass; 2,8 — Void Plate
                    "EVE",
                    "GPG",
                    "EVE",
                    'E', new ItemStack(Item.getItemFromBlock(stoneEldritchTile)),
                    'V', new ItemStack(ItemsTC.plate, 1, 3),
                    'G', new ItemStack(ItemsTC.mirroredGlass),
                    'P', new ItemStack(ItemsTC.primordialPearl, 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
            );
            stabilizer.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "mirror_stabilizer_arcane"));
            e.getRegistry().register(stabilizer);
        }
        // --------- ELDRITCH_construction  (Arcane Workbench, 500 vis)
        {
            ShapedArcaneRecipe eld_con = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "eldritch_construction_arcane"),
                    "MINDCLOCKWORK",
                    500,
                    new AspectList().add(Aspect.AIR, 1).add(Aspect.ORDER, 1).add(Aspect.ENTROPY, 3),
                    new ItemStack(Item.getItemFromBlock(TABlocks.ELDRITCH_CONSTRUCTION)),
                    " E ",
                    "ROR",
                    " E ",
                    'E', new ItemStack(Item.getItemFromBlock(stoneEldritchTile)),
                    'O', Blocks.OBSIDIAN,
                    'R', new ItemStack(ModBlocksItems.RIFT_STONE)
            );
            eld_con.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "eldritch_construction"));
            e.getRegistry().register(eld_con);
        }

        // --------- Rift stone base  (Arcane Workbench, 500 vis)
        {
            ShapedArcaneRecipe rif_base = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_base_arcane"),
                    "MINDCLOCKWORK",
                    500,
                    new AspectList().add(Aspect.AIR, 1).add(Aspect.ORDER, 1).add(Aspect.ENTROPY, 3),
                    new ItemStack(Item.getItemFromBlock(TABlocks.RIFT_STONE_BASE)),
                    "PMP",
                    "EOE",
                    "CEC",
                    'P', new ItemStack(ItemsTC.plate, 1, 3),
                    'M', new ItemStack(ItemsTC.mirroredGlass),
                    'E', new ItemStack(Item.getItemFromBlock(stoneEldritchTile)),
                    'O', new ItemStack(ItemsTC.primordialPearl, 1, OreDictionary.WILDCARD_VALUE),
                    'C', new ItemStack(TABlocks.ELDRITCH_CONSTRUCTION)
            );
            rif_base.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "rift_stone_base"));
            e.getRegistry().register(rif_base);
        }

        // --------- Великодревесная коробка (Arcane Workbench, 200 vis)
        {
            ShapedArcaneRecipe greatwood_box = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "resource_requester_arcane"),
                    "TA_GOLEMDELIVERY",
                    200,
                    new AspectList().add(Aspect.AIR, 2).add(Aspect.FIRE, 2).add(Aspect.ORDER, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":resource_requester")),
                    "LBL",
                    "SMS",
                    "LCL",
                    'L', new ItemStack(Item.getItemFromBlock(plankGreatwood)),
                    'B', new ItemStack(Item.getItemFromBlock(BlocksTC.banners.get(EnumDyeColor.RED))),
                    'S', new ItemStack(Item.getItemFromBlock(slabGreatwood)),
                    'M', new ItemStack(ItemsTC.mind, 1, 1),
                    'C', new ItemStack(Item.getItemFromBlock(hungryChest))
            );
            greatwood_box.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "resource_requester_arcane"));
            e.getRegistry().register(greatwood_box);
        }
        // --------- Инстукция для наполнения (Arcane Workbench, 150 vis)
        {
            ShapedArcaneRecipe item_list = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "resource_list_arcane"),
                    "TA_GOLEMDELIVERY",
                    150,
                    new AspectList().add(Aspect.ORDER, 2).add(Aspect.WATER, 2),
                    new ItemStack(ModBlocksItems.RESOURCE_LIST),
                    "GSG", "GBG", "GHG",
                    'S', new ItemStack(ItemsTC.seals, 1, 15),
                    'B', net.minecraft.init.Items.BOOK,
                    'G', "ingotGold",
                    'H', new ItemStack(Item.getItemFromBlock(hungryChest))
            );
            item_list.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "resource_list_arcane"));
            e.getRegistry().register(item_list);
        }
        // --------- Кристалический Массив (Arcane Workbench, 350 vis)
        {
            ShapedArcaneRecipe item_list = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "cristal_mas_arcane"),
                    "TA_AURA_BOOSTER",
                    350,
                    new AspectList().add(Aspect.ORDER, 2).add(Aspect.WATER, 2),
                    new ItemStack(TABlocks.RIFT_CRISTAL_BLOCK),
                    "GSG", "GBG", "GSG",
                    'S', new ItemStack(ItemsTC.visResonator),
                    'B', new ItemStack(ItemsTC.primordialPearl, 1, OreDictionary.WILDCARD_VALUE),
                    'G',new ItemStack(ModBlocksItems.RIFT_CRISTAL)
            );
            item_list.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "cristal_mas_arcane"));
            e.getRegistry().register(item_list);
        }
    }
}