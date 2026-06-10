package therealpant.thaumicattempts.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntityFluxRift;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class RiftClosureDropHandler {
    private static final int OVERWORLD = 0;
    private static final int NETHER = -1;
    private static final int END = 1;
    private static final int REWARD_MEMORY_TICKS = 20 * 60;
    private static final Map<UUID, Long> rewardedRifts = new HashMap<>();

    private RiftClosureDropHandler() {}

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        if (!(event.getEntity() instanceof EntityItem)) return;

        EntityItem itemEntity = (EntityItem) event.getEntity();
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty() || stack.getItem() != ItemsTC.voidSeed) return;
        if (itemEntity.getThrower() != null && !itemEntity.getThrower().isEmpty()) return;

        cleanupRewardMemory(world.getTotalWorldTime());

        EntityFluxRift rift = findClosingRift(world, itemEntity);
        if (rift == null) return;

        UUID id = rift.getUniqueID();
        if (rewardedRifts.containsKey(id)) return;
        rewardedRifts.put(id, world.getTotalWorldTime());

        Item reward = rewardForDimension(world.provider.getDimension());
        if (reward == null) return;

        int count = rollRewardCount(world);
        if (count <= 0) return;

        EntityItem rewardEntity = new EntityItem(
                world,
                itemEntity.posX,
                itemEntity.posY,
                itemEntity.posZ,
                new ItemStack(reward, count)
        );
        rewardEntity.motionX = itemEntity.motionX + (world.rand.nextDouble() - 0.5D) * 0.08D;
        rewardEntity.motionY = itemEntity.motionY + 0.08D;
        rewardEntity.motionZ = itemEntity.motionZ + (world.rand.nextDouble() - 0.5D) * 0.08D;
        rewardEntity.setDefaultPickupDelay();
        world.spawnEntity(rewardEntity);
    }

    private static EntityFluxRift findClosingRift(World world, Entity itemEntity) {
        AxisAlignedBB search = itemEntity.getEntityBoundingBox().grow(4.0D);
        List<EntityFluxRift> rifts = world.getEntitiesWithinAABB(EntityFluxRift.class, search);
        EntityFluxRift best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityFluxRift rift : rifts) {
            if (rift == null || rift.isDead) continue;
            if (!rift.getCollapse() || rift.getRiftSize() > 1) continue;
            double dist = rift.getDistanceSq(itemEntity);
            if (dist < bestDist) {
                best = rift;
                bestDist = dist;
            }
        }
        return best;
    }

    private static Item rewardForDimension(int dimension) {
        if (dimension == OVERWORLD) return ModBlocksItems.RIFT_FLOWER;
        if (dimension == NETHER) return ModBlocksItems.RIFT_STONE;
        if (dimension == END) return ModBlocksItems.RIFT_CRISTAL;
        return null;
    }

    private static int rollRewardCount(World world) {
        int roll = world.rand.nextInt(100);
        if (roll < 50) return 0;
        if (roll < 80) return 1;
        if (roll < 95) return 2;
        return 3;
    }

    private static void cleanupRewardMemory(long now) {
        rewardedRifts.entrySet().removeIf(e -> now - e.getValue() > REWARD_MEMORY_TICKS);
    }
}
