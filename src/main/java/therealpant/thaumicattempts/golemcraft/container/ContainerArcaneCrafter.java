package therealpant.thaumicattempts.golemcraft.container;

import net.minecraft.entity.player.InventoryPlayer;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter;

public class ContainerArcaneCrafter extends ContainerGolemCrafter {

    private static final int PLAYER_LEFT = 89;
    private static final int PLAYER_TOP = 171;

    private static final int PANEL_LEFT = 55;
    private static final int PANEL_TOP = 34;

    private static final int RESULT_LEFT = 231;
    private static final int RESULT_TOP = 70;

    public ContainerArcaneCrafter(InventoryPlayer playerInv, TileEntityArcaneCrafter te) {
        super(playerInv, te, PLAYER_LEFT, PLAYER_TOP, PANEL_LEFT, PANEL_TOP, RESULT_LEFT, RESULT_TOP);
    }
}