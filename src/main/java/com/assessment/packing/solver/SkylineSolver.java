package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

/**
 * 堆优化天际线启发式。
 *
 * <p>每次处理最低、最左天际线；遍历全部未使用零件和允许方向，按博客的0~7分规则
 * 选择最高分候选。输入顺序只在评分相同时决定优先级。</p>
 */
public final class SkylineSolver implements PackingAlgorithm, PackingDecoder {
    private static final Comparator<Segment> LOWEST_LEFTMOST =
            Comparator.comparingInt((Segment s) -> s.y)
                    .thenComparingInt(s -> s.x)
                    .thenComparingLong(s -> s.serial);

    private final boolean rotationAllowed;
    private final ItemOrder order;
    private long nextSerial;

    public SkylineSolver(boolean rotationAllowed, ItemOrder order) {
        this.rotationAllowed = rotationAllowed;
        this.order = order;
    }

    @Override
    public String name() {
        return "Heap Skyline(" + order + ", rotation=" + rotationAllowed + ")";
    }

    @Override
    public Solution solve(Instance instance) {
        return solveInPriorityOrder(instance, ItemOrdering.sort(instance.items(), order));
    }

    @Override
    public Solution solveInPriorityOrder(Instance instance, List<Item> priorityItems) {
        long start = System.nanoTime();
        nextSerial = 0;

        Set<Integer> usedIds = new HashSet<>();
        List<Placement> placements = new ArrayList<>();

        TreeMap<Integer, Segment> byX = new TreeMap<>();
        PriorityQueue<Segment> heap = new PriorityQueue<>(LOWEST_LEFTMOST);
        addSegment(new Segment(0, 0, instance.binWidth(), nextSerial++), byX, heap);

        while (!heap.isEmpty() && usedIds.size() < priorityItems.size()) {
            Segment current = pollActive(heap);
            if (current == null) break;

            Segment left = leftNeighbor(current, byX);
            Segment right = rightNeighbor(current, byX);
            int leftWall = left == null ? instance.binHeight() - current.y : left.y - current.y;
            int rightWall = right == null ? instance.binHeight() - current.y : right.y - current.y;

            Choice choice = chooseItem(
                    priorityItems, usedIds, current, leftWall, rightWall, instance.binHeight());

            if (choice == null) {
                raiseUnusableSegment(current, left, right, byX, heap);
                continue;
            }

            boolean placeRight = shouldPlaceRight(choice.score, leftWall, rightWall);
            int placedX = placeRight ? current.x + current.length - choice.width : current.x;
            placements.add(new Placement(choice.item, placedX, current.y, choice.rotated));
            usedIds.add(choice.item.id());

            replaceAfterPlacement(current, choice, placeRight, byX, heap);
            normalizeAround(placedX, byX, heap);
        }

        return new Solution(name(), placements, System.nanoTime() - start);
    }

    private Choice chooseItem(
            List<Item> items,
            Set<Integer> usedIds,
            Segment skyline,
            int leftWall,
            int rightWall,
            int binHeight) {

        Choice best = null;
        for (Item item : items) {
            if (usedIds.contains(item.id())) continue;

            int normalScore = score(item.width(), item.height(), skyline, leftWall, rightWall, binHeight);
            if (normalScore >= 0 && (best == null || normalScore > best.score)) {
                best = new Choice(item, item.width(), item.height(), false, normalScore);
            }

            if (rotationAllowed && item.width() != item.height()) {
                int rotatedScore = score(item.height(), item.width(), skyline, leftWall, rightWall, binHeight);
                if (rotatedScore >= 0 && (best == null || rotatedScore > best.score)) {
                    best = new Choice(item, item.height(), item.width(), true, rotatedScore);
                }
            }
        }
        return best;
    }

    static int score(
            int width,
            int height,
            int skylineLength,
            int skylineY,
            int leftWall,
            int rightWall,
            int binHeight) {

        if (width > skylineLength || skylineY + height > binHeight) return -1;

        if (leftWall >= rightWall) {
            if (width == skylineLength && height == leftWall) return 7;
            if (width == skylineLength && height == rightWall) return 6;
            if (width == skylineLength && height > leftWall) return 5;
            if (width < skylineLength && height == leftWall) return 4;
            if (width == skylineLength && height < leftWall && height > rightWall) return 3;
            if (width < skylineLength && height == rightWall) return 2;
            if (width == skylineLength && height < rightWall) return 1;
            if (width < skylineLength && height != leftWall) return 0;
        } else {
            if (width == skylineLength && height == rightWall) return 7;
            if (width == skylineLength && height == leftWall) return 6;
            if (width == skylineLength && height > rightWall) return 5;
            if (width < skylineLength && height == rightWall) return 4;
            if (width == skylineLength && height < rightWall && height > leftWall) return 3;
            if (width < skylineLength && height == leftWall) return 2;
            if (width == skylineLength && height < leftWall) return 1;
            if (width < skylineLength && height != rightWall) return 0;
        }

        throw new IllegalStateException("未覆盖的评分情况：width=" + width + ", height=" + height
                + ", skylineLength=" + skylineLength + ", leftWall=" + leftWall
                + ", rightWall=" + rightWall);
    }

