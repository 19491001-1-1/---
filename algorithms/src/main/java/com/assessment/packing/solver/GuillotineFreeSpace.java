package com.assessment.packing.solver;

import com.assessment.packing.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Guillotine求解器和Skyline废物地图共享的不相交自由矩形核心。 */
final class GuillotineFreeSpace {
    private final GuillotineRectChoice choice;
    private final GuillotineSplitRule splitRule;
    private final boolean mergeRectangles;
    private final List<FreeRectangle> freeRectangles = new ArrayList<>();

    GuillotineFreeSpace(
            GuillotineRectChoice choice,
            GuillotineSplitRule splitRule,
            boolean mergeRectangles) {
        this.choice = Objects.requireNonNull(choice, "自由矩形选择规则不能为空");
        this.splitRule = Objects.requireNonNull(splitRule, "分割规则不能为空");
        this.mergeRectangles = mergeRectangles;
    }

    void addFreeRectangle(FreeRectangle rectangle) {
        Objects.requireNonNull(rectangle, "自由矩形不能为空");
        if (rectangle.width() <= 0 || rectangle.height() <= 0) return;
        for (FreeRectangle existing : freeRectangles) {
            if (overlaps(existing, rectangle)) {
                throw new IllegalArgumentException("Guillotine自由矩形不能重叠："
                        + existing + " 与 " + rectangle);
            }
        }
        freeRectangles.add(rectangle);
        if (mergeRectangles) mergeAll();
    }

