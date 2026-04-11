package therealpant.thaumicattempts.golemnet.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import therealpant.thaumicattempts.api.ICraftEndpoint;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class TileCraftPlanner extends TileEntity implements ITickable, IAnimatable {
    private static final String TAG_MANAGER = "manager";
    private static final String TAG_ANIM_SEED = "animSeed";
    private static final int RESCAN_PERIOD_TICKS = 200;
    @Nullable
    private BlockPos managerPos;
    private int rescanCooldown = 0;
    private final Map<ItemKey, BlockPos> recipeIndex = new HashMap<>();
    private final Map<ItemKey, Integer> chainDepthCache = new HashMap<>();

    // Визуальная анимация (копия параметров dispatcher)
    public float animSpeed = 0.625f;
    public long animSeed = 2L;
    public int[] picks = {0, 1, 2}; // индексы костей 0..7
    public int[] axes  = {0, 1, 2}; // 0=X,1=Y,2=Z
    public long cycleIdx = -1L;
    public static final int DUR_A = 20;
    public static final int GAP = 4;
    private final AnimationFactory factory = new AnimationFactory(this);

    public void reseedForCycle(long newIdx) {
        Random r = new Random(animSeed ^ (newIdx * 0x9E3779B97F4A7C15L));
        int[] a = {0,1,2,3,4,5,6,7};
        for (int i = a.length - 1; i > 0; --i) {
            int j = r.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        picks[0] = a[0];
        picks[1] = a[1];
        picks[2] = a[2];

        axes[0] = r.nextInt(3);
        axes[1] = r.nextInt(3);
        axes[2] = r.nextInt(3);

        cycleIdx = newIdx;
    }

    public static long calcCycleLenForSpeed(float animSpeed) {
        float sp = Math.max(0.05f, animSpeed);
        int base = DUR_A + GAP;
        long aLen = Math.max(1, Math.round(base / sp));
        return aLen * 4L;
    }

    public long cycleLen() {
        return calcCycleLenForSpeed(this.animSpeed);
    }

    @Override
    public void registerControllers(AnimationData data) {
        AnimationController<TileCraftPlanner> baseController =
                new AnimationController<>(this, "base_cycle", 0, this::baseCyclePredicate);
        data.addAnimationController(baseController);
    }

    private <E extends IAnimatable> PlayState baseCyclePredicate(AnimationEvent<E> event) {
        event.getController().setAnimation(
                new AnimationBuilder().addAnimation("animation.model.cycle_base", true)
        );
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
    @Nullable
    public BlockPos getManagerPos() {
        return managerPos;
    }

    public void setManagerPos(@Nullable BlockPos managerPos) {
        BlockPos next = managerPos == null ? null : managerPos.toImmutable();
        if (!Objects.equals(this.managerPos, next)) {
            this.managerPos = next;
            forceRescan();
            markDirty();
        }
    }

    public void forceRescan() {
        rescanCooldown = 0;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (managerPos == null) return;
        if (--rescanCooldown <= 0) {
            rebuildCraftIndex();
            rescanCooldown = RESCAN_PERIOD_TICKS;
        }
    }

    public boolean canCraftOrderViaPlanner(List<Map.Entry<ItemKey, Integer>> requests, TileMirrorManager manager) {
        if (world == null || manager == null || requests == null || requests.isEmpty()) return false;
        if (!manager.getPos().equals(managerPos)) return false;

        if (recipeIndex.isEmpty()) {
            rebuildCraftIndex();
        }

        LinkedHashMap<ItemKey, Integer> reachable = manager.getReachableCatalog();
        Map<ItemKey, Integer> stock = new HashMap<>();
        for (Map.Entry<ItemKey, Integer> en : reachable.entrySet()) {
            if (en.getKey() == null || en.getKey() == ItemKey.EMPTY) continue;
            stock.put(en.getKey(), Math.max(0, en.getValue()));
        }

        for (Map.Entry<ItemKey, Integer> req : requests) {
            if (req == null || req.getKey() == null || req.getKey() == ItemKey.EMPTY) continue;
            if (!tryBuildPlanFor(req.getKey().toStack(1), Math.max(1, req.getValue()), stock, new HashSet<ItemKey>())) {
                return false;
            }
        }
        return true;
    }

    private void rebuildCraftIndex() {
        recipeIndex.clear();
        chainDepthCache.clear();
        if (world == null || managerPos == null) return;
        TileEntity teMgr = world.getTileEntity(managerPos);
        if (!(teMgr instanceof TileMirrorManager)) return;

        Set<BlockPos> requesters = ((TileMirrorManager) teMgr).getRequestersSnapshot();
        for (BlockPos rp : requesters) {
            TileEntity te = world.getTileEntity(rp);
            if (!(te instanceof ICraftEndpoint)) continue;
            List<ItemStack> results = ((ICraftEndpoint) te).listCraftableResults();
            if (results == null || results.isEmpty()) continue;
            for (ItemStack out : results) {
                if (out == null || out.isEmpty()) continue;
                recipeIndex.put(ItemKey.of(out), rp.toImmutable());
            }
        }
    }

    private boolean tryBuildPlanFor(ItemStack targetLike, int amount, Map<ItemKey, Integer> stock, Set<ItemKey> visiting) {
        if (targetLike == null || targetLike.isEmpty() || amount <= 0) return true;

        ItemKey key = ItemKey.of(targetLike);
        int have = Math.max(0, stock.getOrDefault(key, 0));
        if (have >= amount) {
            stock.put(key, have - amount);
            return true;
        }

        int missing = amount - have;
        stock.put(key, 0);

        if (!visiting.add(key)) return false;

        BlockPos rp = findRequesterForKey(key);
        if (rp == null) {
            visiting.remove(key);
            return false;
        }

        TileEntity te = world.getTileEntity(rp);
        if (!(te instanceof ICraftEndpoint)) {
            visiting.remove(key);
            return false;
        }

        ICraftEndpoint ep = (ICraftEndpoint) te;
        int perCraft = Math.max(1, ep.getPerCraftOutputCountFor(targetLike));
        int crafts = (missing + perCraft - 1) / perCraft;

        List<ItemStack> needs = getRecipeInputsForEndpoint(te, targetLike, crafts);
        for (ItemStack need : needs) {
            if (need == null || need.isEmpty()) continue;
            if (!tryBuildPlanFor(need, Math.max(1, need.getCount()), stock, visiting)) {
                visiting.remove(key);
                return false;
            }
        }

        int produced = crafts * perCraft;
        int leftover = Math.max(0, produced - missing);
        if (leftover > 0) {
            stock.put(key, stock.getOrDefault(key, 0) + leftover);
        }

        int bestChildDepth = 0;
        for (ItemStack need : needs) {
            if (need == null || need.isEmpty()) continue;
            bestChildDepth = Math.max(bestChildDepth, chainDepthCache.getOrDefault(ItemKey.of(need), 0));
        }
        chainDepthCache.put(key, bestChildDepth + 1);
        visiting.remove(key);
        return true;
    }

    private List<ItemStack> getRecipeInputsForEndpoint(TileEntity te, ItemStack like, int crafts) {
        if (te instanceof TilePatternRequester) {
            return ((TilePatternRequester) te).getRecipeInputsFor(like, crafts);
        }
        if (te instanceof TileInfusionRequester) {
            TileInfusionRequester inf = (TileInfusionRequester) te;
            int slot = inf.findPatternSlotFor(like);
            if (slot < 0) return Collections.emptyList();
            List<therealpant.thaumicattempts.api.PatternResourceList.Entry> entries = inf.getResourcesForSlot(slot);
            if (entries == null || entries.isEmpty()) return Collections.emptyList();
            List<ItemStack> out = new ArrayList<>();
            for (therealpant.thaumicattempts.api.PatternResourceList.Entry e : entries) {
                if (e == null || e.getKey() == null || e.getKey() == ItemKey.EMPTY) continue;
                ItemStack st = e.getKey().toStack(Math.max(1, e.getCount() * Math.max(1, crafts)));
                if (!st.isEmpty()) out.add(st);
            }
            return out;
        }
        return Collections.emptyList();
    }

    @Nullable
    private BlockPos findRequesterForKey(ItemKey key) {
        if (key == null || key == ItemKey.EMPTY) return null;
        BlockPos cached = recipeIndex.get(key);
        if (cached != null) return cached;

        ItemStack like = key.toStack(1);
        if (like.isEmpty()) return null;
        for (Map.Entry<ItemKey, BlockPos> en : recipeIndex.entrySet()) {
            ItemStack candidate = en.getKey().toStack(1);
            if (!candidate.isEmpty() && ResourceIdentity.sameResource(candidate, like)) {
                return en.getValue();
            }
        }
        return null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (managerPos != null) compound.setLong(TAG_MANAGER, managerPos.toLong());
        compound.setLong(TAG_ANIM_SEED, animSeed);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        managerPos = compound.hasKey(TAG_MANAGER) ? BlockPos.fromLong(compound.getLong(TAG_MANAGER)) : null;
        if (compound.hasKey(TAG_ANIM_SEED)) animSeed = compound.getLong(TAG_ANIM_SEED);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote && animSeed == 0L) {
            animSeed = world.rand.nextLong() ^ getPos().toLong();
        }
    }
}