package therealpant.thaumicattempts.data.enchantments;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import therealpant.thaumicattempts.ThaumicAttempts;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public final class VisChargeAuraHandler {

    private static final String NBT_CD = "ta_vischarge_cd";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        EntityPlayer p = e.player;
        if (p == null || p.world == null || p.world.isRemote) return;

        // Проверяем все “важные” слоты (можешь расширить при желании)
        tickStack(p, p.getHeldItemMainhand());
        tickStack(p, p.getHeldItemOffhand());
        for (ItemStack s : p.inventory.mainInventory) tickStack(p, s);
        for (ItemStack s : p.inventory.armorInventory) tickStack(p, s);
    }

    private static void tickStack(EntityPlayer p, ItemStack s) {
        if (s == null || s.isEmpty()) return;
        if (!(s.getItem() instanceof IRechargable)) return;

        int lvl = EnumInfusionEnchantment.getInfusionEnchantmentLevel(s, EnumInfusionEnchantment.VISBATTERY);
        if (lvl <= 0) return;

        // Уже полный заряд? Тогда не трогаем
        int cur = RechargeHelper.getCharge(s);
        int max = ((IRechargable) s.getItem()).getMaxCharge(s, p);
        if (cur < 0 || max <= 0 || cur >= max) return;

        NBTTagCompound tag = s.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            s.setTagCompound(tag);
        }

        int cd = tag.getInteger(NBT_CD);
        if (cd > 0) {
            tag.setInteger(NBT_CD, cd - 1);
            return;
        }

        // Твои параметры по уровню (3/2/1)
        int intervalTicks = intervalTicks(lvl);
        float costVis = costVis(lvl);

        BlockPos pos = p.getPosition();

        // не трогаем ауру если в этом месте “preserve aura”
        if (AuraHelper.shouldPreserveAura(p.world, p, pos)) {
            tag.setInteger(NBT_CD, intervalTicks);
            return;
        }

        // не тратим частично: сначала проверяем наличие
        if (AuraHelper.getVis(p.world, pos) + 1.0e-4f < costVis) {
            tag.setInteger(NBT_CD, intervalTicks);
            return;
        }

        float drained = AuraHelper.drainVis(p.world, pos, costVis, false);
        if (drained + 1.0e-4f >= costVis) {
            // Заряжаем РОВНО на 1 единицу (как ты просил)
            RechargeHelper.rechargeItemBlindly(s, p, 1);
        }

        tag.setInteger(NBT_CD, intervalTicks);
    }

    private static int intervalTicks(int lvl) {
        if (lvl == 1) return 60; // 1.5s
        if (lvl == 2) return 50; // 1.2s
        return 40;               // 1.0s
    }

    private static float costVis(int lvl) {
        if (lvl == 1) return 1.0f;
        if (lvl == 2) return 1.5f;
        return 2.0f;
    }
}
