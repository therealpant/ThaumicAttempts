package therealpant.thaumicattempts.proxy;

import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.*;
import therealpant.thaumicattempts.client.render.RenderOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;

public class ClientProxy extends CommonProxy {
    // [Клиент] — перенесли регистрацию моделей в отдельный подписчик (ClientModels)
    @Override public void init(FMLInitializationEvent e) {}
    @Override public void postInit(FMLPostInitializationEvent e) {}

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);
        ClientRegistry.bindTileEntitySpecialRenderer(TileOrderTerminal.class, new RenderOrderTerminal());
    }

}