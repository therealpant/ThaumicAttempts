package therealpant.thaumicattempts.golemnet.planner;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import therealpant.thaumicattempts.api.PatternResourceList;
import therealpant.thaumicattempts.golemcraft.item.ItemInfusionPattern;
import therealpant.thaumicattempts.golemcraft.item.ItemResourceList;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter;
import therealpant.thaumicattempts.golemnet.tile.TileInfusionRequester;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;
import therealpant.thaumicattempts.golemnet.tile.TileResourceRequester;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NetworkOperationAdapters {
    private NetworkOperationAdapters() {}

    public static List<INetworkOperationSource> collectSources(List<TileEntity> tiles) {
        List<INetworkOperationSource> out = new ArrayList<>();
        if (tiles == null) return out;
        for (TileEntity te : tiles) {
            if (te instanceof TileResourceRequester) out.add(new ResourceRequesterSource((TileResourceRequester) te));
            else if (te instanceof TileInfusionRequester) out.add(new InfusionRequesterSource((TileInfusionRequester) te));
            else if (te instanceof TilePatternRequester) out.add(new PatternRequesterSource((TilePatternRequester) te));
        }
        return out;
    }

    private static int findPatternIndexByResult(IItemHandler patt, ItemStack like) {
        if (patt == null || like == null || like.isEmpty()) return -1;
        for (int i = 0; i < patt.getSlots(); i++) {
            ItemStack pattern = patt.getStackInSlot(i);
            if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) continue;
            ItemStack preview = ItemResourceList.getPreviewOrFirstEntry(pattern);
            if (!preview.isEmpty() && ResourceIdentity.sameResource(preview, like)) return i;
        }
        return -1;
    }

    private static final class ResourceRequesterSource implements INetworkOperationSource {
        private final TileResourceRequester tile;

        private ResourceRequesterSource(TileResourceRequester tile) { this.tile = tile; }

        @Override
        public List<ItemKey> getProvidedResults() {
            IItemHandler patt = tile.getPatternHandler();
            if (patt == null) return Collections.emptyList();
            List<ItemKey> out = new ArrayList<>();
            for (int i = 0; i < patt.getSlots(); i++) {
                ItemStack pattern = patt.getStackInSlot(i);
                if (pattern.isEmpty() || !(pattern.getItem() instanceof ItemResourceList)) continue;
                ItemStack preview = ItemResourceList.getPreviewOrFirstEntry(pattern);
                if (!preview.isEmpty()) out.add(ItemKey.of(preview));
            }
            return out;
        }

        @Override
        public int getOutputCountFor(ItemKey key) {
            return key == null ? 0 : Math.max(1, key.toStack(1).getCount());
        }

        @Override
        public List<RequiredInput> getRequiredInputsFor(ItemKey key, int times) {
            ItemStack like = key == null ? ItemStack.EMPTY : key.toStack(1);
            IItemHandler patt = tile.getPatternHandler();
            int idx = findPatternIndexByResult(patt, like);
            if (idx < 0) return Collections.emptyList();

            ItemStack pattern = patt.getStackInSlot(idx);
            List<PatternResourceList.Entry> entries = PatternResourceList.build(pattern);
            List<RequiredInput> out = new ArrayList<>();
            for (PatternResourceList.Entry e : entries) {
                out.add(new RequiredInput(e.getKey(), Math.max(1, e.getCount()) * Math.max(1, times)));
            }
            return out;
        }

        @Override
        public boolean enqueueExecution(ItemKey key, int times) {
            return tile.enqueueFromPatternRequester(key.toStack(1), Math.max(1, times)) > 0;
        }

        @Override
        public ProviderType getType() {
            return ProviderType.RESOURCE_REQUESTER;
        }

        @Override
        public String getDebugName() {
            BlockPos p = tile.getPos();
            return "ResourceRequester@" + p;
        }
    }

    private static final class InfusionRequesterSource implements INetworkOperationSource {
        private final TileInfusionRequester tile;

        private InfusionRequesterSource(TileInfusionRequester tile) { this.tile = tile; }

        @Override
        public List<ItemKey> getProvidedResults() {
            List<ItemStack> craftables = tile.listCraftableResults();
            List<ItemKey> out = new ArrayList<>();
            for (ItemStack stack : craftables) if (stack != null && !stack.isEmpty()) out.add(ItemKey.of(stack));
            return out;
        }

        @Override
        public int getOutputCountFor(ItemKey key) {
            return tile.getPerCraftOutputCountFor(key.toStack(1));
        }

        @Override
        public List<RequiredInput> getRequiredInputsFor(ItemKey key, int times) {
            int slot = tile.findPatternSlotFor(key.toStack(1));
            if (slot < 0) return Collections.emptyList();
            List<PatternResourceList.Entry> entries = tile.getResourcesForSlot(slot);
            List<RequiredInput> out = new ArrayList<>();
            for (PatternResourceList.Entry e : entries) {
                out.add(new RequiredInput(e.getKey(), Math.max(1, e.getCount()) * Math.max(1, times)));
            }
            return out;
        }

        @Override
        public boolean enqueueExecution(ItemKey key, int times) {
            return tile.enqueueFromPatternRequester(key.toStack(1), Math.max(1, times)) > 0;
        }

        @Override
        public ProviderType getType() {
            return ProviderType.INFUSION_REQUESTER;
        }

        @Override
        public String getDebugName() {
            return "InfusionRequester@" + tile.getPos();
        }
    }

    private static final class PatternRequesterSource implements INetworkOperationSource {
        private final TilePatternRequester tile;

        private PatternRequesterSource(TilePatternRequester tile) { this.tile = tile; }

        @Override
        public List<ItemKey> getProvidedResults() {
            List<ItemKey> out = new ArrayList<>();
            for (ItemStack stack : tile.listCraftableResults()) {
                if (stack != null && !stack.isEmpty()) out.add(ItemKey.of(stack));
            }
            return out;
        }

        @Override
        public int getOutputCountFor(ItemKey key) {
            return tile.getPerCraftOutputCountFor(key.toStack(1));
        }

        @Override
        public List<RequiredInput> getRequiredInputsFor(ItemKey key, int times) {
            List<ItemStack> need = tile.getRecipeInputsFor(key.toStack(1), Math.max(1, times));
            List<RequiredInput> out = new ArrayList<>();
            for (ItemStack s : need) {
                if (s == null || s.isEmpty()) continue;
                out.add(new RequiredInput(ItemKey.of(s), Math.max(1, s.getCount())));
            }
            return out;
        }

        @Override
        public boolean enqueueExecution(ItemKey key, int times) {
            tile.enqueueCraft(key.toStack(1), Math.max(1, times));
            return true;
        }

        @Override
        public ProviderType getType() {
            TileEntity below = tile.getWorld() == null ? null : tile.getWorld().getTileEntity(tile.getPos().down());
            return below instanceof TileEntityArcaneCrafter ? ProviderType.ARCANE_CRAFTER : ProviderType.GOLEM_CRAFTER;
        }

        @Override
        public String getDebugName() {
            return getType().name() + "@" + tile.getPos();
        }
    }
}