    private static int score(
            int width,
            int height,
            Segment skyline,
            int leftWall,
            int rightWall,
            int binHeight) {
        return score(width, height, skyline.length, skyline.y, leftWall, rightWall, binHeight);
    }

    static boolean shouldPlaceRight(int score, int leftWall, int rightWall) {
        return leftWall >= rightWall ? score == 2 : score == 4 || score == 0;
    }

    private void replaceAfterPlacement(
            Segment current,
            Choice choice,
            boolean placeRight,
            TreeMap<Integer, Segment> byX,
            PriorityQueue<Segment> heap) {

        deactivateAndRemove(current, byX);
        int remaining = current.length - choice.width;

        if (placeRight) {
            if (remaining > 0) {
                addSegment(new Segment(current.x, current.y, remaining, nextSerial++), byX, heap);
            }
            addSegment(new Segment(
                    current.x + remaining,
                    current.y + choice.height,
                    choice.width,
                    nextSerial++), byX, heap);
        } else {
            addSegment(new Segment(
                    current.x,
                    current.y + choice.height,
                    choice.width,
                    nextSerial++), byX, heap);
            if (remaining > 0) {
                addSegment(new Segment(
                        current.x + choice.width,
                        current.y,
                        remaining,
                        nextSerial++), byX, heap);
            }
        }
    }

    private void raiseUnusableSegment(
            Segment current,
            Segment left,
            Segment right,
            TreeMap<Integer, Segment> byX,
            PriorityQueue<Segment> heap) {

        deactivateAndRemove(current, byX);
        if (left == null && right == null) return;

        int targetY;
        if (left == null) targetY = right.y;
        else if (right == null) targetY = left.y;
        else targetY = Math.min(left.y, right.y);

        Segment raised = new Segment(current.x, targetY, current.length, nextSerial++);
        addSegment(raised, byX, heap);
        normalizeAround(raised.x, byX, heap);
    }

    private void normalizeAround(
            int x,
            TreeMap<Integer, Segment> byX,
            PriorityQueue<Segment> heap) {

        Segment current = segmentAtOrBefore(x, byX);
        if (current == null) return;

        boolean merged;
        do {
            merged = false;
            Segment left = leftNeighbor(current, byX);
            if (left != null && left.y == current.y && left.x + left.length == current.x) {
                current = merge(left, current, byX, heap);
                merged = true;
            }

            Segment right = rightNeighbor(current, byX);
            if (right != null && right.y == current.y && current.x + current.length == right.x) {
                current = merge(current, right, byX, heap);
                merged = true;
            }
        } while (merged);
    }

    private Segment merge(
            Segment left,
            Segment right,
            TreeMap<Integer, Segment> byX,
            PriorityQueue<Segment> heap) {

        deactivateAndRemove(left, byX);
        deactivateAndRemove(right, byX);
        Segment merged = new Segment(
                left.x, left.y, left.length + right.length, nextSerial++);
        addSegment(merged, byX, heap);
        return merged;
    }

    private static void addSegment(
            Segment segment,
            TreeMap<Integer, Segment> byX,
            PriorityQueue<Segment> heap) {
        if (segment.length <= 0) return;
        Segment previous = byX.put(segment.x, segment);
        if (previous != null && previous.active) {
            throw new IllegalStateException("天际线横坐标冲突：x=" + segment.x);
        }
        heap.add(segment);
    }

    private static void deactivateAndRemove(Segment segment, TreeMap<Integer, Segment> byX) {
        segment.active = false;
        byX.remove(segment.x, segment);
    }

    private static Segment pollActive(PriorityQueue<Segment> heap) {
        while (!heap.isEmpty()) {
            Segment segment = heap.poll();
            if (segment.active) return segment;
        }
        return null;
    }

    private static Segment leftNeighbor(Segment current, TreeMap<Integer, Segment> byX) {
        var entry = byX.lowerEntry(current.x);
        return entry == null ? null : entry.getValue();
    }

    private static Segment rightNeighbor(Segment current, TreeMap<Integer, Segment> byX) {
        var entry = byX.higherEntry(current.x);
        return entry == null ? null : entry.getValue();
    }

    private static Segment segmentAtOrBefore(int x, TreeMap<Integer, Segment> byX) {
        var entry = byX.floorEntry(x);
        return entry == null ? null : entry.getValue();
    }

    private static final class Segment {
        private final int x;
        private final int y;
        private final int length;
        private final long serial;
        private boolean active = true;

        private Segment(int x, int y, int length, long serial) {
            this.x = x;
            this.y = y;
            this.length = length;
            this.serial = serial;
        }
    }

    private record Choice(Item item, int width, int height, boolean rotated, int score) {}
}
