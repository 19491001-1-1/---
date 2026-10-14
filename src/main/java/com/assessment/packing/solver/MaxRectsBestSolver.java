package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.Objects;

/**
 * MaxRects 确定性组合求解器。
 *
 * <p>它分别运行全部五种 MaxRects 启发式，并返回装入面积最大的布局。它不是新的
 * MaxRects 评分公式，而是用约五倍计算量换取对单一启发式选择的稳健性。面积相同时按
 * {@link MaxRectsHeuristic} 的声明顺序稳定选择。</p>
 */
public final class MaxRectsBestSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;
    private final boolean globalSelection;
    private MaxRectsBestStatistics lastStatistics = MaxRectsBestStatistics.empty();

    public MaxRectsBestSolver(boolean rotationAllowed, ItemOrder order) {
        this(rotationAllowed, order, true);
    }

    public MaxRectsBestSolver(
            boolean rotationAllowed, ItemOrder order, boolean globalSelection) {
        this.rotationAllowed = rotationAllowed;
        this.order = Objects.requireNonNull(order, "排序规则不能为空");
        this.globalSelection = globalSelection;
    }

    @Override
    public String name() {
        return "MaxRects-BEST" + (globalSelection ? "" : "-SEQUENTIAL")
                + "(" + order + ", rotation=" + rotationAllowed + ")";
    }

    public MaxRectsBestStatistics lastStatistics() {
        return lastStatistics;
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        lastStatistics = MaxRectsBestStatistics.empty();
        Solution best = null;
        MaxRectsHeuristic selected = null;
        ArrayList<MaxRectsBestStatistics.Entry> entries = new ArrayList<>();

        for (MaxRectsHeuristic heuristic : MaxRectsHeuristic.values()) {
            Solution candidate = new MaxRectsSolver(
                    rotationAllowed, order, heuristic, globalSelection).solve(instance);
            entries.add(new MaxRectsBestStatistics.Entry(
                    heuristic,
                    candidate.packedArea(),
                    candidate.placements().size(),
                    candidate.elapsedNanos()));
            if (best == null || candidate.packedArea() > best.packedArea()) {
                best = candidate;
                selected = heuristic;
            }
        }

        lastStatistics = new MaxRectsBestStatistics(selected, entries);
        return new Solution(name(), best.placements(), System.nanoTime() - start);
    }
}
