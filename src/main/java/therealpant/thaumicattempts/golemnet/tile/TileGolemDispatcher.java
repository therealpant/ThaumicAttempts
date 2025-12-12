package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thaumcraft.common.golems.EntityThaumcraftGolem;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Tile for the golem dispatcher helper block.
 * Хранит список привязанных големов и ссылку на MirrorManager.
 * Менеджер через него знает, какие golemUUID можно использовать
 * для "форсированных" provisioning-тасков.
 */
public class TileGolemDispatcher extends TileEntity implements ITickable, IAnimatable {

    private static final String TAG_MANAGER    = "manager";
    private static final String TAG_GOLEMS     = "golems";
    private static final String TAG_GOLEM_ID   = "id";
    private static final String TAG_BUSY       = "busy";
    private static final String TAG_SEAL_COLOR = "sealColor";

    private boolean prevWaitingFlag = false;

    private int animLogicPhase = 0;

    @Nullable
    private BlockPos managerPos = null;

    // Множество привязанных големов
    private final LinkedHashSet<UUID> boundGolems = new LinkedHashSet<>();

    // Количество "занятых" слотов — задаётся менеджером (сколько големов в рейсах)
    private int busyGolems = 0;

    // Цвет канала печати для курьерских задач (0..15), -1 = не задан
    private int sealColor = -1;

    private int tickCounter = 0;

    // Цвет по умолчанию для курьеров (если менеджер не задал свой)
    public static final int COURIER_COLOR = 14;

    /* ====== поля для визуалки / рандома ====== */

    // скорость анимации (множитель)
    public float animSpeed = 0.625f;
    // фиксированный сид для выбора кубиков/осей
    public long animSeed = 2L;

    // выбор на текущий цикл
    public int[] picks = {0, 1, 2}; // индексы костей 0..7
    public int[] axes  = {0, 1, 2}; // 0=X,1=Y,2=Z
    public long  cycleIdx = -1;     // номер текущего цикла

    // тайминги (в тиках)
    public static final int DUR_A = 20; // длительность поворота 180°
    public static final int GAP   =  4; // пауза между фазами


    /** выбрать 3 разных индекса [0..7] и 3 случайные оси для нового цикла */
    public void reseedForCycle(long newIdx) {
        Random r = new Random(animSeed ^ (newIdx * 0x9E3779B97F4A7C15L));
        int[] a = {0,1,2,3,4,5,6,7};
        for (int i = a.length - 1; i > 0; --i) {
            int j = r.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        picks[0] = a[0];
        picks[1] = a[1];
        picks[2] = a[2];

        axes[0] = r.nextInt(3);
        axes[1] = r.nextInt(3);
        axes[2] = r.nextInt(3);

        cycleIdx = newIdx;
    }

    /* ===================== API ===================== */

    public java.util.Set<UUID> getBoundGolemsSnapshot() {
        return new java.util.LinkedHashSet<>(boundGolems);
    }

    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public boolean hasBoundGolems() {
        return !boundGolems.isEmpty();
    }

    public boolean isGolemBound(UUID id) {
        return id != null && boundGolems.contains(id);
    }

    public int getBoundGolemCount() {
        return boundGolems.size();
    }

    public int getBusyGolemCount() {
        return busyGolems;
    }

    /**
     * Цвет канала, который должен использоваться provisioning-тасками
     * под этих курьеров.
     */
    public int getSealColor() {
        if (sealColor < 0) {
            return COURIER_COLOR;
        }
        return sealColor & 15;
    }

    /**
     * Вызывается менеджером для синхронизации цвета.
     */
    public void setSealColor(int color) {
        color &= 15;
        if (this.sealColor != color) {
            this.sealColor = color;
            markDirty();
            if (world != null && !world.isRemote) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
    }

    /**
     * Установить/сменить привязку к менеджеру.
     * При смене — отписываемся от старого.
     */
    public void setManagerPos(@Nullable BlockPos pos) {
        BlockPos newPos = (pos == null) ? null : pos.toImmutable();
        if (Objects.equals(managerPos, newPos)) return;

        if (world != null && !world.isRemote) {
            detachFromManager();
            managerPos = newPos;
            if (managerPos != null) {
                TileMirrorManager mgr = getManager();
                if (mgr == null || !mgr.tryBindDispatcher(this.pos, boundGolems.size())) {
                    managerPos = null;
                } else {
                    mgr.onDispatcherSetGolemCount(this.pos, boundGolems.size());
                }
            }
            markDirty();
        } else {
            managerPos = newPos;
        }
    }

    /**
     * Вызывается менеджером, когда он решает отвязать этот диспетчер.
     */
    public void clearManagerPosFromManager(BlockPos manager) {
        if (manager != null && manager.equals(this.managerPos)) {
            this.managerPos = null;
            this.busyGolems = 0;
            this.sealColor = -1;
            markDirty();
        }
    }

    @Nullable
    private TileMirrorManager getManager() {
        if (world == null || managerPos == null) return null;
        TileEntity te = world.getTileEntity(managerPos);
        return (te instanceof TileMirrorManager) ? (TileMirrorManager) te : null;
    }

    public void setBusyFromManager(int busy) {
        busy = Math.max(0, Math.min(boundGolems.size(), busy));
        if (busyGolems != busy) {
            busyGolems = busy;
            markDirty();
        }
    }

    /**
     * Привязать голема к этому диспетчеру (и через него — к менеджеру).
     */
    public boolean tryBindGolem(EntityThaumcraftGolem golem) {
        if (world == null || world.isRemote) return false;
        if (golem == null || golem.isDead) return false;
        if (managerPos == null) return false;

        UUID id = golem.getUniqueID();
        if (id == null) return false;

        if (boundGolems.contains(id)) return true;

        TileMirrorManager mgr = getManager();
        if (mgr == null) return false;

        int current = boundGolems.size();
        if (!mgr.onDispatcherSetGolemCount(this.pos, current + 1)) {
            return false;
        }

        boundGolems.add(id);
        markDirty();
        return true;
    }

    private void detachFromManager() {
        if (world == null || world.isRemote) return;
        if (managerPos == null) return;

        TileEntity te = world.getTileEntity(managerPos);
        if (te instanceof TileMirrorManager) {
            ((TileMirrorManager) te).unregisterDispatcher(this.pos);
        }

        managerPos = null;
        busyGolems = 0;
        sealColor = -1;
        markDirty();
    }

    /* ===================== Жизненный цикл ===================== */

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) {
            detachFromManager();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote && managerPos != null) {
            TileMirrorManager mgr = getManager();
            if (mgr == null) {
                managerPos = null;
                busyGolems = 0;
                markDirty();
            } else {
                if (!mgr.tryBindDispatcher(this.pos, boundGolems.size())) {
                    managerPos = null;
                    busyGolems = 0;
                    markDirty();
                } else {
                    mgr.onDispatcherSetGolemCount(this.pos, boundGolems.size());
                }
            }
            if (world != null && !world.isRemote && animSeed == 0L) {
                animSeed = world.rand.nextLong() ^ getPos().toLong();
                markDirty();
            }
        }
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return net.minecraft.tileentity.TileEntity.INFINITE_EXTENT_AABB;
    }

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean isGlobalRenderer() {
        return true; // форсит вызов TESR даже вне стандартного ббокса/фрустума
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 4096D; // ~64 блока
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
                             net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    /* ========== Geckolib: фабрика и контроллеры ========== */

    private final AnimationFactory factory = new AnimationFactory(this);

    @Override
    public void registerControllers(AnimationData data) {
        // Базовый контроллер, который вечно крутит animation.model.cycle_base из dispatcher.animation.json
        AnimationController<TileGolemDispatcher> baseController =
                new AnimationController<>(this, "base_cycle", 0, this::baseCyclePredicate);

        data.addAnimationController(baseController);
    }

    /**
     * Предикат для базовой анимации из dispatcher.animation.json.
     * Здесь запускаем "animation.model.cycle_base" в цикле.
     */
    private <E extends IAnimatable> PlayState baseCyclePredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder()
                        .addAnimation("animation.model.cycle_base", true) // true = LOOP
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    /* ===================== Tick ===================== */

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++tickCounter % 32 != 0) return;

