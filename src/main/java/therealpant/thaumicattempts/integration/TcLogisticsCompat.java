// therealpant/thaumicattempts/integration/TcLogisticsCompat.java
package therealpant.thaumicattempts.integration;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import thaumcraft.api.ThaumcraftInvHelper;
import thaumcraft.common.golems.seals.SealEntity;
import thaumcraft.common.golems.seals.SealHandler;
import thaumcraft.common.golems.seals.SealProvide;
import therealpant.thaumicattempts.ThaumicAttempts;

import javax.annotation.Nullable;
import java.util.*;

// therealpant/thaumicattempts/integration/TcLogisticsCompat.java
public final class TcLogisticsCompat {
    public static final class Page {
        public final List<ItemStack> window;   // до 7×9, КАЖДАЯ с count=1
        public final List<Integer>  counts;    // реальные количества на иконку
        public final int startRow;             // как в TC: индекс первой строки окна
        public final int totalRows;            // как в TC: items.size()/9 - 8
        public Page(List<ItemStack> w, List<Integer> c, int sr, int tr) {
            this.window=w; this.counts=c; this.startRow=sr; this.totalRows=tr;
        }
    }

    public static Page build(World w, @Nullable String ownerUuid, BlockPos center,
                             int startRow, String search) {
        // Суммируем в int, а не в ItemStack.count
        TreeMap<String, Integer> sums = new TreeMap<>();
        HashMap<String, ItemStack> repr = new HashMap<>();

        String q = (search == null ? "" : search).toLowerCase(Locale.ROOT);

        for (SealEntity seal : SealHandler.getSealsInRange(w, center, 32)) {
            if (!(seal.getSeal() instanceof SealProvide)) continue;
            if (ownerUuid != null && !ownerUuid.equals(seal.getOwner())) continue;

            IItemHandler handler = ThaumcraftInvHelper.getItemHandlerAt(
                    w, seal.getSealPos().pos, seal.getSealPos().face);
            if (handler == null) continue;

            SealProvide provide = (SealProvide) seal.getSeal();

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack st = handler.getStackInSlot(slot).copy();
                if (st.isEmpty()) continue;
                if (!provide.matchesFilters(st)) continue;
                if (!q.isEmpty() && !st.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) continue;

                String key = st.getDisplayName() + st.getItemDamage() + st.getTagCompound();
                sums.merge(key, st.getCount(), Integer::sum);
                // иконка всегда с count=1 — чтобы не словить NBT Count(byte)
                if (!repr.containsKey(key)) {
                    ItemStack icon = st.copy();
                    icon.setCount(1);
                    repr.put(key, icon);
                }
            }
        }

        int tr = sums.size() / 9 - 8;
        if (tr < 0) tr = 0;
        int sr = Math.max(0, Math.min(startRow, tr));

        List<ItemStack> window = new ArrayList<>(63);
        List<Integer>  counts = new ArrayList<>(63);
        int j = 0, qv = 0;
        for (String key : sums.keySet()) {
            j++;
            if (j > sr * 9) {
                window.add(repr.get(key));           // иконка с count=1
                counts.add(sums.get(key));           // реальное число
                if (++qv >= 63) break;
            }
        }
        return new Page(window, counts, sr, tr);
    }
}

