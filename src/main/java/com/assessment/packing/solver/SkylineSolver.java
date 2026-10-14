package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
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
        // 完整布局只在收尾拿最优排列时需要；搜索过程一律走轻量评分路径。
        long start = System.nanoTime();
        List<Placement> placements = new ArrayList<>();
        run(instance, priorityItems, placements);
        return new Solution(name(), placements, System.nanoTime() - start);
    }

    /**
     * 轻量评分路径：只算装入面积，完全不构造 {@link Placement} 列表。
     *
     * <p>搜索过程中绝大多数候选排列只需要一个分数，构造和丢弃放置列表纯属浪费。
     * 两个方法共用 {@link #run} 的同一套放置逻辑，因此面积与 {@code solveInPriorityOrder}
     * 必然一致——这是搜索轨迹不被优化改变的前提。
     */
    @Override
    public long packedAreaInPriorityOrder(Instance instance, List<Item> priorityItems) {
        return run(instance, priorityItems, null);
    }

    /** 跑完整放置逻辑；{@code placements} 为 {@code null} 时只累计面积，不构造放置对象。 */
    private long run(Instance instance, List<Item> priorityItems, List<Placement> placements) {
        nextSerial = 0;
        long area = 0;

        // 用按编号索引的布尔数组代替 HashSet<Integer>：解码是搜索里最热的循环，
        // 装箱与哈希在剖析中占据显著比例，而零件编号在实例内唯一，语义完全等价。
        boolean[] used = new boolean[maxId(priorityItems) + 1];
        int usedCount = 0;

        TreeMap<Integer, Segment> byX = new TreeMap<>();
        PriorityQueue<Segment> heap = new PriorityQueue<>(LOWEST_LEFTMOST);
        addSegment(new Segment(0, 0, instance.binWidth(), nextSerial++), byX, heap);

        while (!heap.isEmpty() && usedCount < priorityItems.size()) {
            Segment current = pollActive(heap);
            if (current == null) break;

            Segment left = leftNeighbor(current, byX);
            Segment right = rightNeighbor(current, byX);
            int leftWall = left == null ? instance.binHeight() - current.y : left.y - current.y;
            int rightWall = right == null ? instance.binHeight() - current.y : right.y - current.y;

            Choice choice = chooseItem(
                    priorityItems, used, current, leftWall, rightWall, instance.binHeight());

            if (choice == null) {
                raiseUnusableSegment(current, left, right, byX, heap);
                continue;
            }

            boolean placeRight = shouldPlaceRight(choice.score, leftWall, rightWall);
            int placedX = placeRight ? current.x + current.length - choice.width : current.x;
            area += (long) choice.width * choice.height;
            if (placements != null) {
                placements.add(new Placement(choice.item, placedX, current.y, choice.rotated));
            }
            used[choice.item.id()] = true;
            usedCount++;

            replaceAfterPlacement(current, choice, placeRight, byX, heap);
            normalizeAround(placedX, byX, heap);
        }
        return area;
    }

    private static int maxId(List<Item> items) {
        int maxId = 0;
        for (Item item : items) maxId = Math.max(maxId, item.id());
        return maxId;
    }

    /**
     * 在未使用零件中挑选评分最高的候选。
     *
     * <p>只保留「当前最优」的标量字段，不每个改进都新建 {@link Choice} 对象——
     * 这个方法在一次解码里被调用上千次，是剖析中最热的一段。
     */
    private Choice chooseItem(
            List<Item> items,
            boolean[] used,
            Segment skyline,
            int leftWall,
            int rightWall,
            int binHeight) {

        Item bestItem = null;
        int bestWidth = 0;
        int bestHeight = 0;
        boolean bestRotated = false;
        // 合法评分为 0~7，-1 专表「放不下」；用 -1 作哨兵即可把不可放候选排除在外，
        // 严格大于保证并列时仍是排列中靠前的零件优先。
        int bestScore = -1;

        int length = skyline.length;
        int skylineY = skyline.y;
        for (Item item : items) {
            if (used[item.id()]) continue;

            int width = item.width();
            int height = item.height();
            int normalScore = score(width, height, length, skylineY, leftWall, rightWall, binHeight);
            if (normalScore > bestScore) {
                bestItem = item;
                bestWidth = width;
                bestHeight = height;
                bestRotated = false;
                bestScore = normalScore;
            }

            if (rotationAllowed && width != height) {
                int rotatedScore = score(
                        height, width, length, skylineY, leftWall, rightWall, binHeight);
                if (rotatedScore > bestScore) {
                    bestItem = item;
                    bestWidth = height;
                    bestHeight = width;
                    bestRotated = true;
                    bestScore = rotatedScore;
                }
            }
        }
        return bestItem == null
                ? null
                : new Choice(bestItem, bestWidth, bestHeight, bestRotated, bestScore);
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
