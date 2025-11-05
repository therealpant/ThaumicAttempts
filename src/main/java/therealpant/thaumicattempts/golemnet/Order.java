package therealpant.thaumicattempts.golemnet;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class Order {

    public enum Type {
        DELIVERY,
        CRAFT   // <-- ОБЯЗАТЕЛЬНО есть
    }

    public Type type = Type.DELIVERY;
    public ItemStack wanted = ItemStack.EMPTY;
    public int amount = 0;

    public BlockPos destination;
    public int destSide = -1;

    public BlockPos patternRequester; // для CRAFT
    public int queueId = 0;

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("t", type.name());
        if (!wanted.isEmpty()) nbt.setTag("w", wanted.serializeNBT());
        nbt.setInteger("a", amount);
        if (destination != null) {
            nbt.setLong("d", destination.toLong());
            nbt.setInteger("ds", destSide);
        }
        if (patternRequester != null) nbt.setLong("pr", patternRequester.toLong());
        nbt.setInteger("q", queueId);
        return nbt;
    }

    public static Order readFromNBT(NBTTagCompound nbt) {
        Order o = new Order();
        o.type = Type.valueOf(nbt.getString("t"));
        if (nbt.hasKey("w")) o.wanted = new ItemStack(nbt.getCompoundTag("w"));
        o.amount = nbt.getInteger("a");
        if (nbt.hasKey("d")) {
            o.destination = BlockPos.fromLong(nbt.getLong("d"));
            o.destSide = nbt.getInteger("ds");
        }
        if (nbt.hasKey("pr")) o.patternRequester = BlockPos.fromLong(nbt.getLong("pr"));
        o.queueId = nbt.getInteger("q");
        return o;
    }

    public static Order delivery(ItemStack wanted, int amount, BlockPos destination, int destSide) {
        Order o = new Order();
        o.type = Type.DELIVERY;
        o.wanted = wanted.copy();
        o.amount = amount;
        o.destination = destination;
        o.destSide = destSide;
        return o;
    }
    public static Order craft(ItemStack wanted, int amount, @javax.annotation.Nullable BlockPos patternRequester) {
        Order o = new Order();
        o.type = Type.CRAFT;
        o.wanted = wanted.copy();
        o.amount = amount;
        o.patternRequester = patternRequester;
        return o;
    }

}
