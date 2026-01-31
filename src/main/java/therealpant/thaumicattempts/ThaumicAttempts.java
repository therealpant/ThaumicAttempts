package therealpant.thaumicattempts;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib3.GeckoLib;
import thaumcraft.api.ThaumcraftApi;
import therealpant.thaumicattempts.client.gui.GuiHandler;
import static therealpant.thaumicattempts.config.TAConfig.ENABLE_ELDRITCH_STONE_RECIPE;

import therealpant.thaumicattempts.command.*;
import therealpant.thaumicattempts.capability.AmberCasterCapability;
import therealpant.thaumicattempts.data.TAAlchemyRecipes;
import therealpant.thaumicattempts.data.TAInfusionEnchantments;
import therealpant.thaumicattempts.data.TAInfusionRecipes;
import therealpant.thaumicattempts.data.research.TAAspects;
import therealpant.thaumicattempts.events.AmberCasterCapabilityHandler;
import therealpant.thaumicattempts.events.TAGemEventHandler;
import therealpant.thaumicattempts.data.research.TAResearchAddenda;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.api.gems.TAGemRegistry;
import therealpant.thaumicattempts.gems.AmberGemDefinition;
import therealpant.thaumicattempts.gems.AmethystGemDefinition;
import therealpant.thaumicattempts.gems.DiamondGemDefinition;
import therealpant.thaumicattempts.common.gems.StubGemDefinition;
import therealpant.thaumicattempts.golemcraft.tile.TileArcaneEarBand;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.golemnet.net.msg.C2S_OrderAdjust;
import therealpant.thaumicattempts.golemnet.net.msg.C2S_OrderSubmit;
import therealpant.thaumicattempts.golemnet.net.msg.C2S_RequestCatalogPage;
import therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.init.ModEntities;
import therealpant.thaumicattempts.proxy.CommonProxy;
import therealpant.thaumicattempts.tile.TilePillar;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;
// S2C
// C2S


@Mod(modid = ThaumicAttempts.MODID, name = "Thaumic Attempts", version = "0.1.0", dependencies = "required-after:thaumcraft@[6.1.BETA26,);required-after:geckolib3")
public class ThaumicAttempts {

    public static final String MODID = "thaumicattempts";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final SimpleNetworkWrapper NET =
            NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @Mod.Instance
    public static ThaumicAttempts INSTANCE;

    @SidedProxy(
            clientSide = "therealpant.thaumicattempts.proxy.ClientProxy",
            serverSide = "therealpant.thaumicattempts.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    /**
     * Креатив-вкладка мода
     */
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MODID) {
        @Override
        public ItemStack createIcon() {
            // безопасно: иконка появляется в игре после регистрации предметов,
            // а вкладка только рендерится на клиенте; если что — подменишь на ваниль.
            return new ItemStack(ModBlocksItems.CRAFT_PATTERN);
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {

        ModEntities.register();

        GeckoLib.initialize();

        AmberCasterCapability.register();

        TAGemRegistry.register(new StubGemDefinition());
        MinecraftForge.EVENT_BUS.register(new TAGemEventHandler());
        MinecraftForge.EVENT_BUS.register(new AmberCasterCapabilityHandler());

        // РЕГИСТРАЦИЯ ВСЕХ ПАКЕТОВ МОДА
        registerPackets();


        // 2) TileEntities (всё, что уже есть)
        GameRegistry.registerTileEntity(
                TileEntityGolemCrafter.class,
                new ResourceLocation(MODID, "golem_crafter")
        );
        GameRegistry.registerTileEntity(
                TileEntityArcaneCrafter.class,
                new ResourceLocation(MODID, "arcane_crafter")
        );
        GameRegistry.registerTileEntity(
                TileArcaneEarBand.class,
                new ResourceLocation(MODID, "tile_arcane_ear_band")
        );
        GameRegistry.registerTileEntity(
                TileOrderTerminal.class,
                new ResourceLocation(MODID, "order_terminal")
        );
        GameRegistry.registerTileEntity(
                therealpant.thaumicattempts.golemnet.tile.TileGolemDispatcher.class,
                new ResourceLocation(ThaumicAttempts.MODID, "golem_dispatcher")
        );
        GameRegistry.registerTileEntity(TilePillar.class,
                new ResourceLocation(ThaumicAttempts.MODID, "pillar"));

        MinecraftForge.EVENT_BUS.register(ThaumcraftProvisionHelper.class);
        // 3) Прокси preInit (если нужно)
        proxy.preInit(e);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        // GUI-handler — нужен один раз и один класс (твой общий GuiHandler)
        NetworkRegistry.INSTANCE.registerGuiHandler(ThaumicAttempts.INSTANCE, new GuiHandler());

        TAGemRegistry.register(new AmberGemDefinition());
        TAGemRegistry.register(new AmethystGemDefinition());
        TAGemRegistry.register(new DiamondGemDefinition());

        //Research
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemcraft"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemcraft_advanced"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemintegration"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemmirrors"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golem_delivery"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golem_controling"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golem_infusion"));

        proxy.init(e);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent e) {

        TAInfusionRecipes.register();
        TAAlchemyRecipes.register();
        TAMultiblockTriggers.register();
        TAInfusionEnchantments.register();
        TAAspects.register();

        //Addenda
        if (ENABLE_ELDRITCH_STONE_RECIPE)
            TAResearchAddenda.injectEldritchVoidTileAddendum();
        TAResearchAddenda.injectArcaneEarAddendum();

        proxy.postInit(e);
    }

    /**
     * Регистрация всех S2C/C2S сообщений с уникальными ID
     */
    private void registerPackets() {
        int id = 0;

        // C2S
        NET.registerMessage(C2S_OrderAdjust.Handler.class, C2S_OrderAdjust.class, id++, Side.SERVER);
        NET.registerMessage(C2S_OrderSubmit.Handler.class, C2S_OrderSubmit.class, id++, Side.SERVER);
        NET.registerMessage(C2S_RequestCatalogPage.Handler.class, C2S_RequestCatalogPage.class, id++, Side.SERVER);

        // S2C
        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot.class, id++, Side.CLIENT);

        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage.class, id++, Side.CLIENT);

        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim.class, id++, Side.CLIENT);

        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2C_SnapshotCreated.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2C_SnapshotCreated.class, id++, Side.CLIENT);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new CommandSpawnFluxAnomaly());
        e.registerServerCommand(new CommandLocateFluxAnomaly());
        e.registerServerCommand(new CommandFluxStatus());
        e.registerServerCommand(new CommandAnomalyDebug());
        e.registerServerCommand(new CommandAnomalyResetCooldown());
    }

}
