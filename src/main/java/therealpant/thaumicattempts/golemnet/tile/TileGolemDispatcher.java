package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import thaumcraft.common.golems.EntityThaumcraftGolem;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Tile for the golem dispatcher helper block.
 */
public class TileGolemDispatcher extends TileEntity implements ITickable {

    private static final String TAG_MANAGER = "manager";
    private static final String TAG_GOLEMS = "golems";
    private static final String TAG_GOLEM_ID = "id";
    private static final String TAG_BUSY = "busy";

    @Nullable
    private BlockPos managerPos = null;
    private final LinkedHashSet<UUID> boundGolems = new LinkedHashSet<>();
    private int busyGolems = 0;
    private int tickCounter = 0;

    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos pos) {
        BlockPos newPos = pos == null ? null : pos.toImmutable();
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

    public void clearManagerPosFromManager(BlockPos pos) {
        if (pos != null && pos.equals(managerPos)) {
            managerPos = null;
            busyGolems = 0;
            markDirty();
        }
    }

    @Nullable
    private TileMirrorManager getManager() {
        if (world == null || managerPos == null) return null;
        TileEntity te = world.getTileEntity(managerPos);
        return (te instanceof TileMirrorManager) ? (TileMirrorManager) te : null;
    }

    public int getBoundGolemCount() {
        return boundGolems.size();
    }

    public int getBusyGolemCount() {
        return busyGolems;
    }

    public void setBusyFromManager(int busy) {
        busy = Math.max(0, Math.min(boundGolems.size(), busy));
        if (busyGolems != busy) {
            busyGolems = busy;
            markDirty();
        }
    }

    public boolean tryBindGolem(EntityThaumcraftGolem golem) {
        if (world == null || world.isRemote) return false;
        if (golem == null || golem.isDead) return false;
        if (managerPos == null) return false;
        UUID id = golem.getUniqueID();
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
        markDirty();
    }

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
                return;
            }
            if (!mgr.tryBindDispatcher(this.pos, boundGolems.size())) {
                managerPos = null;
                busyGolems = 0;
                markDirty();
            } else {
                mgr.onDispatcherSetGolemCount(this.pos, boundGolems.size());
            }
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++tickCounter % 32 != 0) return;

        boolean changed = false;
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
            markDirty();
        }
    }

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
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        managerPos = compound.hasKey(TAG_MANAGER) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER)) : null;
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
    }
}