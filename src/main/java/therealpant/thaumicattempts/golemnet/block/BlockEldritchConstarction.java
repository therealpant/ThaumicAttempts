package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.init.ModBlocksItems;
import therealpant.thaumicattempts.world.tile.TileEldritchConstruction;

public class BlockEldritchConstarction extends Block {
    public static final PropertyEnum<Variant> VARIANT =
            PropertyEnum.create("variant", Variant.class);

    public BlockEldritchConstarction() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.STONE);
        setTranslationKey(ThaumicAttempts.MODID + ".eldritch_constarction");
        setRegistryName(ThaumicAttempts.MODID, "eldritch_constarction");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setDefaultState(blockState.getBaseState().withProperty(VARIANT, Variant.BASE));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEldritchConstruction();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        Variant current = state.getValue(VARIANT);

        if (current.isGemmed() && isPickaxe(held)) {
            if (!world.isRemote) {
                world.setBlockState(pos, state.withProperty(VARIANT, Variant.BASE), 3);
                spawnAsEntity(world, pos, new ItemStack(current.getGem()));
                world.playEvent(2001, pos, Block.getStateId(state));
                if (!player.capabilities.isCreativeMode) held.damageItem(1, player);
            }
            return true;
        }

        Variant inserted = Variant.fromGem(held.getItem());
        if (inserted == null || current != Variant.BASE) return false;

        if (!world.isRemote) {
            world.setBlockState(pos, state.withProperty(VARIANT, inserted), 3);
            if (!player.capabilities.isCreativeMode) held.shrink(1);
            world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                    SoundCategory.BLOCKS, 0.7F, 0.9F + world.rand.nextFloat() * 0.2F);
        }
        return true;
    }

    private static boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ItemPickaxe
                || stack.getItem().getToolClasses(stack).contains("pickaxe"));
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
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(VARIANT, Variant.fromMeta(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(VARIANT).meta;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target,
                                  World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(this, 1, getMetaFromState(state));
    }

    @Override
    public void getSubBlocks(net.minecraft.creativetab.CreativeTabs tab, NonNullList<ItemStack> items) {
        if (tab == ThaumicAttempts.CREATIVE_TAB
                || tab == net.minecraft.creativetab.CreativeTabs.SEARCH) {
            for (Variant variant : Variant.values()) {
                items.add(new ItemStack(this, 1, variant.meta));
            }
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, VARIANT);
    }

    public static boolean matches(IBlockAccess world, BlockPos pos, Variant variant) {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BlockEldritchConstarction
                && state.getValue(VARIANT) == variant;
    }

    public enum Variant implements IStringSerializable {
        BASE(0, "base"),
        AMBER(1, "amber"),
        AMETHYST(2, "amethyst"),
        DIAMOND(3, "diamond");

        private final int meta;
        private final String name;

        Variant(int meta, String name) {
            this.meta = meta;
            this.name = name;
        }

        public int getMeta() {
            return meta;
        }

        public boolean isGemmed() {
            return this != BASE;
        }

        public Item getGem() {
            switch (this) {
                case AMBER: return ModBlocksItems.RIFT_EMBER;
                case AMETHYST: return ModBlocksItems.RIFT_AMETIST;
                case DIAMOND: return ModBlocksItems.RIFT_BRILIANT;
                default: return null;
            }
        }

        @Override
        public String getName() {
            return name;
        }

        public static Variant fromMeta(int meta) {
            for (Variant variant : values()) {
                if (variant.meta == meta) return variant;
            }
            return BASE;
        }

        private static Variant fromGem(Item item) {
            if (item == null) return null;
            if (item == ModBlocksItems.RIFT_EMBER) return AMBER;
            if (item == ModBlocksItems.RIFT_AMETIST) return AMETHYST;
            if (item == ModBlocksItems.RIFT_BRILIANT) return DIAMOND;
            return null;
        }
    }
}
