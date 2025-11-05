// therealpant.thaumicattempts.golemnet.client.GuiHandlerOrderTerminal.java
package therealpant.thaumicattempts.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import therealpant.thaumicattempts.golemnet.container.ContainerOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;

public class GuiHandlerOrderTerminal implements IGuiHandler {
    public static final int GUI_ID = 42;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_ID) {
            TileOrderTerminal te = (TileOrderTerminal) world.getTileEntity(new BlockPos(x, y, z));
            if (te != null) return new ContainerOrderTerminal(player.inventory, te);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_ID) {
            TileOrderTerminal te = (TileOrderTerminal) world.getTileEntity(new BlockPos(x, y, z));
            if (te != null) return new GuiOrderTerminal(player.inventory, te);
        }
        return null;
    }
}
