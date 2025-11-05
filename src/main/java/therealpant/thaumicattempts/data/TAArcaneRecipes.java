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

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.items.ItemsTC;

import thaumcraft.common.blocks.BlockTC;
import therealpant.thaumicattempts.ThaumicAttempts;

import static thaumcraft.api.blocks.BlocksTC.stoneEldritchTile;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class TAArcaneRecipes {

    @SubscribeEvent
    public static void onRegisterRecipes(RegistryEvent.Register<IRecipe> e) {

        /* ---------- Improved Arcane Ear (как у тебя) ---------- */
        try {
            ShapedArcaneRecipe ear = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "arcane_ear_band"),
                    "MINDCLOCKWORK",
                    25,
                    new AspectList().add(Aspect.AIR, 1).add(Aspect.ORDER, 1),
                    new ItemStack(Item.getByNameOrId("thaumcraft:arcane_ear_band")),
                    " C ",
                    " E ",
                    " M ",
                    'C', new ItemStack(ItemsTC.mind, 1, 0),
                    'E', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:arcane_ear"))),
                    'M', new ItemStack(ItemsTC.mechanismSimple)
            );
            ear.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "arcane_ear_band"));
            e.getRegistry().register(ear);
        } catch (Throwable t) {
            System.out.println("[TA] Skip arcane_ear_band: " + t);
        }

        /* ---------- Паттерны (как у тебя) ---------- */
        try {
            ShapedArcaneRecipe pat1 = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "patterns"),
                    "TA_GOLEMCRAFT",
                    150,
                    new AspectList().add(Aspect.ORDER, 2).add(Aspect.EARTH, 2).add(Aspect.ENTROPY, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":craft_pattern")),
                    "PAP",
                    "GMG",
                    "PSP",
                    'P', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:plank_greatwood"))),
                    'A', Blocks.CRAFTING_TABLE,
                    'G', "ingotGold",
                    'M', net.minecraft.init.Items.MAP,
                    'S', new ItemStack(ItemsTC.mind, 1, 0)
            );
            pat1.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "pattern_craft"));
            e.getRegistry().register(pat1);
        } catch (Throwable t) {
            System.out.println("[TA] Skip pattern_craft: " + t);
        }

        try {
            ShapedArcaneRecipe pat2 = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "patterns"),
                    "TA_GOLEMCRAFT",
                    150,
                    new AspectList().add(Aspect.AIR, 2).add(Aspect.ORDER, 2).add(Aspect.EARTH, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":arcane_pattern")),
                    "PAP",
                    "GMG",
                    "PSP",
                    'P', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:plank_greatwood"))),
                    'A', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:arcane_workbench"))),
                    'G', "ingotGold",
                    'M', net.minecraft.init.Items.MAP,
                    'S', new ItemStack(ItemsTC.mind, 1, 0)
            );
            pat2.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "pattern_arcane"));
            e.getRegistry().register(pat2);
        } catch (Throwable t) {
            System.out.println("[TA] Skip pattern_arcane: " + t);
        }

        /* ---------- Order Terminal (как у тебя) ---------- */
        try {
            ShapedArcaneRecipe r = new ShapedArcaneRecipe(
                    new ResourceLocation(ThaumicAttempts.MODID, "order_terminal"),
                    "TA_GOLEM_MIRRORS",
                    100,
                    new AspectList().add(Aspect.AIR, 2).add(Aspect.FIRE, 2),
                    new ItemStack(Item.getByNameOrId(ThaumicAttempts.MODID + ":order_terminal")),
                    " B ",
                    " T ",
                    " S ",
                    'B', net.minecraft.init.Items.BOOK,
                    'T', new ItemStack(Item.getItemFromBlock(Block.getBlockFromName("thaumcraft:table_wood"))),
                    'S', Blocks.BOOKSHELF
            );
            r.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "order_terminal"));
            e.getRegistry().register(r);
        } catch (Throwable t) {
            System.out.println("[TA] Skip order_terminal: " + t);
        }

        /* ---------- Математическое ядро (Arcane Workbench, 500 vis; AER/FIRE/ORDO x3) ---------- */
        try {
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
                    'V', new ItemStack(ItemsTC.plate,1,3),
                    'M', new ItemStack(Item.getByNameOrId("thaumcraft:brain_box")),
                    'P', new ItemStack(ItemsTC.primordialPearl, 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
            );
            mathCore.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "math_core_arcane"));
            e.getRegistry().register(mathCore);
        } catch (Throwable t) {
            System.out.println("[TA] Skip math_core_arcane: " + t);
        }

        /* ---------- Стабилизатор зеркал (Arcane Workbench, 500 vis; AER/ENTROPY/ORDO x3) ---------- */
        try {
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
                    'V', new ItemStack(ItemsTC.plate,1,3),
                    'G', new ItemStack(ItemsTC.mirroredGlass),
                    'P', new ItemStack(ItemsTC.primordialPearl, 1, net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE)
            );
            stabilizer.setRegistryName(new ResourceLocation(ThaumicAttempts.MODID, "mirror_stabilizer_arcane"));
            e.getRegistry().register(stabilizer);
        } catch (Throwable t) {
            System.out.println("[TA] Skip mirror_stabilizer_arcane: " + t);
        }
    }
}
