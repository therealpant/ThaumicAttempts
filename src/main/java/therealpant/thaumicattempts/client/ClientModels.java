// src/main/java/therealpant/thaumicattempts/client/ClientModels.java
package therealpant.thaumicattempts.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.render.*;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.golemnet.block.BlockMathCore;
import therealpant.thaumicattempts.golemnet.block.BlockMirrorStabilizer;
import therealpant.thaumicattempts.golemnet.tile.TileGolemDispatcher;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.proxy.CommonProxy;
import therealpant.thaumicattempts.tile.TilePillar;

import java.util.function.Supplier;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class ClientModels extends CommonProxy {

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent e) {
        /* ---------- ITEM-модели (иконки в инвентаре) ---------- */

        // простые предметы
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.CRAFT_PATTERN, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":craft_pattern", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.INFUSION_PATTERN, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":infusion_pattern", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.ARCANE_PATTERN, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":arcane_pattern", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                ModBlocksItems.RESOURCE_LIST, 0,
                new ModelResourceLocation(ThaumicAttempts.MODID + ":resource_list", "inventory")
        );
        // ItemBlock'и наших блоков (иконки!)
        registerItemBlockModel(ModBlocksItems.GOLEM_CRAFTER, ThaumicAttempts.MODID + ":golem_crafter");
        registerItemBlockModel(ModBlocksItems.ARCANE_CRAFTER, ThaumicAttempts.MODID + ":arcane_crafter");
        registerItemBlockModel(ModBlocksItems.MATH_CORE, ThaumicAttempts.MODID + ":math_core");
        registerItemBlockModel(ModBlocksItems.MIRROR_STABILIZER, ThaumicAttempts.MODID + ":mirror_stabilizer");
        registerItemBlockModel(TABlocks.MIRROR_MANAGER_BASE, ThaumicAttempts.MODID + ":mirror_manager_base");
        registerItemBlockModel(TABlocks.MIRROR_MANAGER_CORE, ThaumicAttempts.MODID + ":mirror_manager_core");

        // ухо — используем таумовскую иконку предмета
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(ModBlocksItems.EAR_BAND), 0,
                new ModelResourceLocation("thaumcraft:arcane_ear", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(TABlocks.RESOURCE_REQUESTER), 0,
                new ModelResourceLocation("thaumicattempts:resource_requester", "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(TABlocks.INFUSION_REQUESTER), 0,
                new ModelResourceLocation("thaumicattempts:infusion_requester", "inventory")
        );

        // предметы-«тайлы»
        registerItemModel(TABlocks.MIRROR_MANAGER_ITEM);
        attachTileRenderer(TABlocks.MIRROR_MANAGER_ITEM, TileMirrorManager::new);
        registerItemModel(TABlocks.ORDER_TERMINAL_ITEM);
        registerItemModel(TABlocks.PATTERN_REQUESTER_ITEM);
        registerItemModel(TABlocks.RESOURCE_REQUESTER_ITEM);
        attachTileRenderer(TABlocks.RESOURCE_REQUESTER_ITEM, TileResourceRequester::new);
        registerItemModel(TABlocks.INFUSION_REQUESTER_ITEM);
        attachTileRenderer(TABlocks.INFUSION_REQUESTER_ITEM, TileInfusionRequester::new);
        registerItemModel(TABlocks.GOLEM_DISPATCHER_ITEM);
        attachTileRenderer(TABlocks.GOLEM_DISPATCHER_ITEM, TileGolemDispatcher::new);
        /* ---------- StateMappers (рендер БЛОКА в мире) ---------- */

        // наши крафтеры: игнорируем таумовский ENABLED (если присутствует)
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.GOLEM_CRAFTER,
                new StateMap.Builder()
                        .ignore(thaumcraft.common.blocks.IBlockEnabled.ENABLED)
                        .build()
        );
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.ARCANE_CRAFTER,
                new StateMap.Builder()
                        .ignore(thaumcraft.common.blocks.IBlockEnabled.ENABLED)
                        .build()
        );

        // ВАЖНО: для ядра и стабилизатора игнорируем ТОЛЬКО наше свойство ACTIVATOR.
        // Свойство active остаётся — оно выбирает модель (active=true/false).
        // СТАБИЛИЗАТОР ЗЕРКАЛ
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.MIRROR_STABILIZER,
                (new StateMap.Builder()).ignore(BlockMirrorStabilizer.SIG).build()
        );

// МАТ. ЯДРО
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.MATH_CORE,
                (new StateMap.Builder()).ignore(BlockMathCore.SIG).build()
        );
        // Таумовское «ухо»: у блока нет ENABLED, мапим все состояния на готовый ресурс.
        ModelLoader.setCustomStateMapper(
                ModBlocksItems.EAR_BAND,
                new StateMapperBase() {
                    @Override
                    protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                        // Thaumcraft сам разрулит варианты через свой blockstate
                        return new ModelResourceLocation("thaumcraft:arcane_ear");
                    }
                }
        );

        /* ---------- TESR ---------- */
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileMirrorManager.class,
                new RenderMirrorManagerGeo());
        ClientRegistry.bindTileEntitySpecialRenderer(
                TilePatternRequester.class,
                new RenderPatternRequesterGeo()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileResourceRequester.class,
                new RenderResourceRequester()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                    TileGolemDispatcher.class,
                    new DispatcherRenderer()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TileInfusionRequester.class,
                new RenderInfusionRequester()
        );
        ClientRegistry.bindTileEntitySpecialRenderer(
                TilePillar.class,
                new RenderPillar()
        );
    }




    private static void registerItemBlockModel(net.minecraft.block.Block block, String path) {
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(block), 0,
                new ModelResourceLocation(path, "inventory")
        );
    }

    private static void registerItemModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(
                item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory")
        );
    }

    private static void attachTileRenderer(Item item, Supplier<? extends net.minecraft.tileentity.TileEntity> factory) {
        item.setTileEntityItemStackRenderer(new TileItemStackRenderer<>(factory));
    }
}