    Selection findBest(List<Item> items, boolean rotationAllowed) {
        Selection best = null;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            Item item = items.get(itemIndex);
            for (int freeIndex = 0; freeIndex < freeRectangles.size(); freeIndex++) {
                FreeRectangle free = freeRectangles.get(freeIndex);
                best = betterOf(best, candidate(
                        itemIndex, item, false, freeIndex, free,
                        item.width(), item.height()));
                if (rotationAllowed && item.width() != item.height()) {
                    best = betterOf(best, candidate(
                            itemIndex, item, true, freeIndex, free,
                            item.height(), item.width()));
                }
            }
        }
        return best;
    }

    Selection findFirstFitting(List<Item> items, boolean rotationAllowed) {
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            Selection selected = findBest(List.of(items.get(itemIndex)), rotationAllowed);
            if (selected != null) {
                return new Selection(
                        itemIndex, selected.item(), selected.freeIndex(),
                        selected.x(), selected.y(), selected.width(), selected.height(),
                        selected.rotated(), selected.score());
            }
        }
        return null;
    }

    PlacedRectangle place(Selection selection) {
        Objects.requireNonNull(selection, "放置选择不能为空");
        FreeRectangle free = freeRectangles.remove(selection.freeIndex());
        if (!contains(free, selection.x(), selection.y(), selection.width(), selection.height())) {
            throw new IllegalStateException("放置选择已经失效");
        }

        boolean horizontal = splitHorizontally(
                splitRule, free, selection.width(), selection.height());
        List<FreeRectangle> children = split(
                free, selection.width(), selection.height(), horizontal);
        freeRectangles.addAll(children);
        if (mergeRectangles) mergeAll();
        return new PlacedRectangle(
                selection.x(), selection.y(), selection.width(), selection.height(),
                selection.rotated(), children);
    }

    List<FreeRectangle> freeRectangles() {
        return List.copyOf(freeRectangles);
    }

    private Selection candidate(
            int itemIndex,
            Item item,
            boolean rotated,
            int freeIndex,
            FreeRectangle free,
            int width,
            int height) {
        if (width > free.width() || height > free.height()) return null;
        long areaFit = free.area() - (long) width * height;
        int shortFit = Math.min(free.width() - width, free.height() - height);
        int longFit = Math.max(free.width() - width, free.height() - height);
        boolean perfect = areaFit == 0;
        Score score = score(choice, perfect, areaFit, shortFit, longFit);
        return new Selection(
                itemIndex, item, freeIndex, free.x(), free.y(), width, height, rotated, score);
    }

    private static Selection betterOf(Selection current, Selection candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.score().compareTo(current.score()) < 0) {
            return candidate;
        }
        return current;
    }

    private static Score score(
            GuillotineRectChoice choice,
            boolean perfect,
            long areaFit,
            int shortFit,
            int longFit) {
        long first;
        long second;
        long third;
        switch (choice) {
            case BAF -> {
                first = areaFit;
                second = shortFit;
                third = longFit;
            }
            case BSSF -> {
                first = shortFit;
                second = longFit;
                third = areaFit;
            }
            case BLSF -> {
                first = longFit;
                second = shortFit;
                third = areaFit;
            }
            case WAF -> {
                first = -areaFit;
                second = -shortFit;
                third = -longFit;
            }
            case WSSF -> {
                first = -shortFit;
                second = -longFit;
                third = -areaFit;
            }
            case WLSF -> {
                first = -longFit;
                second = -shortFit;
                third = -areaFit;
            }
            default -> throw new IllegalStateException("未知Guillotine选择规则：" + choice);
        }
        // 完全匹配优先，避免最差适配评分把零浪费候选排到末尾。
        return new Score(perfect ? 0 : 1, first, second, third);
    }

    static List<FreeRectangle> split(
            FreeRectangle free,
            int placedWidth,
            int placedHeight,
            boolean horizontal) {
        if (placedWidth <= 0 || placedHeight <= 0
                || placedWidth > free.width() || placedHeight > free.height()) {
            throw new IllegalArgumentException("待放矩形不能按给定尺寸放入自由矩形");
        }

        List<FreeRectangle> result = new ArrayList<>(2);
        int remainingWidth = free.width() - placedWidth;
        int remainingHeight = free.height() - placedHeight;
        if (horizontal) {
            addPositive(result, new FreeRectangle(
                    free.x(), free.y() + placedHeight,
                    free.width(), remainingHeight));
            addPositive(result, new FreeRectangle(
                    free.x() + placedWidth, free.y(),
                    remainingWidth, placedHeight));
        } else {
            addPositive(result, new FreeRectangle(
                    free.x(), free.y() + placedHeight,
                    placedWidth, remainingHeight));
            addPositive(result, new FreeRectangle(
                    free.x() + placedWidth, free.y(),
                    remainingWidth, free.height()));
        }
        return List.copyOf(result);
    }

    static boolean splitHorizontally(
            GuillotineSplitRule rule,
            FreeRectangle free,
            int placedWidth,
            int placedHeight) {
        int remainingWidth = free.width() - placedWidth;
        int remainingHeight = free.height() - placedHeight;
        return switch (rule) {
            case SAS -> free.width() <= free.height();
            case LAS -> free.width() > free.height();
            case SLAS -> remainingWidth <= remainingHeight;
            case LLAS -> remainingWidth > remainingHeight;
            // A1=右下，A2=左上；MAXAS把右上A3接到较小者，MINAS接到较大者。
            case MAXAS -> (long) placedWidth * remainingHeight
                    <= (long) remainingWidth * placedHeight;
            case MINAS -> (long) placedWidth * remainingHeight
                    > (long) remainingWidth * placedHeight;
        };
    }

    private void mergeAll() {
        boolean merged;
        do {
            merged = false;
            outer:
            for (int i = 0; i < freeRectangles.size(); i++) {
                for (int j = i + 1; j < freeRectangles.size(); j++) {
                    FreeRectangle combined = merge(freeRectangles.get(i), freeRectangles.get(j));
                    if (combined == null) continue;
                    freeRectangles.remove(j);
                    freeRectangles.set(i, combined);
                    merged = true;
                    break outer;
                }
            }
        } while (merged);
    }

    static FreeRectangle merge(FreeRectangle a, FreeRectangle b) {
        if (a.x() == b.x() && a.width() == b.width()) {
            if (a.top() == b.y()) {
                return new FreeRectangle(a.x(), a.y(), a.width(), a.height() + b.height());
            }
            if (b.top() == a.y()) {
                return new FreeRectangle(b.x(), b.y(), b.width(), a.height() + b.height());
            }
        }
        if (a.y() == b.y() && a.height() == b.height()) {
            if (a.right() == b.x()) {
                return new FreeRectangle(a.x(), a.y(), a.width() + b.width(), a.height());
            }
            if (b.right() == a.x()) {
                return new FreeRectangle(b.x(), b.y(), a.width() + b.width(), b.height());
            }
        }
        return null;
    }

    private static void addPositive(List<FreeRectangle> target, FreeRectangle rectangle) {
        if (rectangle.width() > 0 && rectangle.height() > 0) target.add(rectangle);
    }

    private static boolean contains(
            FreeRectangle free, int x, int y, int width, int height) {
        return x >= free.x() && y >= free.y()
                && (long) x + width <= free.right()
                && (long) y + height <= free.top();
    }

    static boolean overlaps(FreeRectangle a, FreeRectangle b) {
        return a.x() < b.right() && a.right() > b.x()
                && a.y() < b.top() && a.top() > b.y();
    }

    record FreeRectangle(int x, int y, int width, int height) {
        FreeRectangle {
            if (x < 0 || y < 0 || width < 0 || height < 0) {
                throw new IllegalArgumentException("自由矩形坐标和尺寸不能为负数");
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

    record Selection(
            int itemIndex,
            Item item,
            int freeIndex,
            int x,
            int y,
            int width,
            int height,
            boolean rotated,
            Score score) {}

    record PlacedRectangle(
            int x,
            int y,
            int width,
            int height,
            boolean rotated,
            List<FreeRectangle> children) {}

    private record Score(long perfectPriority, long first, long second, long third)
            implements Comparable<Score> {
        @Override
        public int compareTo(Score other) {
            int result = Long.compare(perfectPriority, other.perfectPriority);
            if (result != 0) return result;
            result = Long.compare(first, other.first);
            if (result != 0) return result;
            result = Long.compare(second, other.second);
            if (result != 0) return result;
            return Long.compare(third, other.third);
        }
    }
}
