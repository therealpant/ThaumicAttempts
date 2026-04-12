package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Общая точка постановки automation-заказов по терминальным иконкам.
 * Используется терминалом и прочими сетевыми клиентами (например, пьедесталом).
 */
public final class AutomationOrderSubmitHelper {

    private AutomationOrderSubmitHelper() {
    }

    public static int submitAutomationOrderByIcon(@Nullable World world,
                                                  @Nullable ItemStack orderIcon,
                                                  int items,
                                                  @Nullable BlockPos managerPos,
                                                  @Nullable BlockPos destination,
                                                  int destSide) {
        if (world == null || world.isRemote || orderIcon == null || orderIcon.isEmpty() || items <= 0) return 0;
        if (!TerminalOrderApi.isOrderIcon(orderIcon)) return 0;

        BlockPos targetPos = TerminalOrderApi.getOrderIconPos(orderIcon);
        int slot = TerminalOrderApi.getOrderIconSlot(orderIcon);
        if (targetPos == null || slot < 0) return 0;

        TileEntity te = world.getTileEntity(targetPos);
        if (!(te instanceof IAutomationOrderAcceptor)) return 0;

        return Math.max(0, ((IAutomationOrderAcceptor) te).submitAutomationOrder(
                slot,
                items,
                managerPos,
                destination,
                destSide
        ));
    }
}