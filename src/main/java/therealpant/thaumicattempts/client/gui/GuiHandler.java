package therealpant.thaumicattempts.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import therealpant.thaumicattempts.golemcraft.container.ContainerCraftPattern;
import therealpant.thaumicattempts.golemcraft.container.ContainerGolemCrafter;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPattern;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPatternInfusion;

import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;

public class GuiHandler implements IGuiHandler {
    public static final int GUI_GOLEM_CRAFTER        = 1;
    public static final int GUI_CRAFT_PATTERN        = 2;
    public static final int GUI_ORDER_TERMINAL       = 42;
    public static final int GUI_ARCANE_PATTERN       = 4;
    public static final int GUI_RESOURCE_REQUESTER   = 5;
    public static final int GUI_DELIVERY_PATTERN     = 6;
    public static final int GUI_DELIVERY_STATION     = 7;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        switch (ID) {
            case GUI_GOLEM_CRAFTER: {
                TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
                if (te instanceof TileEntityGolemCrafter) {
                    return new ContainerGolemCrafter(player.inventory, (TileEntityGolemCrafter) te);
                }
                return null;
            }
            case GUI_CRAFT_PATTERN: {
                // ВСЕГДА сначала проверяем isEmpty(), потом getItem()
                ItemStack stack = findPatternStack(player);
                return (!stack.isEmpty())
                        ? new ContainerCraftPattern(player.inventory, stack)
                        : null;
            }

            case GUI_ORDER_TERMINAL: {
                TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
                if (te instanceof TileOrderTerminal) {
                    return new therealpant.thaumicattempts.golemnet.container.ContainerOrderTerminal(
                            player.inventory,
                            (TileOrderTerminal) te
                    );
                }
                return null;
            }
            case GUI_RESOURCE_REQUESTER: {
                TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
                if (te instanceof TileResourceRequester) {
                    return new therealpant.thaumicattempts.golemnet.container.ContainerResourceRequester(
                            player.inventory,
                            (TileResourceRequester) te
                    );
                }
                return null;
            }
            case GUI_ARCANE_PATTERN: // <-- НОВОЕ
                return new therealpant.thaumicattempts.golemcraft.container.ContainerArcanePattern(player.inventory, player.getHeldItemMainhand());
            default:
                return null;
        }
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        switch (ID) {
            case GUI_GOLEM_CRAFTER: {
                TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
                if (te instanceof TileEntityGolemCrafter) {
                    return new GuiGolemCrafter(player.inventory, (TileEntityGolemCrafter) te);
                }
                return null;
            }
            case GUI_CRAFT_PATTERN: {
                ItemStack stack = findPatternStack(player);
                return (!stack.isEmpty())
                        ? new GuiCraftPattern(player.inventory, stack)
                        : null;
            }

            case GUI_ORDER_TERMINAL: {
                TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
                if (te instanceof TileOrderTerminal) {
                    return new therealpant.thaumicattempts.client.gui.GuiOrderTerminal(
                            player.inventory,
                            (TileOrderTerminal) te
                    );
                }
                return null;
            }
            case GUI_RESOURCE_REQUESTER: {
                TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
                if (te instanceof TileResourceRequester) {
                    return new therealpant.thaumicattempts.client.gui.GuiResourceRequester(
                            player.inventory,
                            (TileResourceRequester) te
                    );
                }
                return null;
            }

            case GUI_ARCANE_PATTERN: // <-- НОВОЕ
                return new therealpant.thaumicattempts.client.gui.GuiArcanePattern(player.inventory, player.getHeldItemMainhand());            default:
                return null;
        }
    }

    private ItemStack findPatternStack(EntityPlayer player) {
        ItemStack stack = player.getHeldItemMainhand();
        if (isEditablePattern(stack)) return stack;

        ItemStack off = player.getHeldItemOffhand();
        if (isEditablePattern(off)) return off;

        return ItemStack.EMPTY;
    }

    private boolean isEditablePattern(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemCraftPattern
                || stack.getItem() instanceof ItemCraftPatternInfusion
                || stack.getItem() instanceof ItemResourceList;

    }
}
