package therealpant.thaumicattempts.world.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import therealpant.thaumicattempts.ThaumicAttempts;

/** Invisible interaction/collision cell belonging to a drainage controller below it. */
public class BlockDrainageNodePart extends Block {
    public BlockDrainageNodePart() {
        super(Material.ROCK);
        setRegistryName(ThaumicAttempts.MODID,"drainage_node_part");
        setTranslationKey(ThaumicAttempts.MODID+".drainage_node_part");
        setHardness(6.0F);
        setResistance(30.0F);
        setSoundType(SoundType.STONE);
        setCreativeTab(null);
        setLightOpacity(0);
    }

    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public int quantityDropped(java.util.Random random) { return 0; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) {
        return FULL_BLOCK_AABB;
    }

    @Override
    public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
                                    EnumHand hand,EnumFacing facing,float hitX,float hitY,float hitZ) {
        BlockPos controller=DrainageNodeStructure.findController(world,pos);
        if (controller==null) return false;
        return ((BlockDrainageNode)world.getBlockState(controller).getBlock()).onBlockActivated(
                world,controller,world.getBlockState(controller),player,hand,facing,hitX,hitY,hitZ);
    }

    @Override
    public void neighborChanged(IBlockState state,World world,BlockPos pos,Block changedBlock,BlockPos changedPos) {
        if (!world.isRemote && !DrainageNodeStructure.isDismantling()
                && DrainageNodeStructure.findController(world,pos)==null) world.setBlockToAir(pos);
    }

    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        if (!world.isRemote && !DrainageNodeStructure.isDismantling()) {
            BlockPos controller=DrainageNodeStructure.findController(world,pos);
            if (controller!=null) DrainageNodeStructure.dismantleFromPart(world,controller);
        }
        super.breakBlock(world,pos,state);
    }
}
