package com.assessment.packing.solver;

import com.assessment.packing.model.Item;

import java.util.List;

public final class ItemOrderingTest {
    public static void main(String[] args) {
        coversAllPdfOrdersAndAliases();
        System.out.println("ItemOrderingTest: 全部通过");
    }

    private static void coversAllPdfOrdersAndAliases() {
        List<Item> items = List.of(
                new Item(1, 2, 8),  // area16, short2, long8, perimeter20, diff6, ratio.25
                new Item(2, 4, 5),  // area20, short4, long5, perimeter18, diff1, ratio.80
                new Item(3, 3, 3)); // area9,  short3, long3, perimeter12, diff0, ratio1

        expect(List.of(items.get(2), items.get(0), items.get(1)), ItemOrder.INPUT, 3, 1, 2);
        expect(items, ItemOrder.AREA_ASC, 3, 1, 2);
        expect(items, ItemOrder.AREA_DESC, 2, 1, 3);
        expect(items, ItemOrder.SHORT_SIDE_ASC, 1, 3, 2);
        expect(items, ItemOrder.SHORT_SIDE_DESC, 2, 3, 1);
        expect(items, ItemOrder.LONG_SIDE_ASC, 3, 2, 1);
        expect(items, ItemOrder.LONG_SIDE_DESC, 1, 2, 3);
        expect(items, ItemOrder.PERIMETER_ASC, 3, 2, 1);
        expect(items, ItemOrder.PERIMETER_DESC, 1, 2, 3);
        expect(items, ItemOrder.SIDE_DIFFERENCE_ASC, 3, 2, 1);
        expect(items, ItemOrder.SIDE_DIFFERENCE_DESC, 1, 2, 3);
        expect(items, ItemOrder.SIDE_RATIO_ASC, 1, 2, 3);
        expect(items, ItemOrder.SIDE_RATIO_DESC, 3, 2, 1);

        check(ItemOrder.parse("AscA") == ItemOrder.AREA_ASC, "AscA别名应可解析");
        check(ItemOrder.parse("DescRatio") == ItemOrder.SIDE_RATIO_DESC,
                "DescRatio别名应可解析");
    }

    private static void expect(List<Item> source, ItemOrder order, int... ids) {
        List<Item> sorted = ItemOrdering.sort(source, order);
        for (int i = 0; i < ids.length; i++) {
            check(sorted.get(i).id() == ids[i], order + "第" + i + "项不正确");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
