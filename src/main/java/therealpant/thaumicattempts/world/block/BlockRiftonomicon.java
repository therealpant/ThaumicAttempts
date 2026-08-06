package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.data.TAMultiblockTriggers;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.init.TABlocks;
import therealpant.thaumicattempts.items.ItemRiftonomiconProtocol;
import therealpant.thaumicattempts.items.ItemPrism;
import therealpant.thaumicattempts.world.tile.TileRiftonomicon;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconImpetusPort;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconTransmutationEssentiaInput;
import therealpant.thaumicattempts.world.tile.TileRiftonomiconTransmutationImpetusInput;

import javax.annotation.Nullable;
import java.util.List;

public class BlockRiftonomicon extends Block {
    public static final int CORE_Y_OFFSET = 3;
    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);

    private static final AxisAlignedBB FULL = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB BASE_INNER = new AxisAlignedBB(0, 0, 0, 1, 0.55D, 1);
    private static final AxisAlignedBB SECOND_LAYER_COLUMN = new AxisAlignedBB(0.125D, 0, 0.125D, 0.875D, 1.5D, 0.875D);
    private static final AxisAlignedBB TOP_CRYSTAL = new AxisAlignedBB(0.25D, 0, 0.25D, 0.75D, 1, 0.75D);
    // Compact 4px cube; center is 4px below the normal block center.
    private static final AxisAlignedBB IMPETUS_PORT = new AxisAlignedBB(
            0.375D, 0.125D, 0.375D,
            0.625D, 0.375D, 0.625D
    );

    public BlockRiftonomicon() {
        super(Material.ROCK);
        setHardness(45.0F);
        setResistance(200.0F);
        setLightOpacity(0);
        setTranslationKey(ThaumicAttempts.MODID + ".riftonomicon");
        setRegistryName(ThaumicAttempts.MODID, "riftonomicon");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(this.blockState.getBaseState().withProperty(PART, Part.CORE));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        Part part = state.getValue(PART);
        return part == Part.CORE || part == Part.IMPETUS_PORT
                || part == Part.TRANSMUTATION_ESSENTIA_INPUT
                || part == Part.TRANSMUTATION_IMPETUS_INPUT;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        Part part = state.getValue(PART);
        if (part == Part.CORE) return new TileRiftonomicon();
        if (part == Part.IMPETUS_PORT) return new TileRiftonomiconImpetusPort();
        if (part == Part.TRANSMUTATION_ESSENTIA_INPUT) return new TileRiftonomiconTransmutationEssentiaInput();
        if (part == Part.TRANSMUTATION_IMPETUS_INPUT) return new TileRiftonomiconTransmutationImpetusInput();
        return null;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return getLocalAABB(state.getValue(PART));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return getCollisionAABB(state.getValue(PART));
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> collidingBoxes, @Nullable net.minecraft.entity.Entity entityIn,
                                      boolean isActualState) {
        AxisAlignedBB collision = getCollisionAABB(state.getValue(PART));
        if (collision != NULL_AABB) {
            addCollisionBoxToList(pos, entityBox, collidingBoxes, collision);
        }

    }

    private static AxisAlignedBB getCollisionAABB(Part part) {
        if (part == Part.CORE || part == Part.TOP || part == Part.IMPETUS_PORT
                || part == Part.TRANSMUTATION_IMPETUS_INPUT) {
            return NULL_AABB;
        }
        return getLocalAABB(part);
    }

    private static AxisAlignedBB getLocalAABB(Part part) {
        switch (part) {
            case CORE:
            case TOP:
            case TRANSMUTATION_IMPETUS_INPUT:
                return TOP_CRYSTAL;
            case IMPETUS_PORT:
                return IMPETUS_PORT;
            case BASE:
            case EDGE:
            case CORNER:
            case TRANSMUTATION_ESSENTIA_INPUT:
                return FULL;
            case INNER:
                return BASE_INNER;
            case COLUMN:
                return SECOND_LAYER_COLUMN;
            default:
                return FULL;
        }
    }

    @Nullable
    private static BlockPos findCenter(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) return null;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != TABlocks.RIFTONOMICON) return null;

        Part part = state.getValue(PART);
        int baseY;
        switch (part) {
            case COLUMN:
                baseY = pos.getY() - 1;
                break;
            case TOP:
                baseY = pos.getY() - 2;
                break;
            case CORE:
            case IMPETUS_PORT:
                baseY = pos.getY() - 3;
                break;
            case TRANSMUTATION_IMPETUS_INPUT:
                baseY = pos.getY() - 4;
                break;
            default:
                baseY = pos.getY();
                break;
        }

        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        int coreY = baseY + CORE_Y_OFFSET;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos corePos = new BlockPos(pos.getX() + dx, coreY, pos.getZ() + dz);
                if (!(world.getTileEntity(corePos) instanceof TileRiftonomicon)) continue;
                int distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = corePos.down(CORE_Y_OFFSET);
                }
            }
        }
        return best;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean isTranslucent(IBlockState state) {
        return true;
    }

    @Override
    public boolean getUseNeighborBrightness(IBlockState state) {
        return true;
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        BlockPos center = findCenter(world, pos);
        TileRiftonomicon tile = getRiftonomiconTile(world, center);
        if (tile == null) return false;

        ItemStack held = player.getHeldItem(hand);
        int slot = getProtocolSlot(center, pos);
        if (held.getItem() instanceof ItemPrism && tile.hasTransmutationProtocol()) {
            if (world.isRemote) return tile.getTransmutationPrismCount() < tile.getTransmutationAltarLimit();
            if (tile.insertTransmutationPrism(held)) {
                if (!player.capabilities.isCreativeMode) held.shrink(1);
                player.inventoryContainer.detectAndSendChanges();
                playProtocolSlotSound(world, pos, 0.8F, 1.25F);
            }
            return true;
        }
        if (slot < 0 && held.getItem() == ModBlocksItems.TRANSMUTATION_CORE
                && tile.hasTransmutationProtocol()) {
            if (!world.isRemote && tile.tryStartTransmutation()) {
                if (!player.capabilities.isCreativeMode) held.shrink(1);
                player.inventoryContainer.detectAndSendChanges();
            }
            return true;
        }
        if (slot < 0 && player.isSneaking() && held.isEmpty()
                && tile.hasTransmutationProtocol() && !tile.getTransmutationItem().isEmpty()) {
            if (!world.isRemote) {
                ItemStack extracted = tile.removeTransmutationItem();
                player.setHeldItem(hand, extracted);
                player.inventoryContainer.detectAndSendChanges();
            }
            return true;
        }
        if (slot < 0 && player.isSneaking() && held.isEmpty() && tile.hasTransmutationProtocol()) {
            if (!world.isRemote) {
                ItemStack extracted = tile.removeLastTransmutationPrism();
                if (!extracted.isEmpty()) player.setHeldItem(hand, extracted);
                player.inventoryContainer.detectAndSendChanges();
            }
            return true;
        }
        if (canInteractWithNavigationRunes(tile, held, player, slot)) {
            return interactWithNavigationRunes(world, pos, player, hand, tile, held);
        }

        if (slot < 0) return false;

        boolean canInsert = tile.isProtocolSlotEmpty(slot) && tile.canInsertProtocolItem(held);
        boolean canExtract = held.isEmpty() && !tile.isProtocolSlotEmpty(slot);
        if (!canInsert && !canExtract) return false;
        if (world.isRemote) return true;

        if (canInsert) {
            ItemStack stored = held.copy();
            stored.setCount(1);
            tile.setProtocolInSlot(slot, stored);
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
                if (held.getCount() <= 0) {
                    player.setHeldItem(hand, ItemStack.EMPTY);
                }
            }
            playProtocolSlotSound(world, pos, 0.8F, 1.15F);
            return true;
        }

        ItemStack extracted = tile.removeProtocolFromSlot(slot);
        if (!extracted.isEmpty()) {
            if (!player.inventory.addItemStackToInventory(extracted)) {
                player.dropItem(extracted, false);
            }
            if (extracted.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL
                    && extracted.getMetadata() == ItemRiftonomiconProtocol.META_NAVIGATION_PROTOCOL) {
                giveStacksToPlayerOrDrop(player, tile.removeAllNavigationRunes());
            } else if (extracted.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL
                    && extracted.getMetadata() == ItemRiftonomiconProtocol.META_SPATIAL_SUBSYSTEM) {
                giveStacksToPlayerOrDrop(player, tile.removeNavigationRunesBeyondLimit());
            } else if (extracted.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL
                    && extracted.getMetadata() == ItemRiftonomiconProtocol.META_TRANSMUTATION_PROTOCOL) {
                ItemStack transmutationItem = tile.removeTransmutationItem();
                if (!transmutationItem.isEmpty() && !player.inventory.addItemStackToInventory(transmutationItem)) {
                    player.dropItem(transmutationItem, false);
                }
                giveStacksToPlayerOrDrop(player, tile.removeAllTransmutationPrisms());
            } else if (extracted.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL
                    && extracted.getMetadata() == ItemRiftonomiconProtocol.META_CREATION_PROTOCOL) {
                giveStacksToPlayerOrDrop(player, tile.removeAllCreationItems());
            } else if (extracted.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL
                    && extracted.getMetadata() == ItemRiftonomiconProtocol.META_SCALING_SUBSYSTEM) {
                giveStacksToPlayerOrDrop(player, tile.removeCreationSamplesBeyondLimit());
            }
            player.inventoryContainer.detectAndSendChanges();
            playProtocolSlotSound(world, pos, 0.7F, 0.85F);
        }
        return true;
    }

    @Override
    public float getAmbientOcclusionLightValue(IBlockState state) {
        return 1.0F;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!MultiblockBreakHelper.isRestoring()) {
            MultiblockBreakHelper.Match match = MultiblockBreakHelper.findMatch(
                    world, pos, state, TAMultiblockTriggers.RIFTONOMICON_MB_ID
            );
            if (match != null) {
                dropStoredProtocols(world, pos, match.getCenter());
                MultiblockBreakHelper.restoreMatch(world, pos, match);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        MultiblockBreakHelper.addSourceDrops(
                drops, world, pos, state, fortune, TAMultiblockTriggers.RIFTONOMICON_MB_ID
        );
    }

    private static boolean isRiftonomiconProtocol(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModBlocksItems.RIFTONOMICON_PROTOCOL;
    }

    private static boolean canInteractWithNavigationRunes(TileRiftonomicon tile, ItemStack held,
                                                          EntityPlayer player, int protocolSlot) {
        return protocolSlot < 0 && held.isEmpty() && player.isSneaking() && tile.hasNavigationRunes();
    }

    private static boolean interactWithNavigationRunes(World world, BlockPos pos, EntityPlayer player, EnumHand hand,
                                                       TileRiftonomicon tile, ItemStack held) {
        if (world.isRemote) return true;

        ItemStack extracted = tile.removeLastNavigationRune();
        if (!extracted.isEmpty()) {
            if (!player.inventory.addItemStackToInventory(extracted)) {
                player.dropItem(extracted, false);
            }
            player.inventoryContainer.detectAndSendChanges();
            playProtocolSlotSound(world, pos, 0.65F, 0.9F);
        }
        return true;
    }

    @Nullable
    private static TileRiftonomicon getRiftonomiconTile(IBlockAccess world, @Nullable BlockPos center) {
        if (world == null || center == null) return null;
        TileEntity tile = world.getTileEntity(center.up(CORE_Y_OFFSET));
        return tile instanceof TileRiftonomicon ? (TileRiftonomicon) tile : null;
    }

    private static int getProtocolSlot(@Nullable BlockPos center, BlockPos pos) {
        if (center == null || pos == null || pos.getY() - center.getY() != 1) return -1;

        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        if (Math.abs(dx) != 2 || Math.abs(dz) != 2) return -1;

        if (dx > 0 && dz < 0) return 0;
        if (dx < 0 && dz < 0) return 1;
        if (dx > 0 && dz > 0) return 2;
        return 3;
    }

    private static void dropStoredProtocols(World world, BlockPos brokenPos, BlockPos center) {
        if (world == null || world.isRemote) return;
        TileRiftonomicon tile = getRiftonomiconTile(world, center);
        if (tile == null) return;

        ItemStack transmutationItem = tile.removeTransmutationItem();
        if (!transmutationItem.isEmpty()) {
            InventoryHelper.spawnItemStack(world,
                    brokenPos.getX() + 0.5D, brokenPos.getY() + 0.5D, brokenPos.getZ() + 0.5D,
                    transmutationItem);
        }

        for (ItemStack prism : tile.removeAllTransmutationPrisms()) {
            if (!prism.isEmpty()) InventoryHelper.spawnItemStack(world,
                    brokenPos.getX() + 0.5D, brokenPos.getY() + 0.5D, brokenPos.getZ() + 0.5D, prism);
        }

        for (ItemStack creationItem : tile.removeAllCreationItems()) {
            if (!creationItem.isEmpty()) InventoryHelper.spawnItemStack(world,
                    brokenPos.getX() + 0.5D, brokenPos.getY() + 0.5D, brokenPos.getZ() + 0.5D,
                    creationItem);
        }

        NonNullList<ItemStack> protocols = tile.removeAllProtocols();
        for (ItemStack stack : protocols) {
            if (!stack.isEmpty()) {
                InventoryHelper.spawnItemStack(world,
                        brokenPos.getX() + 0.5D,
                        brokenPos.getY() + 0.5D,
                        brokenPos.getZ() + 0.5D,
                        stack);
            }
        }

        NonNullList<ItemStack> runes = tile.removeAllNavigationRunes();
        for (ItemStack stack : runes) {
            if (!stack.isEmpty()) {
                InventoryHelper.spawnItemStack(world,
                        brokenPos.getX() + 0.5D,
                        brokenPos.getY() + 0.5D,
                        brokenPos.getZ() + 0.5D,
                        stack);
            }
        }
    }

    private static void giveStacksToPlayerOrDrop(EntityPlayer player, NonNullList<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropItem(stack, false);
            }
        }
    }

    private static void playProtocolSlotSound(World world, BlockPos pos, float volume, float pitch) {
        world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM, SoundCategory.BLOCKS, volume, pitch);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, PART);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        Part[] parts = Part.values();
        if (meta < 0 || meta >= parts.length) {
            return getDefaultState();
        }
        return getDefaultState().withProperty(PART, parts[meta]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(PART).ordinal();
    }

    public enum Part implements IStringSerializable {
        CORE("core"),
        BASE("base"),
        INNER("inner"),
        EDGE("edge"),
        CORNER("corner"),
        COLUMN("column"),
        TOP("top"),
        IMPETUS_PORT("impetus_port"),
        TRANSMUTATION_ESSENTIA_INPUT("transmutation_essentia_input"),
        TRANSMUTATION_IMPETUS_INPUT("transmutation_impetus_input");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
