package com.assessment.packing.solver;

import com.assessment.packing.model.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ItemOrdering {
    private ItemOrdering() {}

    static List<Item> sort(List<Item> source, ItemOrder order) {
        List<Item> result = new ArrayList<>(source);
        if (order == ItemOrder.INPUT) return result;
        Comparator<Item> byId = Comparator.comparingInt(Item::id);
        Comparator<Item> byArea = Comparator.comparingLong(Item::area);
        Comparator<Item> byShortSide = Comparator.comparingInt(ItemOrdering::shortSide);
        Comparator<Item> byLongSide = Comparator.comparingInt(ItemOrdering::longSide);
        Comparator<Item> byPerimeter = Comparator.comparingLong(ItemOrdering::perimeter);
        Comparator<Item> byDifference = Comparator.comparingInt(ItemOrdering::sideDifference);
        Comparator<Item> byRatio = ItemOrdering::compareSideRatio;
        Comparator<Item> comparator = switch (order) {
            case INPUT -> throw new IllegalStateException("INPUT应在排序前直接返回");
            case HEIGHT_DESC -> Comparator.comparingInt(Item::height).reversed()
                    .thenComparing(Comparator.comparingLong(Item::area).reversed())
                    .thenComparing(byId);
            case AREA_ASC -> byArea.thenComparing(byLongSide).thenComparing(byId);
            case AREA_DESC -> byArea.reversed()
                    .thenComparing(Comparator.comparingInt(Item::height).reversed())
                    .thenComparing(byId);
            case SHORT_SIDE_ASC -> byShortSide.thenComparing(byLongSide).thenComparing(byId);
            case SHORT_SIDE_DESC -> byShortSide.reversed()
                    .thenComparing(byLongSide.reversed()).thenComparing(byId);
            case LONG_SIDE_ASC -> byLongSide.thenComparing(byShortSide).thenComparing(byId);
            case LONG_SIDE_DESC -> byLongSide.reversed()
                    .thenComparing(byShortSide.reversed()).thenComparing(byId);
            case PERIMETER_ASC -> byPerimeter.thenComparing(byArea).thenComparing(byId);
            case PERIMETER_DESC -> byPerimeter.reversed()
                    .thenComparing(byArea.reversed()).thenComparing(byId);
            case SIDE_DIFFERENCE_ASC -> byDifference.thenComparing(byArea).thenComparing(byId);
            case SIDE_DIFFERENCE_DESC -> byDifference.reversed()
                    .thenComparing(byArea.reversed()).thenComparing(byId);
            case SIDE_RATIO_ASC -> byRatio.thenComparing(byArea).thenComparing(byId);
            case SIDE_RATIO_DESC -> byRatio.reversed()
                    .thenComparing(byArea.reversed()).thenComparing(byId);
        };
        result.sort(comparator);
        return result;
    }

    private static int shortSide(Item item) {
        return Math.min(item.width(), item.height());
    }

    private static int longSide(Item item) {
        return Math.max(item.width(), item.height());
    }

    private static long perimeter(Item item) {
        return 2L * (item.width() + (long) item.height());
    }

    private static int sideDifference(Item item) {
        return Math.abs(item.width() - item.height());
    }

    /** 精确比较 shortSide / longSide，避免浮点舍入改变稳定顺序。 */
    private static int compareSideRatio(Item left, Item right) {
        long lhs = (long) shortSide(left) * longSide(right);
        long rhs = (long) shortSide(right) * longSide(left);
        return Long.compare(lhs, rhs);
    }
}
