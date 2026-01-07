package therealpant.thaumicattempts.world.tile;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

public interface AnomalyLinkedTile {
    void setAnomalyLink(@Nullable UUID anomalyId, @Nullable BlockPos seedPos);

    @Nullable
    UUID getAnomalyId();

    @Nullable
    BlockPos getSeedPos();
}