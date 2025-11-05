package therealpant.thaumicattempts.golemcraft;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.ItemStackHandler;

import static vazkii.botania.common.block.tile.corporea.TileCorporeaIndex.getInputHandler;

public class TileGolemCrafter extends TileEntity implements ITickable {

    public final ItemStackHandler patterns = new ItemStackHandler(15);
    private int rsPower = 0;   // 0..15 (0 = цикл)
    private int cycleIdx = 0;  // 0..14
    private int ticks = 0;

    // цветовой фильтр «как у печатей»: -1 любой, 0..15 = конкретный цвет
    private int golemColor = -1;

    @Override
    public void update() {
        if (world == null) return;

        if (world.isRemote) {
            ticks++;
            if (rsPower == 0 && ticks % 30 == 0) { // 1.5 сек при 20 TPS
                int next = cycleIdx;
                for (int i = 0; i < 15; i++) {
                    next = (next + 1) % 15;
                    if (!patterns.getStackInSlot(next).isEmpty()) { cycleIdx = next; break; }
                }
            }
            return;
        }
        // server: здесь будет логика взаимодействия с големами через печати (запрос ресурсов)
    }

    public void setRedstonePower(int p) {
        p = MathHelper.clamp(p, 0, 15);
        if (p != this.rsPower) {
            this.rsPower = p;
            this.markDirty();
            if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    public int getActiveIndex() {
        if (rsPower > 0) return MathHelper.clamp(rsPower, 1, 15) - 1; // 1..15 → 0..14
        return cycleIdx;
    }

    public net.minecraft.item.ItemStack getActivePattern() { return patterns.getStackInSlot(getActiveIndex()); }
    public int getRsPower() { return rsPower; }

    // === Цвет голема: API для GUI/пакетов ===
    public void setGolemColor(int colorMeta) {
        int c = colorMeta < 0 ? -1 : (colorMeta % 16);
        if (c != this.golemColor) {
            this.golemColor = c;
            markDirty();
            if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }
    public int getGolemColor() { return golemColor; }
    public boolean acceptsGolemColor(int meta) { return golemColor < 0 || (meta % 16) == golemColor; }


    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, @javax.annotation.Nullable net.minecraft.util.EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }
    @SuppressWarnings("unchecked")
    @javax.annotation.Nullable
    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @javax.annotation.Nullable net.minecraft.util.EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            // Простая логика: сверху — вход, снизу — выход, по бокам — вход
        }
        return super.getCapability(capability, facing);
    }
    public boolean isUsableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        if (world.getTileEntity(pos) != this) return false;
        return player.getDistanceSq(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        ) <= 64.0D;
    }
    @Override
    public void readFromNBT(NBTTagCompound nbt) {

    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }
}
