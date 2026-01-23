package therealpant.thaumicattempts.world.item;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.world.block.BlockAnomalyBed;

public class ItemAnomalySeeds extends ItemSeeds {
    public ItemAnomalySeeds(Block crops, Block soil) {
        super(crops, soil);
        setCreativeTab(ThaumicAttempts.CREATIVE_TAB);
        setTranslationKey(ThaumicAttempts.MODID + ".anomaly_seeds");
        setRegistryName(ThaumicAttempts.MODID, "ta_anomaly_seeds");
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (facing != EnumFacing.UP) {
            return EnumActionResult.FAIL;
        }
        if (!player.canPlayerEdit(pos.offset(facing), facing, stack)) {
            return EnumActionResult.FAIL;
        }
        IBlockState soilState = worldIn.getBlockState(pos);
        if (!(soilState.getBlock() instanceof BlockAnomalyBed)) {
            return EnumActionResult.FAIL;
        }
        BlockAnomalyBed.BedState bedState = soilState.getValue(BlockAnomalyBed.BED_STATE);
        if (bedState == BlockAnomalyBed.BedState.NORMAL) {
            return EnumActionResult.FAIL;
        }
        if (!worldIn.isAirBlock(pos.up())) {
            return EnumActionResult.FAIL;
        }
        if (!soilState.getBlock().canSustainPlant(soilState, worldIn, pos, EnumFacing.UP, this)) {
            return EnumActionResult.FAIL;
        }
        worldIn.setBlockState(pos.up(), this.getPlant(worldIn, pos.up()));
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
}