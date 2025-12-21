package therealpant.thaumicattempts.golemnet.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.blocks.BlocksTC;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.init.TABlocks;

public class BlockMirrorManagerCore extends Block {
    private static final String DUST_ID = "thaumcraft:salis_mundus";

    public BlockMirrorManagerCore() {
        super(Material.ROCK);
        setHardness(2.5F);
        setResistance(12.0F);
        setTranslationKey(ThaumicAttempts.MODID + ".mirror_manager_core");
        setRegistryName(ThaumicAttempts.MODID, "mirror_manager_core");
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        ItemStack held = player.getHeldItem(hand);
        if (!isMagicDust(held)) return false;

        if (!isValidStructure(world, pos)) return false;

        if (!player.capabilities.isCreativeMode) {
            held.shrink(1);
        }

        world.setBlockState(pos, TABlocks.MIRROR_MANAGER.getDefaultState(), 3);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileMirrorManager) {
            ((TileMirrorManager) te).setOwnerUuid(player.getUniqueID().toString());
        }
        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    private static boolean isMagicDust(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item dust = Item.getByNameOrId(DUST_ID);
        return dust != null && stack.getItem() == dust;
    }

    private static boolean isValidStructure(World world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock() == TABlocks.MIRROR_MANAGER_BASE
                && world.getBlockState(pos.up()).getBlock() == BlocksTC.stoneEldritchTile;
    }
}