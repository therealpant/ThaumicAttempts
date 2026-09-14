package therealpant.thaumicattempts.integration.rift;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import thaumcraft.common.entities.EntityFluxRift;
import therealpant.thaumicattempts.world.tile.TileRiftWell;

import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;

/** Common-side hooks: no client classes are loaded on a dedicated server. */
public final class RiftWellShapeHooks {
    private static final Map<EntityFluxRift, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RiftWellShapeHooks() {}

    public static void bind(TileRiftWell well, EntityFluxRift rift) {
        State state = STATES.get(rift);
        if (state == null || !state.wellPos.equals(well.getPos())) {
            STATES.put(rift, new State(well.getPos().toImmutable()));
        }
        double x = well.getPos().getX() + 0.5D;
        double y = well.getPos().getY() + TileRiftWell.RIFT_CENTER_Y_OFFSET;
        double z = well.getPos().getZ() + 0.5D;
        if (rift.posX != x || rift.posY != y || rift.posZ != z) {
            rift.setPosition(x, y, z);
            rift.prevPosX = rift.lastTickPosX = x;
            rift.prevPosY = rift.lastTickPosY = y;
            rift.prevPosZ = rift.lastTickPosZ = z;
        }
        prepare(rift);
    }

    /** Called immediately after TC's setSize(), including network-position updates. */
    public static void afterRebuild(EntityFluxRift rift) {
        State state = findState(rift);
        if (state != null) apply(rift, state);
    }

    /** Also called before vanilla rendering, covering initial client chunk arrival. */
    public static void prepare(Entity entity) {
        if (!(entity instanceof EntityFluxRift)) return;
        EntityFluxRift rift = (EntityFluxRift) entity;
        boolean wasControlled = STATES.containsKey(rift);
        State state = findState(rift);
        if (state == null) {
            if (wasControlled) rift.setRiftSize(rift.getRiftSize());
            return;
        }
        if (state.seed != rift.getRiftSeed() || state.size != rift.getRiftSize()) {
            // Seed updates do not rebuild the point list in vanilla TC6.
            rift.setRiftSize(rift.getRiftSize());
        }
        if (!rift.points.isEmpty() && state.firstPoint != rift.points.get(0)) apply(rift, state);
    }

    public static float renderStability(float vanillaStability, Entity entity) {
        // Freeze TC/TA's geometric wobble and radius pulse for controlled rifts.
        // The portal shader can still animate its texture independently.
        return STATES.containsKey(entity) ? 50.0F : vanillaStability;
    }

    private static State findState(EntityFluxRift rift) {
        if (rift.world == null) return null;
        State state = STATES.get(rift);
        BlockPos pos = state == null
                ? new BlockPos(rift.posX, rift.posY - TileRiftWell.RIFT_CENTER_Y_OFFSET, rift.posZ)
                : state.wellPos;
        if (!rift.world.isBlockLoaded(pos)) return null;
        TileEntity tile = rift.world.getTileEntity(pos);
        if (!(tile instanceof TileRiftWell) || !((TileRiftWell) tile).controlsRift(rift)) {
            STATES.remove(rift);
            return null;
        }
        if (state == null) {
            state = new State(pos);
            STATES.put(rift, state);
        }
        return state;
    }

    private static void apply(EntityFluxRift rift, State state) {
        int count = rift.points.size();
        if (count == 0 || rift.pointsWidth.size() != count) return;
        double[][] original = new double[count][3];
        double[] widths = new double[count];
        for (int i = 0; i < count; i++) {
            Vec3d p = rift.points.get(i);
            original[i] = new double[]{p.x, p.y, p.z};
            widths[i] = rift.pointsWidth.get(i);
        }
        double[][] bent = RiftWellGeometry.bend(original, widths,
                TileRiftWell.RIFT_HORIZONTAL_RADIUS, TileRiftWell.RIFT_VERTICAL_RADIUS,
                rift.getRiftSeed());
        for (int i = 0; i < count; i++) {
            rift.points.set(i, new Vec3d(bent[i][0], bent[i][1], bent[i][2]));
        }
        // Preserve SIZE and pointsWidth. Only the centreline is bent.
        state.firstPoint = rift.points.get(0);
        state.seed = rift.getRiftSeed();
        state.size = rift.getRiftSize();
        double radius = TileRiftWell.RIFT_HORIZONTAL_RADIUS;
        double height = TileRiftWell.RIFT_VERTICAL_RADIUS;
        double top = RiftWellGeometry.upperBound(bent, widths, height);
        rift.width = (float) (radius * 2.0D);
        rift.height = (float) (height + top);
        rift.setEntityBoundingBox(new AxisAlignedBB(rift.posX - radius, rift.posY - height,
                rift.posZ - radius, rift.posX + radius, rift.posY + top, rift.posZ + radius));
    }

    private static final class State {
        private final BlockPos wellPos;
        private Object firstPoint;
        private int seed = Integer.MIN_VALUE;
        private int size = Integer.MIN_VALUE;

        private State(BlockPos wellPos) { this.wellPos = wellPos; }
    }
}
