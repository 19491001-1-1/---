package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shelf算法家族，可选Guillotine废物地图改进。 */
public final class ShelfSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;
    private final ShelfHeuristic heuristic;
    private final boolean wasteMapEnabled;
    private final ShelfSelectionMode selectionMode;

    /** 保留原项目入口：默认仍是SHELF-NF。 */
    public ShelfSolver(boolean rotationAllowed, ItemOrder order) {
        this(rotationAllowed, order, ShelfHeuristic.NEXT_FIT, false, ShelfSelectionMode.SEQUENTIAL);
    }

    public ShelfSolver(
            boolean rotationAllowed,
            ItemOrder order,
            ShelfHeuristic heuristic) {
        this(rotationAllowed, order, heuristic, false, ShelfSelectionMode.SEQUENTIAL);
    }

    public ShelfSolver(
            boolean rotationAllowed,
            ItemOrder order,
            ShelfHeuristic heuristic,
            boolean wasteMapEnabled) {
        this(rotationAllowed, order, heuristic, wasteMapEnabled, ShelfSelectionMode.SEQUENTIAL);
    }

    public ShelfSolver(
            boolean rotationAllowed,
            ItemOrder order,
            ShelfHeuristic heuristic,
            boolean wasteMapEnabled,
            ShelfSelectionMode selectionMode) {
        if (wasteMapEnabled && selectionMode == ShelfSelectionMode.GLOBAL) {
            throw new IllegalArgumentException("Shelf-GLOBAL与WM均会改变取件顺序，请作为独立扩展使用");
        }
        this.rotationAllowed = rotationAllowed;
        this.order = Objects.requireNonNull(order, "排序规则不能为空");
        this.heuristic = Objects.requireNonNull(heuristic, "Shelf选层规则不能为空");
        this.wasteMapEnabled = wasteMapEnabled;
        this.selectionMode = Objects.requireNonNull(selectionMode, "Shelf选择模式不能为空");
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Placement> placements = new ArrayList<>();
        if (selectionMode == ShelfSelectionMode.GLOBAL) {
            runGlobal(instance, placements);
        } else {
            run(instance, placements);
        }
        return new Solution(name(), placements, System.nanoTime() - start);
    }

    /**
     * 放置列表只作输出，层状态在各 {@link Shelf} 里，因此轻量路径完全不构造 {@link Placement}。
     */
    @Override
    public long packedAreaOf(Instance instance) {
        return selectionMode == ShelfSelectionMode.GLOBAL
                ? runGlobal(instance, null)
                : run(instance, null);
    }

    @Override
    public String name() {
        return "Shelf-" + heuristic.abbreviation()
                + (wasteMapEnabled ? "-WM" : "")
                + (selectionMode == ShelfSelectionMode.GLOBAL ? "-GLOBAL" : "")
                + "(" + order + ", rotation=" + rotationAllowed + ")";
    }

    /** {@code placements} 为 {@code null} 时只累计面积，不构造放置对象。 */
    private long run(Instance instance, List<Placement> placements) {
        long area = 0;
        List<Item> items = ItemOrdering.sort(instance.items(), order);
        List<Shelf> shelves = new ArrayList<>();
        shelves.add(new Shelf(0));
        GuillotineFreeSpace wasteMap = wasteMapEnabled
                ? new GuillotineFreeSpace(
                        GuillotineRectChoice.BSSF,
                        GuillotineSplitRule.MAXAS,
                        true)
                : null;

        for (Item item : items) {
            if (tryWasteMap(item, placements, wasteMap)) {
                area += item.area();
                continue;
            }

            ShelfCandidate candidate = findShelf(instance, shelves, item);
            if (candidate != null) {
                placeOnShelf(item, shelves.get(candidate.shelfIndex()),
                        candidate.orientation(), placements);
                area += item.area();
                continue;
            }

            Shelf open = shelves.get(shelves.size() - 1);
            if (open.currentX == 0) continue;
            int newShelfY = open.startY + open.height;
            Shelf newShelf = new Shelf(newShelfY);
            Orientation orientation = orientForShelf(instance, newShelf, item, true);
            if (orientation == null) continue;

            if (wasteMap != null) moveShelfGapsToWasteMap(open, wasteMap);
            shelves.add(newShelf);
            placeOnShelf(item, newShelf, orientation, placements);
            area += item.area();
        }
        return area;
    }

    /** {@code placements} 为 {@code null} 时只累计面积，不构造放置对象。 */
    private long runGlobal(Instance instance, List<Placement> placements) {
        long area = 0;
        List<Item> remaining = new ArrayList<>(ItemOrdering.sort(instance.items(), order));
        List<Shelf> shelves = new ArrayList<>();
        shelves.add(new Shelf(0));

        while (!remaining.isEmpty()) {
            GlobalCandidate best = null;
            for (int itemIndex = 0; itemIndex < remaining.size(); itemIndex++) {
                ShelfCandidate shelf = findShelf(instance, shelves, remaining.get(itemIndex));
                if (shelf == null) continue;
                GlobalCandidate candidate = new GlobalCandidate(itemIndex, shelf);
                if (best == null || compareGlobal(candidate, best) < 0) best = candidate;
            }
            if (best != null) {
                Item item = remaining.remove(best.itemIndex());
                placeOnShelf(item, shelves.get(best.shelf().shelfIndex()),
                        best.shelf().orientation(), placements);
                area += item.area();
                continue;
            }

            Shelf open = shelves.get(shelves.size() - 1);
            int newY = open.startY + open.height;
            Item selected = null;
            Orientation orientation = null;
            int selectedIndex = -1;
            Shelf next = new Shelf(newY);
            for (int itemIndex = 0; itemIndex < remaining.size(); itemIndex++) {
                Orientation current = orientForShelf(instance, next, remaining.get(itemIndex), true);
                if (current != null) {
                    selected = remaining.get(itemIndex);
                    orientation = current;
                    selectedIndex = itemIndex;
                    break;
                }
            }
            if (selected == null) break;
            if (open.currentX == 0) {
                // 空层说明剩余零件均无法进入当前板材。
                break;
            }
            shelves.add(next);
            remaining.remove(selectedIndex);
            placeOnShelf(selected, next, orientation, placements);
            area += selected.area();
        }
        return area;
    }

    private static int compareGlobal(GlobalCandidate left, GlobalCandidate right) {
        int score = Long.compare(left.shelf().score(), right.shelf().score());
        if (score != 0) return score;
        int shelf = Integer.compare(left.shelf().shelfIndex(), right.shelf().shelfIndex());
        if (shelf != 0) return shelf;
        return Integer.compare(left.itemIndex(), right.itemIndex());
    }

    private boolean tryWasteMap(
            Item item,
            List<Placement> placements,
            GuillotineFreeSpace wasteMap) {
        if (wasteMap == null) return false;
        GuillotineFreeSpace.Selection selection = wasteMap.findBest(
                List.of(item), rotationAllowed);
        if (selection == null) return false;
        GuillotineFreeSpace.PlacedRectangle placed = wasteMap.place(selection);
        if (placements != null) {
            placements.add(new Placement(item, placed.x(), placed.y(), placed.rotated()));
        }
        return true;
    }

    private ShelfCandidate findShelf(
            Instance instance,
            List<Shelf> shelves,
            Item item) {
        if (heuristic == ShelfHeuristic.NEXT_FIT) {
            int index = shelves.size() - 1;
            Orientation orientation = orientForShelf(
                    instance, shelves.get(index), item, true);
            return orientation == null ? null : new ShelfCandidate(index, orientation, 0);
        }

        ShelfCandidate best = null;
        for (int index = 0; index < shelves.size(); index++) {
            Shelf shelf = shelves.get(index);
            boolean canResize = index == shelves.size() - 1;
            Orientation orientation = orientForShelf(instance, shelf, item, canResize);
            if (orientation == null) continue;

            if (heuristic == ShelfHeuristic.FIRST_FIT) {
                return new ShelfCandidate(index, orientation, index);
            }

            long widthLeft = (long) instance.binWidth()
                    - shelf.currentX - orientation.width();
            long score = switch (heuristic) {
                case BEST_WIDTH_FIT -> widthLeft;
                case BEST_HEIGHT_FIT -> Math.max(shelf.height - orientation.height(), 0);
                // 跟随原始实现：比较放置前该层尚未使用的矩形面积。
                case BEST_AREA_FIT -> (long) (instance.binWidth() - shelf.currentX)
                        * shelf.height;
                case WORST_WIDTH_FIT -> -widthLeft;
                default -> throw new IllegalStateException("Shelf规则不可达：" + heuristic);
            };
            ShelfCandidate current = new ShelfCandidate(index, orientation, score);

            // 完全匹配优先，避免最差适配评分把零浪费候选排到末尾。
            if (heuristic == ShelfHeuristic.WORST_WIDTH_FIT && widthLeft == 0) {
                return current;
            }
            if (best == null || current.score() < best.score()) best = current;
        }
        return best;
    }

    private Orientation orientForShelf(
            Instance instance,
            Shelf shelf,
            Item item,
            boolean canResize) {
        int width = item.width();
        int height = item.height();
        boolean rotated = false;
        int widthLeft = instance.binWidth() - shelf.currentX;

        if (rotationAllowed && width != height
                && ((width > height && width > widthLeft)
                || (width > height && width < shelf.height)
                || (width < height && height > shelf.height && height <= widthLeft))) {
            int temporary = width;
            width = height;
            height = temporary;
            rotated = true;
        }

        Orientation preferred = new Orientation(width, height, rotated);
        if (fits(instance, shelf, preferred, canResize)) return preferred;
        if (!rotationAllowed || item.width() == item.height()) return null;

        Orientation alternative = new Orientation(height, width, !rotated);
        return fits(instance, shelf, alternative, canResize) ? alternative : null;
    }

    private static boolean fits(
            Instance instance,
            Shelf shelf,
            Orientation orientation,
            boolean canResize) {
        int availableHeight = canResize
                ? instance.binHeight() - shelf.startY
                : shelf.height;
        return (long) shelf.currentX + orientation.width() <= instance.binWidth()
                && orientation.height() <= availableHeight;
    }

    private static void placeOnShelf(
            Item item,
            Shelf shelf,
            Orientation orientation,
            List<Placement> placements) {
        if (placements != null) {
            placements.add(new Placement(
                    item, shelf.currentX, shelf.startY, orientation.rotated()));
        }
        shelf.usedRectangles.add(new UsedRectangle(
                shelf.currentX, shelf.startY,
                orientation.width(), orientation.height()));
        shelf.currentX += orientation.width();
        shelf.height = Math.max(shelf.height, orientation.height());
    }

    private static void moveShelfGapsToWasteMap(
            Shelf shelf,
            GuillotineFreeSpace wasteMap) {
        for (UsedRectangle rectangle : shelf.usedRectangles) {
            int gapHeight = shelf.height - rectangle.height();
            if (gapHeight > 0) {
                wasteMap.addFreeRectangle(new GuillotineFreeSpace.FreeRectangle(
                        rectangle.x(), rectangle.y() + rectangle.height(),
                        rectangle.width(), gapHeight));
            }
        }
        shelf.usedRectangles.clear();
    }

    private static final class Shelf {
        private int currentX;
        private final int startY;
        private int height;
        private final List<UsedRectangle> usedRectangles = new ArrayList<>();

        private Shelf(int startY) {
            this.startY = startY;
        }
    }

    private record Orientation(int width, int height, boolean rotated) {}

    private record ShelfCandidate(int shelfIndex, Orientation orientation, long score) {}

    private record GlobalCandidate(int itemIndex, ShelfCandidate shelf) {}

    private record UsedRectangle(int x, int y, int width, int height) {}
}
