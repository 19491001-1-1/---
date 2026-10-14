package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MaxRects 二维矩形排样。
 *
 * <p>{@code freeRectangles} 不是剩余区域的不相交分区，而是剩余空间的全部极大空闲矩形表示；
 * 因此这些矩形允许互相重叠。每次落位后必须切割所有与新零件相交的空闲矩形，再删除被
 * 其他空闲矩形完整包含的项。</p>
 */
public final class MaxRectsSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;
    private final MaxRectsHeuristic heuristic;
    private final boolean globalSelection;

    public MaxRectsSolver(
            boolean rotationAllowed,
            ItemOrder order,
            MaxRectsHeuristic heuristic) {
        this(rotationAllowed, order, heuristic, true);
    }

    public MaxRectsSolver(
            boolean rotationAllowed,
            ItemOrder order,
            MaxRectsHeuristic heuristic,
            boolean globalSelection) {
        this.rotationAllowed = rotationAllowed;
        this.order = Objects.requireNonNull(order, "排序规则不能为空");
        this.heuristic = Objects.requireNonNull(heuristic, "MaxRects启发式不能为空");
        this.globalSelection = globalSelection;
    }

    @Override
    public String name() {
        return "MaxRects-" + heuristic + (globalSelection ? "" : "-SEQUENTIAL")
                + "(" + order + ", rotation=" + rotationAllowed + ")";
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Placement> usedRectangles = new ArrayList<>();
        run(instance, usedRectangles);
        return new Solution(name(), usedRectangles, System.nanoTime() - start);
    }

    /**
     * 已放置矩形只有 {@link MaxRectsHeuristic#CP} 的接触评分需要读，其余四种启发式根本不碰它。
     * 因此轻量路径在非 CP 时传 {@code null}，连 {@link Placement} 都不构造。
     */
    @Override
    public long packedAreaOf(Instance instance) {
        return run(instance, heuristic == MaxRectsHeuristic.CP ? new ArrayList<>() : null);
    }

    /** {@code usedRectangles} 同时充当 CP 评分输入与最终布局输出，可为 {@code null}。 */
    private long run(Instance instance, List<Placement> usedRectangles) {
        long area = 0;
        List<FreeRectangle> freeRectangles = new ArrayList<>();
        List<Item> remainingItems = new ArrayList<>(
                ItemOrdering.sort(instance.items(), order));
        freeRectangles.add(new FreeRectangle(0, 0, instance.binWidth(), instance.binHeight()));

        while (!remainingItems.isEmpty()) {
            Selection selection = globalSelection
                    ? findBestSelection(
                            remainingItems, freeRectangles, usedRectangles,
                            instance.binWidth(), instance.binHeight())
                    : findFirstFittingSelection(
                            remainingItems, freeRectangles, usedRectangles,
                            instance.binWidth(), instance.binHeight());
            if (selection == null) break;

            Candidate best = selection.candidate();
            Item item = remainingItems.remove(selection.itemIndex());
            FreeRectangle occupied = new FreeRectangle(
                    best.x(), best.y(), best.width(), best.height());

            // 注意：不是只切割承载候选的那个空闲矩形，而是切割全部相交项。
            freeRectangles = splitFreeRectangles(freeRectangles, occupied);
            area += item.area();
            if (usedRectangles != null) {
                usedRectangles.add(new Placement(item, best.x(), best.y(), best.rotated()));
            }
        }
        return area;
    }

    private Selection findFirstFittingSelection(
            List<Item> remainingItems,
            List<FreeRectangle> freeRectangles,
            List<Placement> usedRectangles,
            int binWidth,
            int binHeight) {
        for (int index = 0; index < remainingItems.size(); index++) {
            Candidate candidate = findBestCandidate(
                    remainingItems.get(index), freeRectangles, usedRectangles,
                    binWidth, binHeight);
            if (candidate != null) return new Selection(index, candidate);
        }
        return null;
    }

    /**
     * 批量 MaxRects 每轮比较所有“剩余零件 × 空闲矩形 × 允许朝向”。
     * 排序后的先后次序只在主评分完全相同时充当稳定的平局规则。
     */
    private Selection findBestSelection(
            List<Item> remainingItems,
            List<FreeRectangle> freeRectangles,
            List<Placement> usedRectangles,
            int binWidth,
            int binHeight) {

        Selection best = null;
        for (int itemIndex = 0; itemIndex < remainingItems.size(); itemIndex++) {
            Candidate candidate = findBestCandidate(
                    remainingItems.get(itemIndex),
                    freeRectangles,
                    usedRectangles,
                    binWidth,
                    binHeight);
            if (candidate == null) continue;

            if (best == null || candidate.score().compareTo(best.candidate().score()) < 0) {
                best = new Selection(itemIndex, candidate);
            }
        }
        return best;
    }

    private Candidate findBestCandidate(
            Item item,
            List<FreeRectangle> freeRectangles,
            List<Placement> usedRectangles,
            int binWidth,
            int binHeight) {

        Candidate best = null;
        for (FreeRectangle free : freeRectangles) {
            best = betterOf(best, candidateIfFits(
                    item, false, free, usedRectangles, binWidth, binHeight));
            if (rotationAllowed && item.width() != item.height()) {
                best = betterOf(best, candidateIfFits(
                        item, true, free, usedRectangles, binWidth, binHeight));
            }
        }
        return best;
    }

    private Candidate candidateIfFits(
            Item item,
            boolean rotated,
            FreeRectangle free,
            List<Placement> usedRectangles,
            int binWidth,
            int binHeight) {

        int width = rotated ? item.height() : item.width();
        int height = rotated ? item.width() : item.height();
        if (width > free.width() || height > free.height()) return null;

        Score score = score(
                heuristic, free, width, height, usedRectangles, binWidth, binHeight);
        return new Candidate(free.x(), free.y(), width, height, rotated, score);
    }

    private static Candidate betterOf(Candidate current, Candidate candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.compareTo(current) < 0) return candidate;
        return current;
    }

    /**
     * 对所有相交空闲矩形做四向切割并剪枝。分割结果允许互相重叠。
     */
    static List<FreeRectangle> splitFreeRectangles(
            List<FreeRectangle> freeRectangles,
            FreeRectangle occupied) {

        List<FreeRectangle> split = new ArrayList<>();
        for (FreeRectangle free : freeRectangles) {
            if (!intersects(free, occupied)) {
                split.add(free);
                continue;
            }

            // 新零件左侧的整高空闲条带。
            if (occupied.x() > free.x() && occupied.x() < free.right()) {
                addPositive(split, new FreeRectangle(
                        free.x(), free.y(), occupied.x() - free.x(), free.height()));
            }
            // 新零件右侧的整高空闲条带。
            if (occupied.right() < free.right() && occupied.right() > free.x()) {
                addPositive(split, new FreeRectangle(
                        occupied.right(), free.y(),
                        free.right() - occupied.right(), free.height()));
            }
            // 新零件下方的整宽空闲条带。
            if (occupied.y() > free.y() && occupied.y() < free.top()) {
                addPositive(split, new FreeRectangle(
                        free.x(), free.y(), free.width(), occupied.y() - free.y()));
            }
            // 新零件上方的整宽空闲条带。
            if (occupied.top() < free.top() && occupied.top() > free.y()) {
                addPositive(split, new FreeRectangle(
                        free.x(), occupied.top(),
                        free.width(), free.top() - occupied.top()));
            }
        }
        return pruneFreeList(split);
    }

    /**
     * 删除被其他空闲矩形完整包含的矩形；相同矩形只保留第一个。
     * 使用标记后统一收集，避免在双重循环中反复移动 ArrayList 元素。
     */
    static List<FreeRectangle> pruneFreeList(List<FreeRectangle> source) {
        boolean[] removed = new boolean[source.size()];
        for (int i = 0; i < source.size(); i++) {
            if (removed[i]) continue;
            for (int j = i + 1; j < source.size(); j++) {
                if (removed[j]) continue;
                FreeRectangle a = source.get(i);
                FreeRectangle b = source.get(j);
                if (contains(a, b)) {
                    removed[j] = true;
                } else if (contains(b, a)) {
                    removed[i] = true;
                    break;
                }
            }
        }

        List<FreeRectangle> result = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            if (!removed[i]) result.add(source.get(i));
        }
        return List.copyOf(result);
    }

    static Score score(
            MaxRectsHeuristic heuristic,
            FreeRectangle free,
            int width,
            int height,
            List<Placement> usedRectangles,
            int binWidth,
            int binHeight) {

        int horizontalLeftover = free.width() - width;
        int verticalLeftover = free.height() - height;
        int shortSideFit = Math.min(horizontalLeftover, verticalLeftover);
        int longSideFit = Math.max(horizontalLeftover, verticalLeftover);
        long areaFit = free.area() - (long) width * height;

        return switch (heuristic) {
            case BSSF -> new Score(shortSideFit, longSideFit, 0);
            case BLSF -> new Score(longSideFit, shortSideFit, 0);
            case BAF -> new Score(areaFit, shortSideFit, longSideFit);
            case BL -> new Score((long) free.y() + height, free.x(), 0);
            case CP -> new Score(-contactScore(
                    free.x(), free.y(), width, height,
                    usedRectangles, binWidth, binHeight), 0, 0);
        };
    }

    static int contactScore(
            int x,
            int y,
            int width,
            int height,
            List<Placement> usedRectangles,
            int binWidth,
            int binHeight) {

        int score = 0;
        if (x == 0 || x + width == binWidth) score += height;
        if (y == 0 || y + height == binHeight) score += width;

        for (Placement placed : usedRectangles) {
            if (placed.x() == x + width || placed.x() + placed.width() == x) {
                score += commonLength(y, y + height, placed.y(), placed.y() + placed.height());
            }
            if (placed.y() == y + height || placed.y() + placed.height() == y) {
                score += commonLength(x, x + width, placed.x(), placed.x() + placed.width());
            }
        }
        return score;
    }

    private static int commonLength(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return Math.max(0, Math.min(firstEnd, secondEnd) - Math.max(firstStart, secondStart));
    }

    private static boolean intersects(FreeRectangle a, FreeRectangle b) {
        return a.x() < b.right() && b.x() < a.right()
                && a.y() < b.top() && b.y() < a.top();
    }

    private static boolean contains(FreeRectangle outer, FreeRectangle inner) {
        return outer.x() <= inner.x() && outer.y() <= inner.y()
                && outer.right() >= inner.right() && outer.top() >= inner.top();
    }

    private static void addPositive(List<FreeRectangle> target, FreeRectangle rectangle) {
        if (rectangle.width() > 0 && rectangle.height() > 0) target.add(rectangle);
    }

    static record FreeRectangle(int x, int y, int width, int height) {
        FreeRectangle {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("空闲矩形尺寸必须为正数");
            }
        }

        int right() {
            return x + width;
        }

        int top() {
            return y + height;
        }

        long area() {
            return (long) width * height;
        }
    }

    static record Score(long first, long second, long third) implements Comparable<Score> {
        @Override
        public int compareTo(Score other) {
            int comparison = Long.compare(first, other.first);
            if (comparison != 0) return comparison;
            comparison = Long.compare(second, other.second);
            if (comparison != 0) return comparison;
            return Long.compare(third, other.third);
        }
    }

    private record Candidate(
            int x,
            int y,
            int width,
            int height,
            boolean rotated,
            Score score) implements Comparable<Candidate> {

        @Override
        public int compareTo(Candidate other) {
            int comparison = score.compareTo(other.score);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(y, other.y);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(x, other.x);
            if (comparison != 0) return comparison;
            comparison = Boolean.compare(rotated, other.rotated);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(width, other.width);
            if (comparison != 0) return comparison;
            return Integer.compare(height, other.height);
        }
    }

    private record Selection(int itemIndex, Candidate candidate) {}
}
