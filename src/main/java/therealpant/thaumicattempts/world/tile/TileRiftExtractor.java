package therealpant.thaumicattempts.world.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import thecodex6824.thaumicaugmentation.api.client.ImpetusRenderingManager;
import thecodex6824.thaumicaugmentation.api.impetus.node.CapabilityImpetusNode;
import thecodex6824.thaumicaugmentation.api.impetus.node.ConsumeResult;
import thecodex6824.thaumicaugmentation.api.impetus.node.IImpetusConsumer;
import thecodex6824.thaumicaugmentation.api.impetus.node.IImpetusNode;
import thecodex6824.thaumicaugmentation.api.impetus.node.NodeHelper;
import thecodex6824.thaumicaugmentation.api.impetus.node.prefab.SimpleImpetusConsumer;
import thecodex6824.thaumicaugmentation.api.internal.TAInternals;
import thecodex6824.thaumicaugmentation.api.util.DimensionalBlockPos;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.ModBlocksItems;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class TileRiftExtractor extends TileEntity implements ITickable, IAnimatable {

    private static final String TAUG_MODID = "thaumicaugmentation";
    private static final String NBT_IMPETUS_NODE = "TAugImpetusNode";
    private static final int SLOT_CROWN = 0;
    private static final int SLOT_CORE = 1;
    private static final int STAGE_MAX = 10;
    private static final int TICKS_PER_STAGE = 40;
    private static final int IMPETUS_PER_STAGE = 10;

    private static final int TICKS_PER_SECOND = 20;

    @Nullable
    private AnimVisualState lastSentAnim = null;

    private final AnimationFactory factory = new AnimationFactory(this);

    private AnimVisualState animState = AnimVisualState.SLIP;

    private enum AnimVisualState {
        SLIP, ACTIV_PLUS, WORK, ACTIV_MINUS
    }

    private void applyAnimation(AnimationController<?> controller, AnimVisualState state) {
        if (lastSentAnim == state) return; // важно: не трогаем контроллер каждый кадр
        lastSentAnim = state;

        switch (state) {
            case ACTIV_PLUS:
                controller.setAnimation(new AnimationBuilder().addAnimation("activ+", false));
                break;
            case WORK:
                controller.setAnimation(new AnimationBuilder().addAnimation("work", false));
                break;
            case ACTIV_MINUS:
                controller.setAnimation(new AnimationBuilder().addAnimation("activ-", false));
                break;
            case SLIP:
            default:
                controller.setAnimation(new AnimationBuilder().addAnimation("slip", false));
                break;
        }
    }

    private boolean shouldWorkForAnimation() {
        if (world == null) return false;

        // редстоун ставит на паузу => не работаем
        if (world.isBlockPowered(pos)) return false;

        // без валидной короны — не работаем
        ItemStack crown = inventory.getStackInSlot(SLOT_CROWN);
        if (crown.isEmpty() || getResultStack(crown).isEmpty()) return false;

        // пока генерация не завершена
        return stage < STAGE_MAX;
    }


    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_CROWN) {
                return isValidCrown(stack);
            }
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    private final IItemHandler crownHandler = new CrownHandler();
    private final IItemHandler coreHandler = new CoreHandler();
    private final IItemHandler combinedHandler = new CombinedHandler();

    private int stage;
    private int ticksToNextStage;
    private boolean pausedByRedstone;

    @Nullable
    private IImpetusConsumer impetusConsumer;
    @Nullable
    private NBTTagCompound pendingImpetusTag;

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        ensureImpetusNode();

        boolean powered = world.isBlockPowered(pos);
        if (powered) {
            if (!pausedByRedstone) {
                pausedByRedstone = true;
                ThaumicAttempts.LOGGER.debug("[RiftExtractor] Paused by redstone at {}", pos);
            }
            return;
        }
        if (pausedByRedstone) {
            pausedByRedstone = false;
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Resumed after redstone pause at {}", pos);
        }

        ItemStack crown = inventory.getStackInSlot(SLOT_CROWN);
        ItemStack resultStack = getResultStack(crown);

        // нет валидной короны -> сброс прогресса (но core НЕ трогаем)
        if (resultStack.isEmpty()) {
            resetProgress("crown missing/invalid");
            return;
        }

        // если core занят — генерацию не ведём
        // (результат можно забрать, а если там “мусор” — игрок должен убрать)
        ItemStack core = inventory.getStackInSlot(SLOT_CORE);
        if (!core.isEmpty()) {
            // если core уже результат и stage не 10 — можно аккуратно “зафиксировать”
            // но проще: просто ждать пока игрок/автоматика заберёт
            return;
        }

        // если уже закончили стадии, но core пуст (например из-за бага) — положим результат
        if (stage >= STAGE_MAX) {
            inventory.setStackInSlot(SLOT_CORE, resultStack.copy());
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Completion fix: placed result into core at {}", pos);
            markDirtyAndSync();
            return;
        }

        // обычный тик прогресса
        if (ticksToNextStage < TICKS_PER_STAGE) {
            ticksToNextStage++;
            return;
        }

        // достаточно ли импетуса
        if (consumeImpetus(IMPETUS_PER_STAGE, true) < IMPETUS_PER_STAGE) {
            return;
        }

        long consumed = consumeImpetus(IMPETUS_PER_STAGE, false);
        if (consumed < IMPETUS_PER_STAGE) {
            return;
        }

        stage = Math.min(STAGE_MAX, stage + 1);
        ticksToNextStage = 0;

        ThaumicAttempts.LOGGER.debug("[RiftExtractor] Stage {}/{} at {}, consumed {} impetus",
                stage, STAGE_MAX, pos, consumed);

        // 1% шанс разрушить корону и прервать прогресс
        if (world.rand.nextInt(100) == 0) {
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Crown shattered at {} during stage {}", pos, stage);
            inventory.setStackInSlot(SLOT_CROWN, ItemStack.EMPTY);
            resetProgress("crown shattered");
            return;
        }

        // достигли 10 стадии -> кладём результат в core ОДИН раз
        if (stage >= STAGE_MAX) {
            inventory.setStackInSlot(SLOT_CORE, resultStack.copy());
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Generation complete at {}, result placed into core", pos);
        }

        markDirtyAndSync();
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

    public ItemStack getCrownStack() {
        return inventory.getStackInSlot(SLOT_CROWN);
    }

    public ItemStack getCoreStack() {
        return inventory.getStackInSlot(SLOT_CORE);
    }

    public int getStage() {
        return stage;
    }

    public float getCoreAlpha() {
        return MathHelper.clamp((float) stage / (float) STAGE_MAX, 0.0F, 1.0F);
    }

    public boolean tryInsertCrown(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (!isValidCrown(held)) return false;
        if (!inventory.getStackInSlot(SLOT_CROWN).isEmpty()) return false;
        ItemStack toInsert = held.copy();
        toInsert.setCount(1);
        inventory.setStackInSlot(SLOT_CROWN, toInsert);
        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }
        markDirtyAndSync();
        return true;
    }

    public ItemStack getCoreRenderStack() {
        // если уже есть реальный результат в core — показываем его всегда
        ItemStack core = inventory.getStackInSlot(SLOT_CORE);
        if (!core.isEmpty()) return core;

        // иначе, если идёт прогресс — показываем будущий результат по короне
        if (stage > 0 && stage < STAGE_MAX) {
            ItemStack crown = inventory.getStackInSlot(SLOT_CROWN);
            ItemStack res = getResultStack(crown);
            if (!res.isEmpty()) return res;
        }

        return ItemStack.EMPTY;
    }


    public boolean tryExtractCrown(EntityPlayer player) {
        if (player == null) return false;

        ItemStack extracted = inventory.extractItem(SLOT_CROWN, 1, false);
        if (extracted.isEmpty()) return false;

        // если прогресс не завершён — просто сбрасываем прогресс (core мы больше не трогаем)
        if (stage < STAGE_MAX) {
            resetProgress("crown removed by player");
        }

        boolean added = player.inventory.addItemStackToInventory(extracted);
        if (!added && world != null) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), extracted);
        }

        markDirtyAndSync();
        return true;
    }



    public boolean tryExtractCore(EntityPlayer player) {
        if (player == null) return false;
        if (stage < STAGE_MAX) return false;
        ItemStack extracted = inventory.extractItem(SLOT_CORE, 1, false);
        if (extracted.isEmpty()) return false;
        boolean added = player.inventory.addItemStackToInventory(extracted);
        if (!added && world != null) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), extracted);
        }
        if (inventory.getStackInSlot(SLOT_CORE).isEmpty()) {
            stage = 0;
            ticksToNextStage = 0;
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Core extracted, reset at {}", pos);
            markDirtyAndSync();
        }
        return true;
    }

    private void resetProgress(String reason) {
        boolean hadProgress = stage != 0 || ticksToNextStage != 0;
        if (!hadProgress) return;

        stage = 0;
        ticksToNextStage = 0;

        ThaumicAttempts.LOGGER.debug("[RiftExtractor] Progress reset at {} ({})", pos, reason);
        markDirtyAndSync();
    }


    private boolean isValidCrown(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == ModBlocksItems.RIFT_EMBER
                || item == ModBlocksItems.RIFT_AMETIST
                || item == ModBlocksItems.RIFT_BRILIANT;
    }

    private ItemStack getResultStack(ItemStack crown) {
        if (!isValidCrown(crown)) return ItemStack.EMPTY;
        Item item = crown.getItem();
        if (item == ModBlocksItems.RIFT_EMBER) {
            return new ItemStack(ModBlocksItems.RIFT_FLOWER);
        }
        if (item == ModBlocksItems.RIFT_AMETIST) {
            return new ItemStack(ModBlocksItems.RIFT_STONE);
        }
        if (item == ModBlocksItems.RIFT_BRILIANT) {
            return new ItemStack(ModBlocksItems.RIFT_CRISTAL);
        }
        return ItemStack.EMPTY;
    }

    private boolean isSameItem(ItemStack stack, ItemStack other) {
        if (stack.isEmpty() || other.isEmpty()) return false;
        return stack.getItem() == other.getItem();
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
        stage = compound.getInteger("Stage");
        ticksToNextStage = compound.getInteger("TicksToNextStage");
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
        compound.setInteger("Stage", stage);
        compound.setInteger("TicksToNextStage", ticksToNextStage);
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
            if (facing == EnumFacing.DOWN) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(coreHandler);
            }
            if (facing == null) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combinedHandler);
            }
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(crownHandler);
        }
        if (capability == CapabilityImpetusNode.IMPETUS_NODE && Loader.isModLoaded(TAUG_MODID)) {
            ensureImpetusNode();
            if (impetusConsumer != null) {
                return capability.cast((T) impetusConsumer);
            }
        }
        return super.getCapability(capability, facing);
    }

    private void ensureImpetusNode() {
        if (world == null) return;
        if (!Loader.isModLoaded(TAUG_MODID)) return;

        if (impetusConsumer == null) {
            DimensionalBlockPos loc = new DimensionalBlockPos(pos, world.provider.getDimension());
            impetusConsumer = new SimpleImpetusConsumer(1, 0, loc);
            impetusConsumer.init(world);

            if (pendingImpetusTag != null && impetusConsumer instanceof INBTSerializable) {
                ((INBTSerializable<NBTTagCompound>) impetusConsumer).deserializeNBT(pendingImpetusTag);
                pendingImpetusTag = null;
            }

            if (world.isRemote) {
                registerRenderableNodeClient();
            } else {
                NodeHelper.validate(impetusConsumer, world);
                NodeHelper.tryConnectNewlyLoadedPeers(impetusConsumer, world);
            }
        } else if (impetusConsumer instanceof IImpetusNode) {
            ((IImpetusNode) impetusConsumer).setLocation(
                    new DimensionalBlockPos(pos, world.provider.getDimension())
            );
        }
    }

    @SideOnly(Side.CLIENT)
    private void registerRenderableNodeClient() {
        if (impetusConsumer instanceof IImpetusNode) {
            ImpetusRenderingManager.registerRenderableNode((IImpetusNode) impetusConsumer);
        }
    }

    @SideOnly(Side.CLIENT)
    private void deregisterRenderableNodeClient() {
        if (impetusConsumer instanceof IImpetusNode) {
            ImpetusRenderingManager.deregisterRenderableNode((IImpetusNode) impetusConsumer);
        }
    }

    private void unloadImpetusNode() {
        if (world != null && world.isRemote) {
            deregisterRenderableNodeClient();
        }
        if (impetusConsumer != null) {
            impetusConsumer.unload();
        }
        impetusConsumer = null;
    }

    private long consumeImpetus(long requested, boolean simulate) {
        if (requested <= 0) return 0L;
        ensureImpetusNode();
        if (impetusConsumer == null) return 0L;

        ConsumeResult result = NodeHelper.consumeImpetusFromConnectedProviders(requested, impetusConsumer, simulate);
        long consumed = (result == null) ? 0L : result.energyConsumed;

        if (!simulate && consumed > 0 && result != null && result.paths != null && !result.paths.isEmpty()) {
            IImpetusNode selfNode = (impetusConsumer instanceof IImpetusNode) ? (IImpetusNode) impetusConsumer : null;

            for (Map.Entry<Deque<IImpetusNode>, Long> entry : result.paths.entrySet()) {
                Long amt = entry.getValue();
                if (amt == null || amt <= 0) continue;

                List<IImpetusNode> nodes = new ArrayList<>(entry.getKey());

                if (selfNode != null) {
                    if (nodes.isEmpty() || nodes.get(nodes.size() - 1) != selfNode) {
                        nodes.add(selfNode);
                    }
                }

                TAInternals.syncImpetusTransaction(nodes);
            }
        }

        return consumed;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        boolean shouldWork = shouldWorkForAnimation();
        AnimVisualState desiredLoop = shouldWork ? AnimVisualState.WORK : AnimVisualState.SLIP;

        // 1) Если сейчас проигрываем переход (не-loop) — его НЕЛЬЗЯ прерывать.
        //    Просто играем его и ждём AnimationState.Stopped.
        if (animState == AnimVisualState.ACTIV_PLUS || animState == AnimVisualState.ACTIV_MINUS) {
            applyAnimation(controller, animState);

            if (controller.getAnimationState() == AnimationState.Stopped) {
                // переход доигрался -> выбираем следующее loop-состояние
                animState = (animState == AnimVisualState.ACTIV_PLUS) ? AnimVisualState.WORK : AnimVisualState.SLIP;                lastSentAnim = null; // чтобы loop реально установился
                applyAnimation(controller, animState);
            }
            return PlayState.CONTINUE;
        }

        // 2) Мы в состоянии SLIP/WORK. Даже простои и работа должны доигрывать клип до конца.
        //    После завершения клипа решаем, что играть дальше.
        if (animState == AnimVisualState.SLIP || animState == AnimVisualState.WORK) {
            applyAnimation(controller, animState);
            if (controller.getAnimationState() == AnimationState.Stopped) {
                if (animState != desiredLoop) {
                    animState = (desiredLoop == AnimVisualState.WORK) ? AnimVisualState.ACTIV_PLUS : AnimVisualState.ACTIV_MINUS;
                } else {
                    // повторяем тот же клип, пока не потребуется переход
                    animState = desiredLoop;
                }
                lastSentAnim = null;
                applyAnimation(controller, animState);
            }
            return PlayState.CONTINUE;
        }

        // 3) Fallback (на всякий) — ставим корректное состояние, без резких переходов.
        animState = desiredLoop;
        lastSentAnim = null;
        applyAnimation(controller, animState);
        return PlayState.CONTINUE;
    }



    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    private class CrownHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(SLOT_CROWN);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            if (!isValidCrown(stack)) return stack;
            if (!inventory.getStackInSlot(SLOT_CROWN).isEmpty()) return stack;

            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            if (!simulate) {
                ItemStack toInsert = stack.copy();
                toInsert.setCount(1);
                inventory.setStackInSlot(SLOT_CROWN, toInsert);
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;

            ItemStack extracted = inventory.extractItem(SLOT_CROWN, amount, simulate);

            if (!simulate && !extracted.isEmpty()) {
                if (stage < STAGE_MAX) {
                    resetProgress("crown extracted by automation");
                } else {
                    ThaumicAttempts.LOGGER.debug("[RiftExtractor] Crown extracted after completion via automation at {}, core preserved", pos);
                }
            }
            return extracted;
        }

        public ItemStack getCoreRenderStack() {
            // если уже есть реальный результат в core — показываем его всегда
            ItemStack core = inventory.getStackInSlot(SLOT_CORE);
            if (!core.isEmpty()) return core;

            // иначе, если идёт прогресс — показываем будущий результат по короне
            if (stage > 0 && stage < STAGE_MAX) {
                ItemStack crown = inventory.getStackInSlot(SLOT_CROWN);
                ItemStack res = getResultStack(crown);
                if (!res.isEmpty()) return res;
            }

            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }

    private class CoreHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(SLOT_CORE);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack == null ? ItemStack.EMPTY : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (stage < STAGE_MAX || amount <= 0) return ItemStack.EMPTY;
            ItemStack extracted = inventory.extractItem(SLOT_CORE, amount, simulate);
            if (!simulate && !extracted.isEmpty() && inventory.getStackInSlot(SLOT_CORE).isEmpty()) {
                stage = 0;
                ticksToNextStage = 0;
                ThaumicAttempts.LOGGER.debug("[RiftExtractor] Core extracted via automation at {}", pos);
                markDirtyAndSync();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }

    private class CombinedHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == SLOT_CROWN) return inventory.getStackInSlot(SLOT_CROWN);
            if (slot == SLOT_CORE) return inventory.getStackInSlot(SLOT_CORE);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != SLOT_CROWN) return stack == null ? ItemStack.EMPTY : stack;
            return crownHandler.insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == SLOT_CROWN) {
                return crownHandler.extractItem(0, amount, simulate);
            }
            if (slot == SLOT_CORE) {
                return coreHandler.extractItem(0, amount, simulate);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }
}
