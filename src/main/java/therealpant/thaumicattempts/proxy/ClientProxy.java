package therealpant.thaumicattempts.proxy;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.*;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.other.FXEssentiaStream;
import therealpant.thaumicattempts.client.OptifineCompat;
import therealpant.thaumicattempts.client.AnomalyArchiveInteractionClient;
import therealpant.thaumicattempts.client.RiftonomiconNavigationClient;
import therealpant.thaumicattempts.client.RunicWardHud;
import therealpant.thaumicattempts.client.render.RenderOrderTerminal;
import therealpant.thaumicattempts.client.render.RenderGuidingFirefly;
import therealpant.thaumicattempts.data.research.TAAspects;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.world.EntityGuidingFirefly;

public class ClientProxy extends CommonProxy {
    private int monolithStreamSequence;

    // [Клиент] — перенесли регистрацию моделей в отдельный подписчик (ClientModels)
    @Override public void init(FMLInitializationEvent e) {}
    @Override public void postInit(FMLPostInitializationEvent e) {}

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);
        OptifineCompat.ensureSafeSettings();
        MinecraftForge.EVENT_BUS.register(AnomalyArchiveInteractionClient.class);
        MinecraftForge.EVENT_BUS.register(RiftonomiconNavigationClient.class);
        MinecraftForge.EVENT_BUS.register(new RunicWardHud());
        ClientRegistry.bindTileEntitySpecialRenderer(TileOrderTerminal.class, new RenderOrderTerminal());
        RenderingRegistry.registerEntityRenderingHandler(
                EntityGuidingFirefly.class,
                RenderGuidingFirefly::new);
    }

    @Override
    public void spawnMonolithParadoxumStream(World world, BlockPos source, BlockPos target) {
        if (world == null || source == null || target == null) return;
        int color = TAAspects.PARADOXUM == null
                ? 0x020A3A
                : TAAspects.PARADOXUM.getColor();

        /*
         * FXEssentiaStream merges active streams by source block, target block
         * and constructor colour. A unique internal colour keeps every
         * monolith dispatch in its own entry; the visible colour is restored
         * immediately after construction.
         */
        int streamKeyColor = (color ^ nextMonolithStreamSequence()) & 0x00FFFFFF;
        FXEssentiaStream stream = new FXEssentiaStream(
                world,
                source.getX() + 0.5D,
                source.getY() + 9.5D / 16.0D,
                source.getZ() + 0.5D,
                target.getX() + 0.5D,
                target.getY() + 1.5D,
                target.getZ() + 0.5D,
                (int) world.getTotalWorldTime(),
                streamKeyColor,
                0.1F,
                1,
                0.0D);
        stream.setRBGColorF(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F);
        ParticleEngine.addEffect(world, stream);
    }

    private int nextMonolithStreamSequence() {
        monolithStreamSequence = (monolithStreamSequence + 1) & 0x00FFFFFF;
        if (monolithStreamSequence == 0) monolithStreamSequence = 1;
        return monolithStreamSequence;
    }

}
