package therealpant.thaumicattempts.integration.rift;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RiftWellArea {
    private RiftWellArea() {}
    public static <T extends TileEntity> List<T> nearby(World world, BlockPos origin, int radius, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                if (!world.isBlockLoaded(new BlockPos(x << 4, origin.getY(), z << 4))) continue;
                for (TileEntity tile : world.getChunk(x, z).getTileEntityMap().values()) {
                    if (type.isInstance(tile) && !tile.isInvalid() && tile.getPos().distanceSq(origin) <= radius * radius)
                        result.add(type.cast(tile));
                }
            }
        }
        result.sort(Comparator.comparingDouble((T tile) -> tile.getPos().distanceSq(origin))
                .thenComparingLong(tile -> tile.getPos().toLong()));
        return result;
    }
}
