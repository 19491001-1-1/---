package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.List;

/**
 * Shelf Floor-Ceiling 双向填充算法。
 * 每层地板从左向右放；层关闭后，天花板从右向左回填，并用精确碰撞检查保证合法。
 */
public final class ShelfFloorCeilingSolver implements PackingAlgorithm {
    private final boolean rotationAllowed;
    private final ItemOrder order;

    public ShelfFloorCeilingSolver(boolean rotationAllowed) {
        this(rotationAllowed, ItemOrder.LONG_SIDE_DESC);
    }

    public ShelfFloorCeilingSolver(boolean rotationAllowed, ItemOrder order) {
        this.rotationAllowed = rotationAllowed;
        this.order = java.util.Objects.requireNonNull(order, "排序规则不能为空");
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Item> items = ItemOrdering.sort(instance.items(), order);
        List<Placement> placements = new ArrayList<>();
        List<Shelf> closedShelves = new ArrayList<>();
        Shelf open = new Shelf(0, instance.binWidth());

        for (Item item : items) {
            FloorCandidate floor = bestFloorOrientation(instance, open, item);
            if (floor != null) {
                Placement placement = new Placement(item, open.floorX, open.y, floor.rotated());
                add(open, placement, placements);
                open.floorX += placement.width();
                open.height = Math.max(open.height, placement.height());
                continue;
            }

            if (!open.rectangles.isEmpty()) {
                closedShelves.add(open);
            }

            CeilingCandidate ceiling = findCeilingCandidate(closedShelves, item);
            if (ceiling != null) {
                Shelf shelf = ceiling.shelf();
                Placement placement = new Placement(
                        item, ceiling.x(), shelf.y + shelf.height - ceiling.height(),
                        ceiling.rotated());
                add(shelf, placement, placements);
                shelf.ceilingX = ceiling.x();
                Shelf top = closedShelves.get(closedShelves.size() - 1);
                open = new Shelf(top.y + top.height, instance.binWidth());
                continue;
            }

            int nextY = closedShelves.isEmpty()
                    ? 0
                    : closedShelves.get(closedShelves.size() - 1).y
                            + closedShelves.get(closedShelves.size() - 1).height;
            open = new Shelf(nextY, instance.binWidth());
            floor = bestFloorOrientation(instance, open, item);
            if (floor == null) continue;
            Placement placement = new Placement(item, 0, open.y, floor.rotated());
            add(open, placement, placements);
            open.floorX = placement.width();
            open.height = placement.height();
        }

        return new Solution(name(), placements, System.nanoTime() - start);
    }

    @Override
    public String name() {
        return "Shelf-Floor-Ceiling(" + order + ", rotation=" + rotationAllowed + ")";
    }

    private FloorCandidate bestFloorOrientation(Instance instance, Shelf shelf, Item item) {
        FloorCandidate best = null;
        for (boolean rotated : rotations(item)) {
            int width = rotated ? item.height() : item.width();
            int height = rotated ? item.width() : item.height();
            if ((long) shelf.floorX + width > instance.binWidth()) continue;
            if ((long) shelf.y + Math.max(shelf.height, height) > instance.binHeight()) continue;
            FloorCandidate candidate = new FloorCandidate(rotated, width, height);
            if (best == null
                    || candidate.height() < best.height()
                    || (candidate.height() == best.height() && candidate.width() < best.width())) {
                best = candidate;
            }
        }
        return best;
    }

    private CeilingCandidate findCeilingCandidate(List<Shelf> shelves, Item item) {
        CeilingCandidate best = null;
        for (int i = shelves.size() - 1; i >= 0; i--) {
            Shelf shelf = shelves.get(i);
            for (boolean rotated : rotations(item)) {
                int width = rotated ? item.height() : item.width();
                int height = rotated ? item.width() : item.height();
                if (width > shelf.ceilingX || height > shelf.height) continue;
                int x = rightmostNonOverlappingX(shelf, width, height);
                if (x < 0) continue;
                CeilingCandidate candidate = new CeilingCandidate(
                        shelf, x, width, height, rotated);
                if (best == null
                        || candidate.shelf().y < best.shelf().y
                        || (candidate.shelf().y == best.shelf().y && candidate.x() > best.x())
                        || (candidate.shelf().y == best.shelf().y && candidate.x() == best.x()
                        && candidate.height() < best.height())) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** 从当前天花板右边界向左滑动，跳过所有碰撞矩形。 */
    private static int rightmostNonOverlappingX(Shelf shelf, int width, int height) {
        int x = shelf.ceilingX - width;
        int y = shelf.y + shelf.height - height;
        while (x >= 0) {
            Rect collision = null;
            for (Rect rectangle : shelf.rectangles) {
                if (overlaps(x, y, width, height, rectangle)) {
                    // 选最靠右的碰撞体，只做刚好越过它的最小左移。
                    if (collision == null || rectangle.x > collision.x) collision = rectangle;
                }
            }
            if (collision == null) return x;
            x = collision.x - width;
        }
        return -1;
    }

    private static boolean overlaps(int x, int y, int width, int height, Rect other) {
        return x < other.x + other.width && (long) x + width > other.x
                && y < other.y + other.height && (long) y + height > other.y;
    }

    private List<Boolean> rotations(Item item) {
        if (rotationAllowed && item.width() != item.height()) return List.of(false, true);
        return List.of(false);
    }

    private static void add(Shelf shelf, Placement placement, List<Placement> placements) {
        placements.add(placement);
        shelf.rectangles.add(new Rect(
                placement.x(), placement.y(), placement.width(), placement.height()));
    }

    private static final class Shelf {
        private final int y;
        private int height;
        private int floorX;
        private int ceilingX;
        private final List<Rect> rectangles = new ArrayList<>();

        private Shelf(int y, int binWidth) {
            this.y = y;
            this.ceilingX = binWidth;
        }
    }

    private record Rect(int x, int y, int width, int height) {}
    private record FloorCandidate(boolean rotated, int width, int height) {}
    private record CeilingCandidate(
            Shelf shelf, int x, int width, int height, boolean rotated) {}
}
