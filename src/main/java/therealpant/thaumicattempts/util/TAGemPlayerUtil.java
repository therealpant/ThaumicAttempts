package therealpant.thaumicattempts.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class TAGemPlayerUtil {

    private TAGemPlayerUtil() {}

    public static int getSameGemCount(EntityPlayer player, ResourceLocation gemId) {
        if (player == null || gemId == null) {
            return 1;
        }
        int count = 0;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (!TAGemInlayUtil.hasGem(armor)) {
                continue;
            }
            ResourceLocation id = TAGemInlayUtil.getGemId(armor);
            if (gemId.equals(id)) {
                count++;
            }
        }
        return Math.max(1, count);
    }
}
