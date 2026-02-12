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
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.api.EldritchExtractorApi;
import therealpant.thaumicattempts.api.EldritchExtractorRecipe;
import therealpant.thaumicattempts.integration.thaumicaugmentation.ImpetusCompat;
import therealpant.thaumicattempts.items.ItemTAGem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.lib.events.EssentiaHandler;

import javax.annotation.Nullable;


public class TileRiftExtractor extends TileEntity implements ITickable, IAnimatable {

    private static final String TAUG_MODID = "thaumicaugmentation";
    private static final int SLOT_CROWN = 0;
    private static final int SLOT_CORE = 1;
    private static final int TICKS_PER_STAGE = 40;

    private static final int TICKS_PER_SECOND = 20;
    private static final int ESSENTIA_DRAIN_RANGE = 8;
    private static final float ESSENTIA_FALLBACK_MULTIPLIER = 1.5F;
    private static final float ELDRITCH_RATIO = 0.40F;
    private static final float VOID_RATIO = 0.40F;
    private static final float FLUX_RATIO = 0.20F;

    @Nullable
    private AnimVisualState lastSentAnim = null;

    private final AnimationFactory factory = new AnimationFactory(this);

    private AnimVisualState animState = AnimVisualState.SLIP;

    private enum AnimVisualState {
        SLIP, ACTIV_PLUS, WORK, ACTIV_MINUS
    }

    private void applyAnimation(AnimationController<?> controller, AnimVisualState state) {
        applyAnimation(controller, state, false);
    }

