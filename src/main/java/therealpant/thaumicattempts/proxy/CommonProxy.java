package therealpant.thaumicattempts.proxy;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class CommonProxy {
    // [Сервер/общая логика] — пока ничего не требуется
    public void preInit(FMLPreInitializationEvent e) {}
    public void init(FMLInitializationEvent e) {}
    public void postInit(FMLPostInitializationEvent e) {}

    public void spawnMonolithParadoxumStream(World world, BlockPos source, BlockPos target) {}

    public void handleClientPacket(IMessage message) {}

}
