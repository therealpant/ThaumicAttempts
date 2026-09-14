package therealpant.thaumicattempts.world.tile.automation;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public interface ParadoxPedestalAutomationHost {
    int getAutomationSlotCount();

    ItemStack getAutomationStack(int slot);

    boolean canAcceptAutomatedStack(ItemStack stack);

    boolean isAutomatedStackComplete(ItemStack stack);

    boolean insertAutomatedStack(int slot, ItemStack stack);

    ItemStack extractAutomatedStack(int slot);

    Vec3d getAutomationSlotPosition(int slot);

    void onAutomationChanged();

    boolean hasPedestalReservation(BlockPos pedestalPos);
}
