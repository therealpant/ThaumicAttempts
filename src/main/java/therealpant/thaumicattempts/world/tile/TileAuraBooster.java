package therealpant.thaumicattempts.world.tile;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.fml.common.Loader;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.aura.AuraHelper;
import thecodex6824.thaumicaugmentation.api.impetus.node.CapabilityImpetusNode;
import thecodex6824.thaumicaugmentation.api.impetus.node.ConsumeResult;
import thecodex6824.thaumicaugmentation.api.impetus.node.IImpetusConsumer;
import thecodex6824.thaumicaugmentation.api.impetus.node.NodeHelper;

import thecodex6824.thaumicaugmentation.api.impetus.node.prefab.SimpleImpetusConsumer;
import thecodex6824.thaumicaugmentation.api.util.DimensionalBlockPos;
import javax.annotation.Nullable;

public class TileAuraBooster extends TileEntity implements ITickable, IEssentiaTransport {

    private static final String TAUG_MODID = "thaumicaugmentation";
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
    private static final String NBT_IMPETUS_NODE = "TAugImpetusNode";

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
    @Nullable
    private IImpetusConsumer impetusConsumer;
    @Nullable
    private NBTTagCompound pendingImpetusTag;

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        ensureImpetusNode();
        if (++tickCounter % TICK_INTERVAL != 0) return;
        attemptBoost();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ensureImpetusNode();
    }

    @Override
    public void validate() {
        super.validate();
        ensureImpetusNode();
    }

    @Override
    public void invalidate() {
        unloadImpetusNode();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        unloadImpetusNode();
        super.onChunkUnload();
    }

    public ItemStack getPearlStack() {
        return inventory.getStackInSlot(0);
    }

    public boolean tryInsertPearl(net.minecraft.entity.player.EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!isPearl(held)) return false;
        if (!inventory.getStackInSlot(0).isEmpty()) return false;
        ItemStack toInsert = held.copy();
        toInsert.setCount(1);
        ItemStack remainder = inventory.insertItem(0, toInsert, false);
        if (!remainder.isEmpty()) return false;
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        markDirtyAndSync();
        return true;
    }

    public boolean tryExtractPearl(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) return false;
        if (inventory.getStackInSlot(0).isEmpty()) return false;
        ItemStack extracted = inventory.extractItem(0, 1, false);
        if (extracted.isEmpty()) return false;
        boolean added = player.inventory.addItemStackToInventory(extracted);
        if (!added && world != null) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), extracted);
        }
        markDirtyAndSync();
        return true;
    }

    private boolean hasPearl() {
        return isPearl(getPearlStack());
    }

    private void attemptBoost() {
        int pearlBonus = hasPearl() ? 1 : 0;
        int impetusEfficiency = Math.min(3, 2 + pearlBonus);
        if (canBoostAny(impetusEfficiency) && consumeImpetus(IMPETUS_COST)) {
            if (boostChunks(impetusEfficiency)) {
                handlePearlWear();
            }
            return;
        }

        if (essentiaAmount >= ESSENTIA_COST) {
            int efficiency = Math.min(3, 1 + pearlBonus);
            if (!canBoostAny(efficiency)) return;
            if (boostChunks(efficiency)) {
                consumeEssentia(ESSENTIA_COST);
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
        BlockPos chunkCenter = getChunkCenter(chunkX, chunkZ);
        float base = AuraHelper.getAuraBase(world, chunkCenter);
        return base > 0.0F ? base : 0.0F;
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
        if (compound.hasKey(NBT_IMPETUS_NODE)) {
            pendingImpetusTag = compound.getCompoundTag(NBT_IMPETUS_NODE);
            if (impetusConsumer instanceof INBTSerializable) {
                ((INBTSerializable<NBTTagCompound>) impetusConsumer).deserializeNBT(pendingImpetusTag);
                pendingImpetusTag = null;
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Inventory", inventory.serializeNBT());
        compound.setInteger("Essentia", essentiaAmount);
        compound.setInteger("PearlUse", pearlUseCounter);
        if (impetusConsumer instanceof INBTSerializable) {
            compound.setTag(NBT_IMPETUS_NODE, ((INBTSerializable<NBTTagCompound>) impetusConsumer).serializeNBT());
        }
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
        if (capability == CapabilityImpetusNode.IMPETUS_NODE && Loader.isModLoaded(TAUG_MODID)) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        if (capability == CapabilityImpetusNode.IMPETUS_NODE && Loader.isModLoaded(TAUG_MODID)) {
            ensureImpetusNode();
            if (impetusConsumer != null) {
                return capability.cast((T) impetusConsumer);
            }
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

    private void ensureImpetusNode() {
        if (world == null || world.isRemote) return;
        if (!Loader.isModLoaded(TAUG_MODID)) return;
        if (impetusConsumer == null) {
            DimensionalBlockPos loc = new DimensionalBlockPos(pos, world.provider.getDimension());
            impetusConsumer = new SimpleImpetusConsumer(1, 0, loc);
            impetusConsumer.init(world);
            NodeHelper.validate(impetusConsumer, world);
            NodeHelper.tryConnectNewlyLoadedPeers(impetusConsumer, world);
            if (pendingImpetusTag != null && impetusConsumer instanceof INBTSerializable) {
                ((INBTSerializable<NBTTagCompound>) impetusConsumer).deserializeNBT(pendingImpetusTag);
                pendingImpetusTag = null;
            }
        }
    }

    private void unloadImpetusNode() {
        if (impetusConsumer != null) {
            impetusConsumer.unload();
        }
    }

    private boolean consumeImpetus(int amount) {
        if (amount <= 0) return false;
        ensureImpetusNode();
        if (impetusConsumer == null) return false;
        ConsumeResult result = NodeHelper.consumeImpetusFromConnectedProviders(amount, impetusConsumer, false);
        return result != null && result.energyConsumed >= amount;
    }
}
