package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 可配置的Guillotine算法，支持全局候选选择。 */
public final class GuillotineSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;
    private final GuillotineRectChoice choice;
    private final GuillotineSplitRule splitRule;
    private final boolean mergeRectangles;
    private final boolean globalSelection;

    public GuillotineSolver(
            boolean rotationAllowed,
            ItemOrder order,
            GuillotineRectChoice choice,
            GuillotineSplitRule splitRule,
            boolean mergeRectangles) {
        this(rotationAllowed, order, choice, splitRule, mergeRectangles, true);
    }

    public GuillotineSolver(
            boolean rotationAllowed,
            ItemOrder order,
            GuillotineRectChoice choice,
            GuillotineSplitRule splitRule,
            boolean mergeRectangles,
            boolean globalSelection) {
        this.rotationAllowed = rotationAllowed;
        this.order = Objects.requireNonNull(order, "排序规则不能为空");
        this.choice = Objects.requireNonNull(choice, "自由矩形选择规则不能为空");
        this.splitRule = Objects.requireNonNull(splitRule, "分割规则不能为空");
        this.mergeRectangles = mergeRectangles;
        this.globalSelection = globalSelection;
    }

    @Override
    public String name() {
        return "Guillotine-" + choice + '-' + splitRule
                + (mergeRectangles ? "-RM" : "")
                + (globalSelection ? "" : "-SEQUENTIAL")
                + "(" + order + ", rotation=" + rotationAllowed + ")";
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Item> remaining = new ArrayList<>(ItemOrdering.sort(instance.items(), order));
        List<Placement> placements = new ArrayList<>();
        GuillotineFreeSpace freeSpace = new GuillotineFreeSpace(
                choice, splitRule, mergeRectangles);
        freeSpace.addFreeRectangle(new GuillotineFreeSpace.FreeRectangle(
                0, 0, instance.binWidth(), instance.binHeight()));

        while (!remaining.isEmpty()) {
            GuillotineFreeSpace.Selection selection = globalSelection
                    ? freeSpace.findBest(remaining, rotationAllowed)
                    : freeSpace.findFirstFitting(remaining, rotationAllowed);
            if (selection == null) break;
            Item item = remaining.remove(selection.itemIndex());
            GuillotineFreeSpace.PlacedRectangle placed = freeSpace.place(selection);
            placements.add(new Placement(item, placed.x(), placed.y(), placed.rotated()));
        }

        return new Solution(name(), placements, System.nanoTime() - start);
    }

}