        boolean changed = false;

        // Чистим мёртвых/пропавших големов
        Iterator<UUID> it = boundGolems.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity ent = ((WorldServer) world).getEntityFromUuid(id);
            if (!(ent instanceof EntityThaumcraftGolem) || ent.isDead) {
                it.remove();
                changed = true;
            }
        }

        if (changed) {
            TileMirrorManager mgr = getManager();
            if (mgr != null) {
                mgr.onDispatcherSetGolemCount(this.pos, boundGolems.size());
            }
            if (busyGolems > boundGolems.size()) {
                busyGolems = boundGolems.size();
            }
            markDirty();
        }
    }

    /* ===================== NBT ===================== */

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        if (managerPos != null) {
            compound.setLong(TAG_MANAGER, managerPos.toLong());
        }

        NBTTagList list = new NBTTagList();
        for (UUID id : boundGolems) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId(TAG_GOLEM_ID, id);
            list.appendTag(entry);
        }
        compound.setTag(TAG_GOLEMS, list);

        compound.setInteger(TAG_BUSY, busyGolems);
        compound.setInteger(TAG_SEAL_COLOR, getSealColor());
        compound.setFloat("animSpeed", animSpeed);
        compound.setLong("animSeed", animSeed);

        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        managerPos = compound.hasKey(TAG_MANAGER)
                ? BlockPos.fromLong(compound.getLong(TAG_MANAGER))
                : null;

        boundGolems.clear();
        if (compound.hasKey(TAG_GOLEMS, 9)) {
            NBTTagList list = compound.getTagList(TAG_GOLEMS, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                if (entry.hasUniqueId(TAG_GOLEM_ID)) {
                    boundGolems.add(entry.getUniqueId(TAG_GOLEM_ID));
                }
            }
        }

        busyGolems = Math.max(0, Math.min(boundGolems.size(), compound.getInteger(TAG_BUSY)));

        if (compound.hasKey("animSpeed")) animSpeed = compound.getFloat("animSpeed");
        if (compound.hasKey("animSeed"))  animSeed  = compound.getLong("animSeed");

        if (compound.hasKey(TAG_SEAL_COLOR)) {
            sealColor = compound.getInteger(TAG_SEAL_COLOR) & 15;
        } else {
            sealColor = -1;
        }
    }

    public static long calcCycleLenForSpeed(float animSpeed) {
        float sp = Math.max(0.05f, animSpeed);
        int base = DUR_A + GAP;
        long aLen = Math.max(1, Math.round(base / sp));
        return aLen * 4L;
    }

    public long cycleLen() {
        return calcCycleLenForSpeed(this.animSpeed);
    }

}
