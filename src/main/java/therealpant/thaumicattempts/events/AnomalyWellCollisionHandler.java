package therealpant.thaumicattempts.events;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.world.EntityAnomalyWellResult;
import therealpant.thaumicattempts.world.block.BlockAnomalyWell;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class AnomalyWellCollisionHandler {
    private AnomalyWellCollisionHandler() {
    }

    @SubscribeEvent
    public static void onGetCollisionBoxes(GetCollisionBoxesEvent event) {
        if (event.getEntity() instanceof EntityAnomalyWellResult) {
            return;
        }
        World world = event.getWorld();
        AxisAlignedBB query = event.getAabb();
        if (world == null || query == null || TABlocks.ANOMALY_WELL == null) {
            return;
        }

        int minX = MathHelper.floor(query.minX) - 2;
        int maxX = MathHelper.floor(query.maxX) + 2;
        int minY = MathHelper.floor(query.minY) - 1;
        int maxY = MathHelper.floor(query.maxY) + 1;
        int minZ = MathHelper.floor(query.minZ) - 2;
        int maxZ = MathHelper.floor(query.maxZ) + 2;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.setPos(x, y, z);
                    if (world.getBlockState(cursor).getBlock() == TABlocks.ANOMALY_WELL) {
                        BlockAnomalyWell.addExtendedCollisionBoxes(
                                cursor.toImmutable(), query, event.getCollisionBoxesList());
                    }
                }
            }
        }
    }
}
