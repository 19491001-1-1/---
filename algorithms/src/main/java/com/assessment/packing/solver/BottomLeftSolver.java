package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import com.assessment.packing.validation.SolutionValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 候选点 Bottom-Left。
 * 候选横坐标来自箱子左边界和已放置矩形右边界；候选纵坐标来自底边和已放置矩形上边界。
 */
public final class BottomLeftSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;

    public BottomLeftSolver(boolean rotationAllowed, ItemOrder order) {
        this.rotationAllowed = rotationAllowed;
        this.order = order;
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Placement> placements = new ArrayList<>();
        run(instance, placements);
        return new Solution(name(), placements, System.nanoTime() - start);
    }

    @Override
    public long packedAreaOf(Instance instance) {
        return run(instance, new ArrayList<>());
    }

    /** 放置列表本算法内部要做重叠检测，因此轻量路径仍需构造它，只省掉最后的 Solution。 */
    private long run(Instance instance, List<Placement> placements) {
        long area = 0;
        for (Item item : ItemOrdering.sort(instance.items(), order)) {
            Candidate best = findBest(item, placements, instance);
            if (best != null) {
                placements.add(new Placement(item, best.x, best.y, best.rotated));
                area += item.area();
            }
        }
        return area;
    }

    @Override
    public String name() {
        return "Bottom-Left(" + order + ", rotation=" + rotationAllowed + ")";
    }

    private Candidate findBest(Item item, List<Placement> placed, Instance instance) {
        Set<Integer> xs = new LinkedHashSet<>();
        Set<Integer> ys = new LinkedHashSet<>();
        xs.add(0);
        ys.add(0);
        for (Placement p : placed) {
            xs.add(p.x() + p.width());
            ys.add(p.y() + p.height());
        }

        List<Candidate> feasible = new ArrayList<>();
        for (int y : ys) {
            for (int x : xs) {
                addIfFeasible(feasible, item, x, y, false, placed, instance);
                if (rotationAllowed && item.width() != item.height()) {
                    addIfFeasible(feasible, item, x, y, true, placed, instance);
                }
            }
        }

        return feasible.stream()
                .min(Comparator.comparingInt((Candidate c) -> c.y)
                        .thenComparingInt(c -> c.x)
                        .thenComparingInt(c -> c.y + c.height)
                        .thenComparingInt(c -> c.x + c.width)
                        .thenComparing(c -> c.rotated))
                .orElse(null);
    }

    private static void addIfFeasible(
            List<Candidate> feasible,
            Item item,
            int x,
            int y,
            boolean rotated,
            List<Placement> placed,
            Instance instance) {

        int width = rotated ? item.height() : item.width();
        int height = rotated ? item.width() : item.height();
        if (x + width > instance.binWidth() || y + height > instance.binHeight()) return;

        Placement candidatePlacement = new Placement(item, x, y, rotated);
        for (Placement other : placed) {
            if (SolutionValidator.overlaps(candidatePlacement, other)) return;
        }
        feasible.add(new Candidate(x, y, width, height, rotated));
    }

    private record Candidate(int x, int y, int width, int height, boolean rotated) {}
}
