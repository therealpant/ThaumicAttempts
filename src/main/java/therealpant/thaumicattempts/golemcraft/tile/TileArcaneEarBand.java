package therealpant.thaumicattempts.golemcraft.tile;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.common.blocks.IBlockEnabled;
import thaumcraft.common.lib.utils.BlockStateUtils;
import thaumcraft.common.tiles.devices.TileArcaneEar;

import java.util.ArrayList;

public class TileArcaneEarBand extends thaumcraft.common.tiles.devices.TileArcaneEar {
    // твоя логика, поля, методы


    /** Базовая нота (0..24), вокруг неё строится диапазон */
    public int baseNote = 0;

    public int getBaseNote() { return baseNote & 0xFF; }



    /** Текущая постоянная сила редстоуна (0..15) */
    private int currentPower = 0;

    /** Предыдущая услышанная нота в диапазоне, для правила «две подряд → 0» */
    private int prevNoteInRange = -1; // -1 = ещё не было
    /** Чередование при одинаковых нотах: false -> следующая такая же выключит, true -> включит */
    private boolean flipOnSame = false;

    public int getCurrentPower() {
        return currentPower;
    }

    public void changePitch() {
        baseNote = (baseNote + 1) % 25;
        this.note = (byte) (baseNote & 0xFF); // <- ключевая строка: обновляем поле родителя
        markDirty();
        syncToClient();
    }


    private void syncToClient() {
        if (world != null && !world.isRemote) {
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
            // если надо пробудить соседей — можно дополнительно pingNeighbors()
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        ArrayList<Integer[]> nbe = thaumcraft.common.tiles.devices.TileArcaneEar.noteBlockEvents
                .get(world.provider.getDimension());
        if (nbe == null || nbe.isEmpty()) return;

        final int rangeStart = baseNote;
        final int rangeEnd   = Math.min(24, baseNote + 14);

        for (Integer[] ev : nbe) {
            int ex = ev[0], ey = ev[1], ez = ev[2];
            int evTone = ev[3], evNote = ev[4];

            if (this.getDistanceSq(ex + 0.5, ey + 0.5, ez + 0.5) > 4096.0) continue;
            if (evTone != (tone & 0xFF)) continue;
            if (evNote < rangeStart || evNote > rangeEnd) continue;

            // ================= одинаковая нота подряд: чередуем OFF/ON =================
            if (evNote == prevNoteInRange) {
                if (!flipOnSame) {
                    // выключаемся
                    if (currentPower != 0) {
                        currentPower = 0;
                        setEnabledSafely(false);
                        markDirty();
                        notifyNeighbors();
                    }
                    flipOnSame = true; // следующая такая же включит
                } else {
                    // включаемся снова силой по позиции в диапазоне
                    int newPower = 1 + (evNote - rangeStart);
                    if (newPower > 15) newPower = 15;

                    if (currentPower != newPower) {
                        currentPower = newPower;
                        setEnabledSafely(true);
                        markDirty();
                        notifyNeighbors();
                    }
                    flipOnSame = false; // следующая такая же выключит
                }
                // prevNoteInRange остаётся тем же (evNote), чтобы серия работала как тумблер
                break;
            }

            // ================= другая нота: всегда включаемся нужной силой и сбрасываем парность =================
            int newPower = 1 + (evNote - rangeStart);
            if (newPower > 15) newPower = 15;

            if (currentPower != newPower) {
                currentPower = newPower;
                setEnabledSafely(true);
                markDirty();
                notifyNeighbors();
            }
            prevNoteInRange = evNote;
            flipOnSame = false; // новая нота сбрасывает парность
            break;
        }
    }


    /** Уведомление соседей/чанг о смене состояния (чтобы редстоун обновился сразу) */
    private void notifyNeighbors() {
        world.notifyNeighborsOfStateChange(pos, getBlockType(), true);
        EnumFacing f = BlockStateUtils.getFacing(getBlockMetadata()).getOpposite();
        world.notifyNeighborsOfStateChange(pos.offset(f), getBlockType(), true);
        IBlockState s2 = world.getBlockState(pos);
        world.markAndNotifyBlock(pos, world.getChunk(pos), s2, s2, 3);
    }

    private void setEnabledSafely(boolean enable) {
        IBlockState state = world.getBlockState(pos);
        if (!state.getPropertyKeys().contains(thaumcraft.common.blocks.IBlockEnabled.ENABLED)) return;

        boolean cur = thaumcraft.common.lib.utils.BlockStateUtils.isEnabled(state);
        if (cur == enable) return;

        TileEntity keep = world.getTileEntity(pos);
        world.setBlockState(pos, state.withProperty(thaumcraft.common.blocks.IBlockEnabled.ENABLED, enable), 3);
        if (keep != null) {
            keep.validate();
            world.setTileEntity(pos, keep);
        }
        // соседям сообщим отдельно через notifyNeighbors()
    }

    /** Пересчёт тона по блоку «за ухом» (как у оригинального уха) */
    public void updateTone() {
        try {
            EnumFacing facing = BlockStateUtils.getFacing(getBlockMetadata()).getOpposite();
            BlockPos back = pos.offset(facing);
            IBlockState ib = world.getBlockState(back);
            Material mat = ib.getMaterial();

            byte t = 0;
            if (mat == Material.ROCK)  t = 1;
            if (mat == Material.SAND)  t = 2;
            if (mat == Material.GLASS) t = 3;
            if (mat == Material.WOOD)  t = 4;

            Block b = ib.getBlock();
            if (b == Blocks.CLAY)       t = 5;
            if (b == Blocks.GOLD_BLOCK) t = 6;
            if (b == Blocks.WOOL)       t = 7;
            if (b == Blocks.PACKED_ICE) t = 8;
            if (b == Blocks.BONE_BLOCK) t = 9;

            this.tone = t;
            markDirty();
        } catch (Exception ignored) {}
    }

    /* ================= NBT ================= */

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("baseNoteInt", baseNote);
        nbt.setByte("tone", tone);
        nbt.setInteger("currentPower", currentPower);
        nbt.setInteger("prevNoteInRange", prevNoteInRange);
        nbt.setByte("BaseNote", (byte)(baseNote & 0xFF));

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("baseNoteInt")) baseNote = nbt.getInteger("baseNoteInt");
        else baseNote = nbt.getByte("baseNote") & 0xFF; // на случай старых миров

        tone = nbt.getByte("tone");
        currentPower = nbt.getInteger("currentPower");
        prevNoteInRange = nbt.getInteger("prevNoteInRange");

        baseNote = nbt.getByte("BaseNote") & 0xFF;

        if (nbt.hasKey("BaseNoteInt")) {
            baseNote = Math.max(0, Math.min(24, nbt.getInteger("BaseNoteInt")));
            this.note = (byte) (baseNote & 0xFF); // <- синк в родительское поле
        } else {
            // на случай старых миров: выровнять baseNote по родительскому note
            baseNote = this.note & 0xFF;
        }

        // нормализация
        if (baseNote < 0) baseNote = 0;
        if (baseNote > 24) baseNote = 24;
        if (currentPower < 0) currentPower = 0;
        if (currentPower > 15) currentPower = 15;
        if (prevNoteInRange < -1 || prevNoteInRange > 24) prevNoteInRange = -1;
    }


    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }

    @SideOnly(Side.CLIENT)
    public void clientSetBaseNote(int n) {
        baseNote = (n & 0xFF) % 25;
        this.note = (byte) (baseNote & 0xFF); // <- обновляем поле родителя, чтобы рендер увидел
    }


}