    private void applyAnimation(AnimationController<?> controller, AnimVisualState state, boolean forceRestart) {
        if (forceRestart) {
            controller.markNeedsReload();
            lastSentAnim = null;
        }
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
        EldritchExtractorRecipe recipe = getRecipeForCrown(crown);
        if (crown.isEmpty() || recipe == null || recipe.getResult().isEmpty()) return false;

        int maxStages = stageMax > 0 ? stageMax : recipe.getStages();
        // пока генерация не завершена
        return stage < maxStages;
    }


    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirtyAndSync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_CROWN) {
                return getRecipeForCrown(stack) != null;
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
    private int stageMax;
    private int ticksToNextStage;
    private boolean pausedByRedstone;
    private ItemStack cachedCoreRenderStack = ItemStack.EMPTY;
    private int stageEssentiaTargetEldritch;
    private int stageEssentiaTargetVoid;
    private int stageEssentiaTargetFlux;
    private int stageEssentiaSpentEldritch;
    private int stageEssentiaSpentVoid;
    private int stageEssentiaSpentFlux;


    @Override
    public void update() {
        if (world == null || world.isRemote) return;


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
        EldritchExtractorRecipe recipe = getRecipeForCrown(crown);
        ItemStack resultStack = recipe == null ? ItemStack.EMPTY : recipe.getResult();

        // нет валидной короны -> сброс прогресса (но core НЕ трогаем)
        if (recipe == null || resultStack.isEmpty() || recipe.getStages() <= 0 || recipe.getImpetusCost() <= 0) {
            resetProgress("crown missing/invalid");
            return;
        }
        cachedCoreRenderStack = resultStack.copy();
        if (stageMax != recipe.getStages()) {
            if (stage > 0 || ticksToNextStage > 0) {
                resetProgress("crown recipe changed");
            }
            stageMax = recipe.getStages();
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
        if (stage >= stageMax) {
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

        long consumedImpetus = 0L;
        if (Loader.isModLoaded(TAUG_MODID)) {
            if (consumeImpetus(recipe.getImpetusCost(), true) < recipe.getImpetusCost()) {
                return;
            }


            consumedImpetus = consumeImpetus(recipe.getImpetusCost(), false);
            if (consumedImpetus < recipe.getImpetusCost()) {
                return;
            }
        } else {
            prepareStageEssentiaCosts(recipe.getImpetusCost());
            if (!consumeStageEssentiaUntilBlocked()) {
                return;
            }
        }

        stage = Math.min(stageMax, stage + 1);
        ticksToNextStage = 0;

        if (Loader.isModLoaded(TAUG_MODID)) {
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Stage {}/{} at {}, consumed {} impetus",
                    stage, stageMax, pos, consumedImpetus);
        } else {
            ThaumicAttempts.LOGGER.debug(
                    "[RiftExtractor] Stage {}/{} at {}, consumed fallback essentia: eldritch={}, void={}, flux={}",
                    stage, stageMax, pos,
                    stageEssentiaTargetEldritch, stageEssentiaTargetVoid, stageEssentiaTargetFlux
            );
        }

        resetStageEssentiaProgress();

        // достигли финальной стадии -> кладём результат в core ОДИН раз
        if (stage >= stageMax) {
            applyCrownUsage(recipe, crown);
            inventory.setStackInSlot(SLOT_CORE, resultStack.copy());
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Generation complete at {}, result placed into core", pos);
        }

        markDirtyAndSync();
    }


    @Override
    public void onLoad() {
        super.onLoad();

    }

    @Override
    public void validate() {
        super.validate();

    }

    @Override
    public void invalidate() {

        super.invalidate();
    }

    @Override
    public void onChunkUnload() {

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
        int maxStages = stageMax;
        if (maxStages <= 0) {
            EldritchExtractorRecipe recipe = getRecipeForCrown(inventory.getStackInSlot(SLOT_CROWN));
            maxStages = recipe == null ? 1 : recipe.getStages();
        }
        return MathHelper.clamp((float) stage / (float) maxStages, 0.0F, 1.0F);
    }

    @SideOnly(Side.CLIENT)
    public float getCoreAlphaSmooth(float partialTicks) {
        int maxStages = stageMax;

        if (maxStages <= 0) {
            EldritchExtractorRecipe recipe = getRecipeForCrown(inventory.getStackInSlot(SLOT_CROWN));
            maxStages = (recipe == null) ? 1 : Math.max(1, recipe.getStages());
        }

        // вообще не стартовали — полностью скрыто
        if (stage <= 0 && ticksToNextStage <= 0) {
            return 0.0F;
        }

        float stageProgress = MathHelper.clamp(
                (ticksToNextStage + partialTicks) / (float) TICKS_PER_STAGE,
                0.0F, 1.0F
        );

        float t = ((float) stage + stageProgress) / (float) maxStages;
        t = MathHelper.clamp(t, 0.0F, 1.0F);

        // smoothstep
        t = t * t * (3.0F - 2.0F * t);

        return t; // 0..1
    }

    public boolean tryInsertCrown(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) return false;
        ItemStack held = player.getHeldItem(hand);
        if (getRecipeForCrown(held) == null) return false;
        if (!inventory.getStackInSlot(SLOT_CROWN).isEmpty()) {
            if (!tryExtractCrown(player)) return false;
        }
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
        if ((stage > 0 || ticksToNextStage > 0) && stage < stageMax) {
            ItemStack crown = inventory.getStackInSlot(SLOT_CROWN);
            EldritchExtractorRecipe recipe = getRecipeForCrown(crown);
            if (recipe != null && !recipe.getResult().isEmpty()) {
                cachedCoreRenderStack = recipe.getResult().copy();
                return recipe.getResult();
            }
            if (!cachedCoreRenderStack.isEmpty()) {
                return cachedCoreRenderStack;
            }
        }

        return ItemStack.EMPTY;
    }


    public boolean tryExtractCrown(EntityPlayer player) {
        if (player == null) return false;

        ItemStack crown = inventory.getStackInSlot(SLOT_CROWN);
        EldritchExtractorRecipe recipe = getRecipeForCrown(crown);
        boolean wasCrafting = isCraftingActive(crown, recipe);

        ItemStack extracted = inventory.extractItem(SLOT_CROWN, 1, false);
        if (extracted.isEmpty()) return false;

        // если прогресс не завершён — просто сбрасываем прогресс (core мы больше не трогаем)
        if (stage < stageMax && wasCrafting) {
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
        if (stage < stageMax || stageMax <= 0) return false;
        ItemStack extracted = inventory.extractItem(SLOT_CORE, 1, false);
        if (extracted.isEmpty()) return false;
        boolean added = player.inventory.addItemStackToInventory(extracted);
        if (!added && world != null) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), extracted);
        }
        if (inventory.getStackInSlot(SLOT_CORE).isEmpty()) {
            stage = 0;
            ticksToNextStage = 0;
            stageMax = 0;
            resetStageEssentiaProgress();
            ThaumicAttempts.LOGGER.debug("[RiftExtractor] Core extracted, reset at {}", pos);
            markDirtyAndSync();
        }
        return true;
    }

    private void resetProgress(String reason) {
        boolean hadProgress = stage != 0 || ticksToNextStage != 0;
        if (!hadProgress) {
            stageMax = 0;
            cachedCoreRenderStack = ItemStack.EMPTY;
            resetStageEssentiaProgress();
            return;
        }

        stage = 0;
        ticksToNextStage = 0;
        stageMax = 0;
        cachedCoreRenderStack = ItemStack.EMPTY;
        resetStageEssentiaProgress();

        ThaumicAttempts.LOGGER.debug("[RiftExtractor] Progress reset at {} ({})", pos, reason);
        markDirtyAndSync();
    }


    @Nullable
    private EldritchExtractorRecipe getRecipeForCrown(ItemStack crown) {
        return EldritchExtractorApi.findRecipe(crown);
    }

    private void prepareStageEssentiaCosts(long impetusCost) {
        if (stageEssentiaTargetEldritch > 0 || stageEssentiaTargetVoid > 0 || stageEssentiaTargetFlux > 0) return;
        int total = (int) Math.ceil(Math.max(0L, impetusCost) * ESSENTIA_FALLBACK_MULTIPLIER);
        stageEssentiaTargetEldritch = (int) Math.ceil(total * ELDRITCH_RATIO);
        stageEssentiaTargetVoid = (int) Math.ceil(total * VOID_RATIO);
        stageEssentiaTargetFlux = (int) Math.ceil(total * FLUX_RATIO);
    }

    private boolean consumeStageEssentiaUntilBlocked() {
        if (world == null) return false;
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            progressed |= consumeSingleEssentiaUnit(Aspect.ELDRITCH, stageEssentiaTargetEldritch, true);
            progressed |= consumeSingleEssentiaUnit(Aspect.VOID, stageEssentiaTargetVoid, false);
            progressed |= consumeSingleEssentiaUnit(Aspect.FLUX, stageEssentiaTargetFlux, false);
            if (isStageEssentiaComplete()) {
                return true;
            }
        }
        return isStageEssentiaComplete();
    }

    private boolean consumeSingleEssentiaUnit(Aspect aspect, int target, boolean eldritch) {
        if (target <= 0 || aspect == null) return false;
        int spent = eldritch ? stageEssentiaSpentEldritch : (aspect == Aspect.VOID ? stageEssentiaSpentVoid : stageEssentiaSpentFlux);
        if (spent >= target) return false;
        boolean ok = EssentiaHandler.drainEssentia(this, aspect, EnumFacing.UP, ESSENTIA_DRAIN_RANGE, 0);
        if (!ok) return false;
        if (eldritch) {
            stageEssentiaSpentEldritch++;
        } else if (aspect == Aspect.VOID) {
            stageEssentiaSpentVoid++;
        } else {
            stageEssentiaSpentFlux++;
        }
        markDirty();
        return true;
    }

    private boolean isStageEssentiaComplete() {
        return stageEssentiaSpentEldritch >= stageEssentiaTargetEldritch
                && stageEssentiaSpentVoid >= stageEssentiaTargetVoid
                && stageEssentiaSpentFlux >= stageEssentiaTargetFlux;
    }

    private void resetStageEssentiaProgress() {
        stageEssentiaTargetEldritch = 0;
        stageEssentiaTargetVoid = 0;
        stageEssentiaTargetFlux = 0;
        stageEssentiaSpentEldritch = 0;
        stageEssentiaSpentVoid = 0;
        stageEssentiaSpentFlux = 0;
    }

    private void applyCrownUsage(EldritchExtractorRecipe recipe, ItemStack crown) {
        if (world == null || recipe == null || crown == null || crown.isEmpty()) return;

        int minDamage = Math.max(0, recipe.getMinDamage());
        int maxDamage = Math.max(0, recipe.getMaxDamage());
        int applied = getRandomValueInRange(minDamage, maxDamage);

        if (ItemTAGem.isGem(crown)) {
            int maxDurability = ItemTAGem.getMaxGemDurability(crown);
            if (maxDurability <= 0) return;
            int newDamage = ItemTAGem.getGemDamage(crown) + applied;
            if (newDamage >= maxDurability) {
                inventory.setStackInSlot(SLOT_CROWN, ItemStack.EMPTY);
            } else {
                ItemTAGem.setGemDamage(crown, newDamage);
            }
            return;
        }
        if (crown.isItemStackDamageable()) {
            int maxDurability = crown.getMaxDamage();
            int newDamage = crown.getItemDamage() + applied;
            if (newDamage >= maxDurability) {
                inventory.setStackInSlot(SLOT_CROWN, ItemStack.EMPTY);
            } else {
                crown.setItemDamage(newDamage);
            }
            return;
        }
            if (applied <= 0) return;
            int chance = Math.min(100, applied);
            if (world.rand.nextInt(100) < chance) {
                inventory.setStackInSlot(SLOT_CROWN, ItemStack.EMPTY);
            }
    }

    private int getRandomValueInRange(int minValue, int maxValue) {
        int min = Math.min(minValue, maxValue);
        int max = Math.max(minValue, maxValue);
        int size = max - min + 1;
        int[] range = new int[size];
        for (int i = 0; i < size; i++) {
            range[i] = min + i;
        }
        return range[world.rand.nextInt(size)];
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
        stageMax = compound.getInteger("StageMax");
        stageEssentiaTargetEldritch = compound.getInteger("StageEssTargetEldritch");
        stageEssentiaTargetVoid = compound.getInteger("StageEssTargetVoid");
        stageEssentiaTargetFlux = compound.getInteger("StageEssTargetFlux");
        stageEssentiaSpentEldritch = compound.getInteger("StageEssSpentEldritch");
        stageEssentiaSpentVoid = compound.getInteger("StageEssSpentVoid");
        stageEssentiaSpentFlux = compound.getInteger("StageEssSpentFlux");
        if (compound.hasKey("CachedCoreRenderStack")) {
            cachedCoreRenderStack = new ItemStack(compound.getCompoundTag("CachedCoreRenderStack"));
        } else {
            cachedCoreRenderStack = ItemStack.EMPTY;
        }

    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Inventory", inventory.serializeNBT());
        compound.setInteger("Stage", stage);
        compound.setInteger("TicksToNextStage", ticksToNextStage);
        compound.setInteger("StageMax", stageMax);
        compound.setInteger("StageEssTargetEldritch", stageEssentiaTargetEldritch);
        compound.setInteger("StageEssTargetVoid", stageEssentiaTargetVoid);
        compound.setInteger("StageEssTargetFlux", stageEssentiaTargetFlux);
        compound.setInteger("StageEssSpentEldritch", stageEssentiaSpentEldritch);
        compound.setInteger("StageEssSpentVoid", stageEssentiaSpentVoid);
        compound.setInteger("StageEssSpentFlux", stageEssentiaSpentFlux);
        if (!cachedCoreRenderStack.isEmpty()) {
            compound.setTag("CachedCoreRenderStack", cachedCoreRenderStack.serializeNBT());
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
        if (ImpetusCompat.isImpetusCapability(capability)) return true;
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
        if (ImpetusCompat.isImpetusCapability(capability)) {
            T node = ImpetusCompat.getImpetusCapabilityInstance(capability, this);
            if (node != null) return node;
        }
        return super.getCapability(capability, facing);
    }

    private long consumeImpetus(long requested, boolean simulate) {
        if (requested <= 0 || !Loader.isModLoaded(TAUG_MODID)) return 0L;
        int req = requested > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) requested;
        return simulate
                ? (ImpetusCompat.canConsumeImpetus(this, req) ? req : 0L)
                : (ImpetusCompat.consumeImpetus(this, req) ? req : 0L);
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
                animState = (animState == AnimVisualState.ACTIV_PLUS) ? AnimVisualState.WORK : AnimVisualState.SLIP;
                applyAnimation(controller, animState, true);
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
                    applyAnimation(controller, animState, true);
                    return PlayState.CONTINUE;
                }
                // повторяем тот же клип, пока не потребуется переход
                animState = desiredLoop;
                applyAnimation(controller, animState, true);
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

    private boolean isCraftingActive(ItemStack crown, @Nullable EldritchExtractorRecipe recipe) {
        if (world == null) return false;
        if (world.isBlockPowered(pos)) return false;
        if (recipe == null || recipe.getResult().isEmpty()) return false;
        if (recipe.getStages() <= 0 || recipe.getImpetusCost() <= 0) return false;
        if (stage >= recipe.getStages()) return false;
        if (!inventory.getStackInSlot(SLOT_CORE).isEmpty()) return false;
        return stage > 0 || ticksToNextStage > 0;
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
            if (getRecipeForCrown(stack) == null) return stack;
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
                if (stage < stageMax) {
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
            if ((stage > 0 || ticksToNextStage > 0) && stage < stageMax) {
                ItemStack crown = inventory.getStackInSlot(SLOT_CROWN);
                EldritchExtractorRecipe recipe = getRecipeForCrown(crown);
                if (recipe != null && !recipe.getResult().isEmpty()) return recipe.getResult();
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
            if (stage < stageMax || stageMax <= 0 || amount <= 0) return ItemStack.EMPTY;
            ItemStack extracted = inventory.extractItem(SLOT_CORE, amount, simulate);
            if (!simulate && !extracted.isEmpty() && inventory.getStackInSlot(SLOT_CORE).isEmpty()) {
                stage = 0;
                ticksToNextStage = 0;
                stageMax = 0;
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
