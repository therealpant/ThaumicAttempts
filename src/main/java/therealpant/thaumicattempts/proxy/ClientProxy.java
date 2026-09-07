package therealpant.thaumicattempts.proxy;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.client.fx.other.FXEssentiaStream;
import therealpant.thaumicattempts.client.ClientCatalogCache;
import therealpant.thaumicattempts.client.OptifineCompat;
import therealpant.thaumicattempts.client.AnomalyArchiveInteractionClient;
import therealpant.thaumicattempts.client.RiftonomiconNavigationClient;
import therealpant.thaumicattempts.client.RunicWardClientState;
import therealpant.thaumicattempts.client.RunicWardHud;
import therealpant.thaumicattempts.client.render.RenderOrderTerminal;
import therealpant.thaumicattempts.client.render.RenderGuidingFirefly;
import therealpant.thaumicattempts.data.research.TAAspects;
import therealpant.thaumicattempts.golemnet.net.msg.S2CFlyAnim;
import therealpant.thaumicattempts.golemnet.net.msg.S2CPatternRequesterAnim;
import therealpant.thaumicattempts.golemnet.net.msg.S2C_CatalogPage;
import therealpant.thaumicattempts.golemnet.net.msg.S2C_DraftSnapshot;
import therealpant.thaumicattempts.golemnet.net.msg.S2C_SnapshotCreated;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;
import therealpant.thaumicattempts.net.msg.S2C_AmberCountUpdate;
import therealpant.thaumicattempts.net.msg.S2C_MonolithParadoxumStream;
import therealpant.thaumicattempts.net.msg.S2C_RunicWardState;
import therealpant.thaumicattempts.net.msg.S2C_ParallelEssentiaStreams;
import therealpant.thaumicattempts.util.TAGemCountCache;
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
    public void handleClientPacket(IMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> handleClientPacketOnMainThread(message));
    }

    private void handleClientPacketOnMainThread(IMessage message) {
        Minecraft minecraft = Minecraft.getMinecraft();

        if (message instanceof S2C_AmberCountUpdate) {
            S2C_AmberCountUpdate packet = (S2C_AmberCountUpdate) message;
            TAGemCountCache.setClientAmberCount(packet.getPlayerId(), packet.getCount());
            return;
        }
        if (message instanceof S2C_MonolithParadoxumStream) {
            S2C_MonolithParadoxumStream packet = (S2C_MonolithParadoxumStream) message;
            if (minecraft.world != null) {
                spawnMonolithParadoxumStream(minecraft.world, packet.getSource(), packet.getTarget());
            }
            return;
        }
        if (message instanceof S2C_RunicWardState) {
            S2C_RunicWardState packet = (S2C_RunicWardState) message;
            RunicWardClientState.update(packet.isActive(), packet.getCurrent(), packet.getMax(), packet.getFortification());
            return;
        }
        if (message instanceof S2C_ParallelEssentiaStreams) {
            S2C_ParallelEssentiaStreams packet = (S2C_ParallelEssentiaStreams) message;
            if (minecraft.world == null) return;
            int tick = (int) minecraft.world.getTotalWorldTime();
            for (S2C_ParallelEssentiaStreams.Stream stream : packet.getStreams()) {
                FXDispatcher.INSTANCE.essentiaTrailFx(
                        stream.getSource(), packet.getMatrix().down(), tick,
                        stream.getColor(), 0.1F, stream.getLength());
            }
            return;
        }
        if (message instanceof S2C_DraftSnapshot) {
            S2C_DraftSnapshot packet = (S2C_DraftSnapshot) message;
            ClientCatalogCache.setDraft(packet.isCraftTab(), packet.getDraftStacks(), packet.getDraftCounts());
            ClientCatalogCache.setPending(packet.isCraftTab(), packet.getPendingStacks(), packet.getPendingCounts());
            return;
        }
        if (message instanceof S2C_CatalogPage) {
            S2C_CatalogPage packet = (S2C_CatalogPage) message;
            int nonEmpty = 0;
            if (packet.getItems35() != null) {
                for (int i = 0; i < Math.min(35, packet.getItems35().size()); i++) {
                    if (packet.getItems35().get(i) != null && !packet.getItems35().get(i).isEmpty()) nonEmpty++;
                }
            }
            ClientCatalogCache.applyPagePacket(
                    packet.isCraftTab(), packet.getSnapshotId(),
                    Math.max(1, packet.getPageNo1()), Math.max(0, packet.getTotalPages()), packet.hasMore(),
                    packet.getItems35(), packet.getCounts(), packet.getMakeCounts(), packet.getMakePossible(),
                    nonEmpty, false);
            return;
        }
        if (message instanceof S2CFlyAnim) {
            S2CFlyAnim packet = (S2CFlyAnim) message;
            if (minecraft.world == null) return;
            TileEntity tile = minecraft.world.getTileEntity(packet.managerPos);
            if (tile instanceof TileMirrorManager) {
                ((TileMirrorManager) tile).clientAddFlying(
                        packet.stack, packet.ring, packet.slot, packet.srcRing, packet.srcSlot,
                        packet.mode, packet.duration, packet.seed, packet.delay);
            }
            return;
        }
        if (message instanceof S2CPatternRequesterAnim) {
            S2CPatternRequesterAnim packet = (S2CPatternRequesterAnim) message;
            if (minecraft.world == null) return;
            TileEntity tile = minecraft.world.getTileEntity(packet.requesterPos);
            if (tile instanceof TilePatternRequester) {
                ((TilePatternRequester) tile).clientAddFlying(
                        packet.stack, packet.mode, packet.duration, packet.seed, packet.delay);
            }
            return;
        }
        if (message instanceof S2C_SnapshotCreated) {
            S2C_SnapshotCreated packet = (S2C_SnapshotCreated) message;
            ClientCatalogCache.onSnapshotCreated(packet.isCraftTab(), packet.getSnapshotId());
            ClientCatalogCache.guiOpened(packet.isCraftTab());
        }
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
