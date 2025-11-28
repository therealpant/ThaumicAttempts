package therealpant.thaumicattempts;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib3.GeckoLib;
import thaumcraft.api.ThaumcraftApi;
import therealpant.thaumicattempts.client.gui.GuiHandler;
import therealpant.thaumicattempts.data.TAAlchemyRecipes;
import therealpant.thaumicattempts.data.TAInfusionRecipes;

import therealpant.thaumicattempts.data.research.TAResearchAddenda;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;
import therealpant.thaumicattempts.golemcraft.tile.TileArcaneEarBand;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.golemnet.net.msg.*;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.proxy.CommonProxy;
import therealpant.thaumicattempts.util.ThaumcraftProvisionHelper;
// S2C
// C2S


@Mod(modid = ThaumicAttempts.MODID, name = "Thaumic Attempts", version = "0.1.0")
public class ThaumicAttempts {

    public static final String MODID = "thaumicattempts";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance
    public static ThaumicAttempts INSTANCE;

    @SidedProxy(
            clientSide = "therealpant.thaumicattempts.proxy.ClientProxy",
            serverSide = "therealpant.thaumicattempts.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    /** Наш сетевой канал — то самое NET, которое было красным */
    public static SimpleNetworkWrapper NET;

    /** Креатив-вкладка мода */
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

        GeckoLib.initialize();

        // 1) Сетевой канал + регистрация пакетов
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
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

        //Research
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemcraft"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemcraft_advanced"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemintegration"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golemmirrors"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golem_delivery"));
        ThaumcraftApi.registerResearchLocation(new ResourceLocation(ThaumicAttempts.MODID, "research/golem_controling"));

        MinecraftForge.EVENT_BUS.register(ThaumcraftProvisionHelper.class);
        // 3) Прокси preInit (если нужно)
        proxy.preInit(e);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        // GUI-handler — нужен один раз и один класс (твой общий GuiHandler)
        NetworkRegistry.INSTANCE.registerGuiHandler(ThaumicAttempts.INSTANCE, new GuiHandler());

        proxy.init(e);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent e) {

        TAInfusionRecipes.register();
        TAAlchemyRecipes.register();
        TAResearchAddenda.injectEldritchVoidTileAddendum();
        TAResearchAddenda.injectArcaneEarAddendum();

        proxy.postInit(e);
    }

    /** Регистрация всех S2C/C2S сообщений с уникальными ID */
    private void registerPackets() {
        int id = 0;

        // C2S
        NET.registerMessage(C2S_OrderAdjust.Handler.class,        C2S_OrderAdjust.class,        id++, Side.SERVER);
        NET.registerMessage(C2S_OrderSubmit.Handler.class,        C2S_OrderSubmit.class,        id++, Side.SERVER);
        NET.registerMessage(C2S_RequestCatalogPage.Handler.class, C2S_RequestCatalogPage.class, id++, Side.SERVER);

        // S2C
        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot.class, id++, Side.CLIENT);

        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage.class,   id++, Side.CLIENT);

        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim.class,        id++, Side.CLIENT);

        NET.registerMessage(therealpant.thaumicattempts.golemnet.net.msg.S2C_SnapshotCreated.Handler.class,
                therealpant.thaumicattempts.golemnet.net.msg.S2C_SnapshotCreated.class, id++, Side.CLIENT);
    }




}
