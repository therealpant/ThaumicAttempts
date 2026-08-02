package therealpant.thaumicattempts.world;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import thaumcraft.common.entities.EntitySpecialItem;

/**
 * Thaumcraft's boss-drop item effect with a persistent lifetime.
 */
public class EntityAnomalyWellResult extends EntitySpecialItem {
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private boolean anchorInitialized;

    public EntityAnomalyWellResult(World world) {
        super(world);
        this.lifespan = Integer.MAX_VALUE;
    }

    public EntityAnomalyWellResult(World world, double x, double y, double z,
                                   ItemStack stack) {
        super(world, x, y, z, stack);
        this.lifespan = Integer.MAX_VALUE;
        setAnchor(x, y, z);
    }

    @Override
    public void onUpdate() {
        if (!anchorInitialized) {
            setAnchor(this.posX, this.posY, this.posZ);
        }
        super.onUpdate();
        if (this.isDead) return;

        this.setPosition(anchorX, anchorY, anchorZ);
        this.prevPosX = anchorX;
        this.prevPosY = anchorY;
        this.prevPosZ = anchorZ;
        this.lastTickPosX = anchorX;
        this.lastTickPosY = anchorY;
        this.lastTickPosZ = anchorZ;
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.fallDistance = 0.0F;
        this.noClip = true;
    }

    private void setAnchor(double x, double y, double z) {
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        anchorInitialized = true;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        // EntityItem stores age as a short. Keeping it near zero prevents an
        // overflow from distorting Thaumcraft's age-based ray effect on reload.
        compound.setShort("Age", (short) 0);
        compound.setInteger("Lifespan", Integer.MAX_VALUE);
        compound.setDouble("WellAnchorX", anchorX);
        compound.setDouble("WellAnchorY", anchorY);
        compound.setDouble("WellAnchorZ", anchorZ);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.lifespan = Integer.MAX_VALUE;
        if (compound.hasKey("WellAnchorX", 6)
                && compound.hasKey("WellAnchorY", 6)
                && compound.hasKey("WellAnchorZ", 6)) {
            setAnchor(
                    compound.getDouble("WellAnchorX"),
                    compound.getDouble("WellAnchorY"),
                    compound.getDouble("WellAnchorZ"));
        } else {
            setAnchor(this.posX, this.posY, this.posZ);
        }
    }
}
