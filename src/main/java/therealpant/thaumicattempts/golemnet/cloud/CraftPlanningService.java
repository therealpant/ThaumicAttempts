package therealpant.thaumicattempts.golemnet.cloud;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import therealpant.thaumicattempts.api.ICloudCraftConsumer;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.util.ItemKey;
import therealpant.thaumicattempts.util.ResourceIdentity;

import javax.annotation.Nullable;
import java.util.*;

public class CraftPlanningService {
    private final int maxDepth;

    public CraftPlanningService(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public PlanningResult plan(TileMirrorManager manager, ItemKey requestedKey, int amount, Map<ItemKey, Integer> availableStock) {
        if (manager == null || manager.getWorld() == null || requestedKey == null || requestedKey == ItemKey.EMPTY || amount <= 0) {
            return PlanningResult.failed("invalid-request");
        }

        RecipeBook book = buildRecipeBook(manager);
        if (!book.hasRecipeFor(requestedKey)) {
            return PlanningResult.failed("missing-input:" + requestedKey);
        }

        Map<ItemKey, Integer> stock = new HashMap<>();
        if (availableStock != null) {
            for (Map.Entry<ItemKey, Integer> en : availableStock.entrySet()) {
                if (en == null || en.getKey() == null || en.getKey() == ItemKey.EMPTY) continue;
                stock.put(en.getKey(), Math.max(0, en.getValue()));
            }
        }

        PlanNode root = buildNode(book, requestedKey, Math.max(1, amount), stock, 0, new HashSet<ItemKey>());
        if (root == null) {
            return PlanningResult.failed("missing-input:" + requestedKey);
        }
        return PlanningResult.success(root, stock, buildDebugDump(root));
    }

    @Nullable
    private PlanNode buildNode(RecipeBook book,
                               ItemKey key,
                               int amount,
                               Map<ItemKey, Integer> stock,
                               int depth,
                               Set<ItemKey> visiting) {
        if (depth > maxDepth || key == null || key == ItemKey.EMPTY || amount <= 0) return null;
        RecipeRef recipe = book.findRecipe(key);
        if (recipe == null) return null;

        int available = Math.max(0, stock.getOrDefault(key, 0));
        int missing = Math.max(0, amount - available);
        int cycles = 0;
        if (missing > 0) {
            cycles = Math.max(1, (missing + recipe.outputPerCycle - 1) / recipe.outputPerCycle);
            int produced = cycles * recipe.outputPerCycle;
            int leftover = Math.max(0, produced - missing);
            stock.put(key, leftover);
        } else {
            stock.put(key, available - amount);
        }

        PlanNode node = new PlanNode(key, amount, cycles, recipe);

        if (cycles <= 0) return node;
        if (!visiting.add(key)) return null;

        for (Map.Entry<ItemKey, Integer> input : recipe.inputsPerCycle.entrySet()) {
            if (input == null || input.getKey() == null || input.getKey() == ItemKey.EMPTY) continue;
            int requiredAmount = Math.max(1, input.getValue()) * cycles;
            node.requiredInputs.put(input.getKey(), requiredAmount);

            int have = Math.max(0, stock.getOrDefault(input.getKey(), 0));
            if (have >= requiredAmount) {
                stock.put(input.getKey(), have - requiredAmount);
                continue;
            }

            int needToCraft = requiredAmount - have;
            stock.put(input.getKey(), 0);
            RecipeRef dep = book.findRecipe(input.getKey());
            if (dep == null) {
                visiting.remove(key);
                return null;
            }

            PlanNode child = buildNode(book, input.getKey(), needToCraft, stock, depth + 1, visiting);
            if (child == null) {
                visiting.remove(key);
                return null;
            }
            node.children.add(child);
        }

        visiting.remove(key);
        return node;
    }

    private RecipeBook buildRecipeBook(TileMirrorManager manager) {
        RecipeBook book = new RecipeBook();
        for (BlockPos pos : manager.getRequestersSnapshot()) {
            TileEntity te = manager.getWorld().getTileEntity(pos);
            if (!(te instanceof ICloudCraftConsumer)) continue;
            ICloudCraftConsumer consumer = (ICloudCraftConsumer) te;
            List<ItemStack> craftables = consumer.listCraftableResults();
            if (craftables == null) continue;
            for (ItemStack out : craftables) {
                if (out == null || out.isEmpty()) continue;
                ItemKey outKey = ItemKey.of(out);
                int perCycle = Math.max(1, consumer.getPerCraftOutputCountFor(out));
                Map<ItemKey, Integer> inputs = consumer.getInputsPerCycle(out);
                if (inputs == null) inputs = Collections.emptyMap();
                book.addRecipe(new RecipeRef(outKey, perCycle, inputs, consumer));
            }
        }
        return book;
    }

    private String buildDebugDump(PlanNode root) {
        StringBuilder sb = new StringBuilder();
        sb.append("root item=").append(root.itemKey)
                .append(", required cycles=").append(root.cycles)
                .append('\n');
        appendNode(sb, root, 0);
        return sb.toString();
    }

    private void appendNode(StringBuilder sb, PlanNode node, int depth) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append("- ").append(node.itemKey)
                .append(" cycles=").append(node.cycles)
                .append(" consumer=").append(node.recipe.consumer == null ? "null" : node.recipe.consumer.getOutputEndpoint().getPos())
                .append(" inputs=").append(node.requiredInputs)
                .append('\n');
        for (PlanNode child : node.children) {
            appendNode(sb, child, depth + 1);
        }
    }

