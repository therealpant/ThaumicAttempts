package therealpant.thaumicattempts.world.tile;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TileAuraBooster extends TileEntity implements ITickable, IEssentiaTransport {

    private static final int TICK_INTERVAL = 60;
    private static final int ESSENTIA_CAP = 8;
    private static final int ESSENTIA_SUCTION = 128;
    private static final int ESSENTIA_COST = 1;
    private static final int IMPETUS_COST = 50;
    private static final int PEARL_DAMAGE_INTERVAL = 10;
    private static final float VIS_ADD_MULTIPLIER = 0.10F;
    private static final float VIS_MAX_MULTIPLIER = 0.75F;
    private static final Aspect REQUIRED_ASPECT = Aspect.AURA;
    private static final String PEARL_ID = "thaumcraft:primordial_pearl";

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isPearl(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    private int tickCounter;
    private int essentiaAmount;
    private int pearlUseCounter;

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++tickCounter % TICK_INTERVAL != 0) return;
        attemptBoost();
    }

    public ItemStack getPearlStack() {
        return inventory.getStackInSlot(0);
    }

    private boolean hasPearl() {
        return isPearl(getPearlStack());
    }

    private void attemptBoost() {
        int pearlBonus = hasPearl() ? 1 : 0;
        if (ImpetusCompat.isAvailable() && ImpetusCompat.canConsumeImpetus(this, IMPETUS_COST)) {
            int efficiency = Math.min(3, 2 + pearlBonus);
            if (!canBoostAny(efficiency)) return;
            if (ImpetusCompat.consumeImpetus(this, IMPETUS_COST)) {
                if (boostChunks(efficiency)) {
                    handlePearlWear();
                }
                return;
            }
        }

        if (essentiaAmount >= ESSENTIA_COST) {
            int efficiency = Math.min(3, 1 + pearlBonus);
            if (!canBoostAny(efficiency)) return;
            consumeEssentia(ESSENTIA_COST);
            if (boostChunks(efficiency)) {
                handlePearlWear();
            }
        }
    }

    private void handlePearlWear() {
        if (!hasPearl()) return;
        pearlUseCounter++;
        if (pearlUseCounter < PEARL_DAMAGE_INTERVAL) return;
        pearlUseCounter = 0;
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.isEmpty()) return;
        if (stack.isItemStackDamageable()) {
            stack.setItemDamage(stack.getItemDamage() + 1);
            if (stack.getItemDamage() >= stack.getMaxDamage()) {
                stack.shrink(1);
            }
        } else {
            stack.shrink(1);
        }
        markDirtyAndSync();
    }

    private boolean canBoostAny(int efficiency) {
        if (efficiency <= 0) return false;
        BlockPos origin = pos;
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (efficiency == 3) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (needsBoost(chunkX + dx, chunkZ + dz, efficiency)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return needsBoost(chunkX, chunkZ, efficiency);
    }

    private boolean boostChunks(int efficiency) {
        if (efficiency <= 0) return false;
        BlockPos origin = pos;
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        boolean boosted = false;
        if (efficiency == 3) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boosted |= boostChunk(chunkX + dx, chunkZ + dz, efficiency);
                }
            }
            return boosted;
        }
        return boostChunk(chunkX, chunkZ, efficiency);
    }

    private boolean needsBoost(int chunkX, int chunkZ, int efficiency) {
        float base = getChunkBase(chunkX, chunkZ);
        if (base <= 0.0F) return false;
        float max = base * (1.0F + VIS_MAX_MULTIPLIER * efficiency);
        float current = AuraHelper.getVis(world, getChunkCenter(chunkX, chunkZ));
        return current < 0.8F * max;
    }

    private boolean boostChunk(int chunkX, int chunkZ, int efficiency) {
        float base = getChunkBase(chunkX, chunkZ);
        if (base <= 0.0F) return false;
        float max = base * (1.0F + VIS_MAX_MULTIPLIER * efficiency);
        BlockPos chunkPos = getChunkCenter(chunkX, chunkZ);
        float current = AuraHelper.getVis(world, chunkPos);
        if (current >= 0.8F * max) return false;
        float add = base * VIS_ADD_MULTIPLIER * efficiency;
        float allowed = max - current;
        if (allowed <= 0.0F) return false;
        float toAdd = Math.min(add, allowed);
        if (toAdd <= 0.0F) return false;
        AuraHelper.addVis(world, chunkPos, toAdd);
        return true;
    }

    private float getChunkBase(int chunkX, int chunkZ) {
        AuraChunk chunk = AuraHandler.getAuraChunk(world.provider.getDimension(), chunkX, chunkZ);
        if (chunk == null) return 0.0F;
        try {
            Field field = AuraChunk.class.getDeclaredField("base");
            field.setAccessible(true);
            Object value = field.get(chunk);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = AuraChunk.class.getDeclaredMethod("getBase");
            method.setAccessible(true);
            Object value = method.invoke(chunk);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0.0F;
    }

    private BlockPos getChunkCenter(int chunkX, int chunkZ) {
        return new BlockPos((chunkX << 4) + 8, pos.getY(), (chunkZ << 4) + 8);
    }

    private void consumeEssentia(int amount) {
        if (amount <= 0 || essentiaAmount <= 0) return;
        essentiaAmount = Math.max(0, essentiaAmount - amount);
        markDirtyAndSync();
        notifyEssentiaChange();
    }

    private void notifyEssentiaChange() {
        if (world != null) {
            world.notifyNeighborsOfStateChange(pos, getBlockType(), true);
        }
    }

    private static boolean isPearl(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item pearl = Item.getByNameOrId(PEARL_ID);
        return pearl != null && stack.getItem() == pearl;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Inventory")) {
            inventory.deserializeNBT(compound.getCompoundTag("Inventory"));
        }
        essentiaAmount = compound.getInteger("Essentia");
        pearlUseCounter = compound.getInteger("PearlUse");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Inventory", inventory.serializeNBT());
        compound.setInteger("Essentia", essentiaAmount);
        compound.setInteger("PearlUse", pearlUseCounter);
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        if (ImpetusCompat.isImpetusCapability(capability)) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        if (ImpetusCompat.isImpetusCapability(capability)) {
            return ImpetusCompat.getImpetusCapabilityInstance(capability, this);
        }
        return super.getCapability(capability, facing);
    }

    // ========================= IEssentiaTransport =========================

    @Override
    public boolean isConnectable(EnumFacing face) {
        return true;
    }

    @Override
    public boolean canInputFrom(EnumFacing face) {
        return true;
    }

    @Override
    public boolean canOutputTo(EnumFacing face) {
        return false;
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {}

    @Override
    public Aspect getSuctionType(EnumFacing face) {
        return essentiaAmount < ESSENTIA_CAP ? REQUIRED_ASPECT : null;
    }

    @Override
    public int getSuctionAmount(EnumFacing face) {
        return essentiaAmount < ESSENTIA_CAP ? ESSENTIA_SUCTION : 0;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, EnumFacing face) {
        if (aspect != REQUIRED_ASPECT || amount <= 0) return 0;
        int can = Math.min(amount, ESSENTIA_CAP - essentiaAmount);
        if (can <= 0) return 0;
        essentiaAmount += can;
        markDirtyAndSync();
        notifyEssentiaChange();
        return can;
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, EnumFacing face) {
        return 0;
    }

    @Override
    public Aspect getEssentiaType(EnumFacing face) {
        return essentiaAmount > 0 ? REQUIRED_ASPECT : null;
    }

    @Override
    public int getEssentiaAmount(EnumFacing face) {
        return essentiaAmount;
    }

    @Override
    public int getMinimumSuction() {
        return 8;
    }
}
