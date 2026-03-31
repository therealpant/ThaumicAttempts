package therealpant.thaumicattempts.golemnet.planner;

import therealpant.thaumicattempts.util.ItemKey;

import java.util.ArrayList;
import java.util.List;

public final class PlannedOperationNode {
    public final ItemKey requested;
    public final int amount;
    public final INetworkOperationSource source;
    public final int operations;
    public final List<PlannedOperationNode> dependencies = new ArrayList<>();

    public PlannedOperationNode(ItemKey requested, int amount, INetworkOperationSource source, int operations) {
        this.requested = requested;
        this.amount = Math.max(1, amount);
        this.source = source;
        this.operations = Math.max(1, operations);
    }

    public void addDependency(PlannedOperationNode node) {
        if (node != null) dependencies.add(node);
    }
}