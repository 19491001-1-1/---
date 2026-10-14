package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import com.assessment.packing.validation.SolutionValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 连续移动式 Bottom-Left。
 *
 * <p>对每个顶部入口，将矩形交替向下、向左移动到最近阻挡位置；移动距离直接跳到边界，
 * 不按单位步长逐格移动。整个过程的 x/y 单调不增，因此一定终止。</p>
 */
public final class ContinuousBottomLeftSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;

    public ContinuousBottomLeftSolver(boolean rotationAllowed, ItemOrder order) {
        this.rotationAllowed = rotationAllowed;
        this.order = order;
    }

    @Override
    public String name() {
        return "Continuous Bottom-Left(" + order + ", rotation=" + rotationAllowed + ")";
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Placement> placed = new ArrayList<>();

        for (Item item : ItemOrdering.sort(instance.items(), order)) {
            RestingPosition best = findBestRestingPosition(item, placed, instance);
            if (best != null) {
                placed.add(new Placement(item, best.x, best.y, best.rotated));
            }
        }

        return new Solution(name(), placed, System.nanoTime() - start);
    }

    private RestingPosition findBestRestingPosition(
            Item item, List<Placement> placed, Instance instance) {

        List<RestingPosition> positions = new ArrayList<>();
        addOrientationPositions(positions, item, false, placed, instance);
        if (rotationAllowed && item.width() != item.height()) {
            addOrientationPositions(positions, item, true, placed, instance);
        }

        return positions.stream()
                .distinct()
                .min(Comparator.comparingInt((RestingPosition p) -> p.y)
                        .thenComparingInt(p -> p.x)
                        .thenComparingInt(p -> p.y + p.height)
                        .thenComparingInt(p -> p.x + p.width)
                        .thenComparing(p -> p.rotated))
                .orElse(null);
    }

    private void addOrientationPositions(
            List<RestingPosition> positions,
            Item item,
            boolean rotated,
            List<Placement> placed,
            Instance instance) {

        int width = rotated ? item.height() : item.width();
        int height = rotated ? item.width() : item.height();
        if (width > instance.binWidth() || height > instance.binHeight()) return;

        int startY = instance.binHeight() - height;

        // 枚举顶部的所有整数横向入口。每个入口都可能落入不同的阻挡结构。
        for (int startX = 0; startX + width <= instance.binWidth(); startX++) {
            Placement seed = new Placement(item, startX, startY, rotated);
            if (overlapsAny(seed, placed)) continue;

            Placement resting = settle(seed, placed);
            positions.add(new RestingPosition(
                    resting.x(), resting.y(), resting.width(), resting.height(), rotated));
        }
    }

    private Placement settle(Placement seed, List<Placement> placed) {
        Placement current = seed;

        while (true) {
            int downY = lowestReachableY(current, placed);
            Placement afterDown = new Placement(current.item(), current.x(), downY, current.rotated());

            int leftX = leftmostReachableX(afterDown, placed);
            Placement afterLeft = new Placement(
                    current.item(), leftX, afterDown.y(), current.rotated());

            if (afterLeft.x() == current.x() && afterLeft.y() == current.y()) {
                return current;
            }
            current = afterLeft;
        }
    }

    /** 在不穿过任何矩形的条件下，直接下降到最近的支撑面。 */
    private static int lowestReachableY(Placement moving, List<Placement> placed) {
        int targetY = 0;
        for (Placement obstacle : placed) {
            if (horizontalProjectionsOverlap(moving, obstacle)) {
                int obstacleTop = obstacle.y() + obstacle.height();
                if (obstacleTop <= moving.y()) targetY = Math.max(targetY, obstacleTop);
            }
        }
        return targetY;
    }

    /** 在不穿过任何矩形的条件下，直接左移到最近的阻挡面。 */
    private static int leftmostReachableX(Placement moving, List<Placement> placed) {
        int targetX = 0;
        for (Placement obstacle : placed) {
            if (verticalProjectionsOverlap(moving, obstacle)) {
                int obstacleRight = obstacle.x() + obstacle.width();
                if (obstacleRight <= moving.x()) targetX = Math.max(targetX, obstacleRight);
            }
        }
        return targetX;
    }

    private static boolean horizontalProjectionsOverlap(Placement a, Placement b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width();
    }

    private static boolean verticalProjectionsOverlap(Placement a, Placement b) {
        return a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }

    private static boolean overlapsAny(Placement candidate, List<Placement> placed) {
        for (Placement other : placed) {
            if (SolutionValidator.overlaps(candidate, other)) return true;
        }
        return false;
    }

    private record RestingPosition(int x, int y, int width, int height, boolean rotated) {}
}