    public static final class PlanningResult {
        public final boolean success;
        @Nullable
        public final PlanNode root;
        public final Map<ItemKey, Integer> remainingStock;
        public final String failReason;
        public final String debugDump;

        private PlanningResult(boolean success, @Nullable PlanNode root, Map<ItemKey, Integer> remainingStock, String failReason, String debugDump) {
            this.success = success;
            this.root = root;
            this.remainingStock = remainingStock == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(remainingStock));
            this.failReason = failReason == null ? "" : failReason;
            this.debugDump = debugDump == null ? "" : debugDump;
        }

        static PlanningResult success(PlanNode root, Map<ItemKey, Integer> remainingStock, String dump) {
            return new PlanningResult(true, root, remainingStock, "", dump);
        }

        static PlanningResult failed(String reason) {
            return new PlanningResult(false, null, Collections.emptyMap(), reason, "");
        }
    }

    public static final class PlanNode {
        public final ItemKey itemKey;
        public final int requestedAmount;
        public final int cycles;
        public final RecipeRef recipe;
        public final Map<ItemKey, Integer> requiredInputs = new LinkedHashMap<>();
        public final List<PlanNode> children = new ArrayList<>();

        private PlanNode(ItemKey itemKey, int requestedAmount, int cycles, RecipeRef recipe) {
            this.itemKey = itemKey;
            this.requestedAmount = requestedAmount;
            this.cycles = cycles;
            this.recipe = recipe;
        }
    }

    public static final class RecipeRef {
        public final ItemKey output;
        public final int outputPerCycle;
        public final Map<ItemKey, Integer> inputsPerCycle;
        public final ICloudCraftConsumer consumer;

        private RecipeRef(ItemKey output, int outputPerCycle, Map<ItemKey, Integer> inputsPerCycle, ICloudCraftConsumer consumer) {
            this.output = output;
            this.outputPerCycle = Math.max(1, outputPerCycle);
            this.inputsPerCycle = new LinkedHashMap<>();
            if (inputsPerCycle != null) {
                for (Map.Entry<ItemKey, Integer> en : inputsPerCycle.entrySet()) {
                    if (en == null || en.getKey() == null || en.getKey() == ItemKey.EMPTY) continue;
                    this.inputsPerCycle.put(en.getKey(), Math.max(1, en.getValue()));
                }
            }
            this.consumer = consumer;
        }
    }

    private static final class RecipeBook {
        private final Map<ItemKey, List<RecipeRef>> byOutput = new HashMap<>();

        void addRecipe(RecipeRef ref) {
            if (ref == null || ref.output == null || ref.output == ItemKey.EMPTY) return;
            byOutput.computeIfAbsent(ref.output, x -> new ArrayList<>()).add(ref);
        }

        boolean hasRecipeFor(ItemKey key) {
            return findRecipe(key) != null;
        }

        @Nullable
        RecipeRef findRecipe(ItemKey key) {
            List<RecipeRef> direct = byOutput.get(key);
            if (direct != null && !direct.isEmpty()) return direct.get(0);

            ItemStack like = key == null ? ItemStack.EMPTY : key.toStack(1);
            if (like.isEmpty()) return null;
            for (Map.Entry<ItemKey, List<RecipeRef>> en : byOutput.entrySet()) {
                ItemStack candidate = en.getKey().toStack(1);
                if (!candidate.isEmpty() && ResourceIdentity.sameResource(candidate, like) && !en.getValue().isEmpty()) {
                    return en.getValue().get(0);
                }
            }
            return null;
        }
    }
}