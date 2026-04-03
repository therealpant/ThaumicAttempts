package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import javax.annotation.Nullable;
import java.util.Locale;

public class TileSequentialCraftPlanner extends TileEntity implements ITickable {
    private static final Logger LOG = LogManager.getLogger("ThaumicAttempts/SequentialPlanner");

    public enum PlannerStatus {
        IDLE,
        SCANNING,
        PLANNING,
        FAILED
    }

    private static final String TAG_MANAGER = "Manager";
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_STATUS = "PlannerStatus";
    private static final int RECIPE_INDEX_REFRESH_TICKS = 100;


    @Nullable
    private BlockPos managerPos;
    private boolean active = false;
    private PlannerStatus status = PlannerStatus.IDLE;

    private int lastRecipeRefreshTick = -9999;

    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos managerPos) {
        this.managerPos = managerPos;
        markDirty();
    }

    public void clearManagerPosFromManager(BlockPos pos) {
        if (this.managerPos != null && this.managerPos.equals(pos)) {
            this.managerPos = null;
            this.active = false;
            this.status = PlannerStatus.IDLE;
            markDirty();
        }
    }

    public boolean isActivePlanner() {
        return active;
    }

    public PlannerStatus getStatus() {
        return status;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        TileMirrorManager manager = getManager();
        active = manager != null && manager.tryBindPlanner(pos);
        if (!active || manager == null) {
            status = PlannerStatus.IDLE;
            return;
        }

        final int now = manager.getServerTickCounter();
        if ((now - lastRecipeRefreshTick) >= RECIPE_INDEX_REFRESH_TICKS) {
            status = PlannerStatus.SCANNING;
            manager.refreshRecipeIndexFromPlanner();
            lastRecipeRefreshTick = now;
            LOG.debug("[Planner {}] refreshed logistics recipe index", pos);
        }

        status = PlannerStatus.PLANNING;
        if (!manager.isLogisticsHealthy()) {
            status = PlannerStatus.FAILED;
            LOG.warn("[Planner {}] logistics pipeline is not healthy", pos);
            return;
        }

        status = PlannerStatus.IDLE;
    }

    @Nullable
    private TileMirrorManager getManager() {
        if (world == null || managerPos == null) return null;
        TileEntity te = world.getTileEntity(managerPos);
        return te instanceof TileMirrorManager ? (TileMirrorManager) te : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        if (managerPos != null) tag.setLong(TAG_MANAGER, managerPos.toLong());
        tag.setBoolean(TAG_ACTIVE, active);
        tag.setString(TAG_STATUS, status.name());

        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        managerPos = tag.hasKey(TAG_MANAGER, Constants.NBT.TAG_LONG) ? BlockPos.fromLong(tag.getLong(TAG_MANAGER)) : null;
        active = tag.getBoolean(TAG_ACTIVE);

        String st = tag.getString(TAG_STATUS).toUpperCase(Locale.ROOT);
        try {
            status = PlannerStatus.valueOf(st);
        } catch (IllegalArgumentException ignored) {
            status = PlannerStatus.IDLE;
        }
    }
}