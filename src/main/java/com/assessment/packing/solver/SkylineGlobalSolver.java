package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 支持 Bottom-Left 与 Best-Fit 评分的全局选择 Skyline。
 *
 * <p>天际线由从左到右、无缝覆盖板材宽度的水平段组成。候选矩形的左边与某段左端对齐，
 * 放置高度等于其横跨范围内的最高天际线。BL最小化放置后的顶边，BF最小化矩形下方
 * 因跨越较低线段而无法再利用的面积。全局选择模式每轮比较
 * 全部“未放零件 × 天际线起点 × 允许朝向”，排序规则只在评分完全相同时稳定破平局。</p>
 */
public final class SkylineGlobalSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;
    private final SkylinePlacementHeuristic heuristic;
    private final boolean wasteMapEnabled;
    private final SkylineSelectionMode selectionMode;

    public SkylineGlobalSolver(
            boolean rotationAllowed,
            ItemOrder order,
            SkylinePlacementHeuristic heuristic) {
        this(rotationAllowed, order, heuristic, false, SkylineSelectionMode.GLOBAL);
    }

    public SkylineGlobalSolver(
            boolean rotationAllowed,
            ItemOrder order,
            SkylinePlacementHeuristic heuristic,
            boolean wasteMapEnabled) {
        this(rotationAllowed, order, heuristic, wasteMapEnabled,
                wasteMapEnabled ? SkylineSelectionMode.SEQUENTIAL : SkylineSelectionMode.GLOBAL);
    }

    public SkylineGlobalSolver(
            boolean rotationAllowed,
            ItemOrder order,
            SkylinePlacementHeuristic heuristic,
            boolean wasteMapEnabled,
            SkylineSelectionMode selectionMode) {
        this.rotationAllowed = rotationAllowed;
        this.order = Objects.requireNonNull(order, "排序规则不能为空");
        this.heuristic = Objects.requireNonNull(heuristic, "Skyline启发式不能为空");
        this.wasteMapEnabled = wasteMapEnabled;
        this.selectionMode = Objects.requireNonNull(selectionMode, "Skyline选择模式不能为空");
    }

    @Override
    public String name() {
        return "Skyline-" + (heuristic == SkylinePlacementHeuristic.BOTTOM_LEFT ? "BL" : "BF")
                + (wasteMapEnabled ? "-WM" : "")
                + (selectionMode == SkylineSelectionMode.GLOBAL ? "-GLOBAL" : "")
                + "(" + order + ", rotation=" + rotationAllowed + ")";
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Placement> placements = new ArrayList<>();
        run(instance, placements);
        return new Solution(name(), placements, System.nanoTime() - start);
    }

    /**
     * 放置列表只作输出，天际线状态在各自的 {@link SkylineLevel} 列表里，
     * 因此轻量路径完全不构造 {@link Placement}。
     */
    @Override
    public long packedAreaOf(Instance instance) {
        return run(instance, null);
    }

    /** {@code placements} 为 {@code null} 时只累计面积，不构造放置对象。 */
    private long run(Instance instance, List<Placement> placements) {
        List<Item> remaining = new ArrayList<>(ItemOrdering.sort(instance.items(), order));
        List<SkylineLevel> skyline = new ArrayList<>();
        skyline.add(new SkylineLevel(0, 0, instance.binWidth()));
        GuillotineFreeSpace wasteMap = wasteMapEnabled
                ? new GuillotineFreeSpace(
                        GuillotineRectChoice.BSSF,
                        GuillotineSplitRule.MAXAS,
                        true)
                : null;

        if (selectionMode == SkylineSelectionMode.SEQUENTIAL) {
            return solveSequential(instance, remaining, skyline, placements, wasteMap);
        }

        long area = 0;
        while (!remaining.isEmpty()) {
            if (wasteMap != null) {
                GuillotineFreeSpace.Selection wasteSelection = wasteMap.findBest(
                        remaining, rotationAllowed);
                if (wasteSelection != null) {
                    Item item = remaining.remove(wasteSelection.itemIndex());
                    GuillotineFreeSpace.PlacedRectangle placed = wasteMap.place(wasteSelection);
                    area += item.area();
                    if (placements != null) {
                        placements.add(new Placement(
                                item, placed.x(), placed.y(), placed.rotated()));
                    }
                    continue;
                }
            }

            Candidate best = findBestCandidate(instance, remaining, skyline);
            if (best == null) break;

            area += best.item().area();
            if (placements != null) {
                placements.add(new Placement(
                        best.item(), best.fit().x(), best.fit().y(), best.rotated()));
            }
            if (wasteMap != null) addWasteMapAreas(wasteMap, skyline, best);
            addSkylineLevel(skyline, best.levelIndex(), best.fit(), best.width(), best.height());
            remaining.remove(best.item());
        }

        return area;
    }

    private long solveSequential(
            Instance instance,
            List<Item> remaining,
            List<SkylineLevel> skyline,
            List<Placement> placements,
            GuillotineFreeSpace wasteMap) {
        long area = 0;
        while (!remaining.isEmpty()) {
            Item item = remaining.remove(0);
            if (wasteMap != null) {
                GuillotineFreeSpace.Selection wasteSelection = wasteMap.findBest(
                        List.of(item), rotationAllowed);
                if (wasteSelection != null) {
                    GuillotineFreeSpace.PlacedRectangle placed = wasteMap.place(wasteSelection);
                    area += item.area();
                    if (placements != null) {
                        placements.add(new Placement(
                                item, placed.x(), placed.y(), placed.rotated()));
                    }
                    continue;
                }
            }

            Candidate best = findBestCandidate(instance, List.of(item), skyline);
            if (best == null) continue;
            area += item.area();
            if (placements != null) {
                placements.add(new Placement(
                        item, best.fit().x(), best.fit().y(), best.rotated()));
            }
            if (wasteMap != null) addWasteMapAreas(wasteMap, skyline, best);
            addSkylineLevel(skyline, best.levelIndex(), best.fit(), best.width(), best.height());
        }
        return area;
    }

    private static void addWasteMapAreas(
            GuillotineFreeSpace wasteMap,
            List<SkylineLevel> skyline,
            Candidate candidate) {
        int right = candidate.fit().x() + candidate.width();
        for (int index = candidate.levelIndex(); index < skyline.size(); index++) {
            SkylineLevel level = skyline.get(index);
            if (level.x() >= right) break;
            int overlapRight = Math.min(right, level.x() + level.width());
            int overlapWidth = overlapRight - level.x();
            int gapHeight = candidate.fit().y() - level.y();
            if (overlapWidth > 0 && gapHeight > 0) {
                wasteMap.addFreeRectangle(new GuillotineFreeSpace.FreeRectangle(
                        level.x(), level.y(), overlapWidth, gapHeight));
            }
        }
    }

    private Candidate findBestCandidate(
            Instance instance,
            List<Item> remaining,
            List<SkylineLevel> skyline) {
        Candidate best = null;
        for (Item item : remaining) {
            for (int levelIndex = 0; levelIndex < skyline.size(); levelIndex++) {
                Candidate normal = candidateAt(
                        instance, skyline, levelIndex, item,
                        item.width(), item.height(), false);
                if (isBetter(normal, best)) best = normal;

                if (rotationAllowed && item.width() != item.height()) {
                    Candidate rotated = candidateAt(
                            instance, skyline, levelIndex, item,
                            item.height(), item.width(), true);
                    if (isBetter(rotated, best)) best = rotated;
                }
            }
        }
        return best;
    }

    private static Candidate candidateAt(
            Instance instance,
            List<SkylineLevel> skyline,
            int levelIndex,
            Item item,
            int width,
            int height,
            boolean rotated) {
        Fit fit = fitAt(
                skyline, levelIndex, width, height,
                instance.binWidth(), instance.binHeight());
        return fit == null ? null : new Candidate(
                item, width, height, rotated, levelIndex, fit);
    }

    private boolean isBetter(Candidate candidate, Candidate best) {
        if (candidate == null) return false;
        if (best == null) return true;

        if (heuristic == SkylinePlacementHeuristic.BEST_FIT) {
            int byWaste = Long.compare(candidate.fit().wastedArea(), best.fit().wastedArea());
            if (byWaste != 0) return byWaste < 0;
        }

        int byTop = Integer.compare(candidate.fit().top(), best.fit().top());
        if (byTop != 0) return byTop < 0;
        int byX = Integer.compare(candidate.fit().x(), best.fit().x());
        if (byX != 0) return byX < 0;
        return candidate.fit().y() < best.fit().y();
    }

    static Fit fitAt(
            List<SkylineLevel> skyline,
            int levelIndex,
            int width,
            int height,
            int binWidth,
            int binHeight) {
        if (width <= 0 || height <= 0 || levelIndex < 0 || levelIndex >= skyline.size()) {
            return null;
        }
        int x = skyline.get(levelIndex).x();
        if ((long) x + width > binWidth) return null;

        int y = 0;
        int widthLeft = width;
        int index = levelIndex;
        while (widthLeft > 0) {
            if (index >= skyline.size()) return null;
            SkylineLevel level = skyline.get(index++);
            y = Math.max(y, level.y());
            if ((long) y + height > binHeight) return null;
            widthLeft -= level.width();
        }

        long wastedArea = 0;
        widthLeft = width;
        index = levelIndex;
        while (widthLeft > 0) {
            SkylineLevel level = skyline.get(index++);
            int overlap = Math.min(widthLeft, level.width());
            wastedArea += (long) (y - level.y()) * overlap;
            widthLeft -= overlap;
        }
        return new Fit(x, y, y + height, wastedArea);
    }

    private static void addSkylineLevel(
            List<SkylineLevel> skyline,
            int levelIndex,
            Fit fit,
            int width,
            int height) {
        int right = fit.x() + width;
        skyline.add(levelIndex, new SkylineLevel(fit.x(), fit.y() + height, width));

        int index = levelIndex + 1;
        while (index < skyline.size()) {
            SkylineLevel level = skyline.get(index);
            if (level.x() >= right) break;

            int covered = right - level.x();
            if (covered < level.width()) {
                skyline.set(index, new SkylineLevel(
                        level.x() + covered, level.y(), level.width() - covered));
                break;
            }
            skyline.remove(index);
        }
        mergeEqualNeighbors(skyline);
    }

    private static void mergeEqualNeighbors(List<SkylineLevel> skyline) {
        int index = 0;
        while (index + 1 < skyline.size()) {
            SkylineLevel left = skyline.get(index);
            SkylineLevel right = skyline.get(index + 1);
            if (left.y() == right.y() && left.x() + left.width() == right.x()) {
                skyline.set(index, new SkylineLevel(
                        left.x(), left.y(), left.width() + right.width()));
                skyline.remove(index + 1);
            } else {
                index++;
            }
        }
    }

    static record SkylineLevel(int x, int y, int width) {
        SkylineLevel {
            if (x < 0 || y < 0 || width <= 0) {
                throw new IllegalArgumentException("非法天际线段");
            }
        }
    }

    static record Fit(int x, int y, int top, long wastedArea) {}

    private record Candidate(
            Item item,
            int width,
            int height,
            boolean rotated,
            int levelIndex,
            Fit fit) {}
}
