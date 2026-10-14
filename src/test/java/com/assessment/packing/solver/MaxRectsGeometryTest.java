package com.assessment.packing.solver;

import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;

import java.util.List;

public final class MaxRectsGeometryTest {
    public static void main(String[] args) {
        centralPlacementCreatesFourDirectionalSplits();
        splitsEveryIntersectingFreeRectangle();
        edgeTouchDoesNotSplit();
        exhaustiveSplitPreservesExactlyTheFreeCells();
        prunesContainedAndDuplicateRectangles();
        verifiesFiveScoresAndTieRules();
        System.out.println("MaxRectsGeometryTest: 全部通过");
    }

    private static void centralPlacementCreatesFourDirectionalSplits() {
        MaxRectsSolver.FreeRectangle whole = rect(0, 0, 10, 10);
        MaxRectsSolver.FreeRectangle occupied = rect(3, 3, 4, 4);
        List<MaxRectsSolver.FreeRectangle> split = MaxRectsSolver.splitFreeRectangles(
                List.of(whole), occupied);

        check(split.size() == 4, "内部放置应产生上、下、左、右四个候选空闲矩形");
        check(split.contains(rect(0, 0, 3, 10)), "缺少左侧整高矩形");
        check(split.contains(rect(7, 0, 3, 10)), "缺少右侧整高矩形");
        check(split.contains(rect(0, 0, 10, 3)), "缺少下方整宽矩形");
        check(split.contains(rect(0, 7, 10, 3)), "缺少上方整宽矩形");
    }

    private static void splitsEveryIntersectingFreeRectangle() {
        MaxRectsSolver.FreeRectangle horizontal = rect(0, 0, 10, 6);
        MaxRectsSolver.FreeRectangle vertical = rect(0, 0, 6, 10);
        MaxRectsSolver.FreeRectangle unrelated = rect(8, 8, 2, 2);
        MaxRectsSolver.FreeRectangle occupied = rect(2, 2, 4, 4);

        List<MaxRectsSolver.FreeRectangle> result = MaxRectsSolver.splitFreeRectangles(
                List.of(horizontal, vertical, unrelated), occupied);

        check(!result.contains(horizontal) && !result.contains(vertical),
                "两个相交空闲矩形都必须被替换，不能只切割承载项");
        check(result.contains(unrelated), "不相交空闲矩形必须原样保留");
        for (MaxRectsSolver.FreeRectangle free : result) {
            check(!overlaps(free, occupied), "切割结果不能继续包含已占用区域：" + free);
        }
    }

    private static void prunesContainedAndDuplicateRectangles() {
        MaxRectsSolver.FreeRectangle outer = rect(0, 0, 10, 10);
        MaxRectsSolver.FreeRectangle inner = rect(2, 2, 3, 3);
        MaxRectsSolver.FreeRectangle unrelated = rect(20, 20, 2, 2);
        List<MaxRectsSolver.FreeRectangle> pruned = MaxRectsSolver.pruneFreeList(
                List.of(inner, outer, outer, unrelated));

        check(pruned.size() == 2, "包含项与重复项应被删除");
        check(pruned.contains(outer), "应保留外层极大空闲矩形");
        check(pruned.contains(unrelated), "不被包含的矩形必须保留");
    }

    private static void edgeTouchDoesNotSplit() {
        MaxRectsSolver.FreeRectangle free = rect(0, 0, 5, 5);
        MaxRectsSolver.FreeRectangle touching = rect(5, 1, 2, 2);
        List<MaxRectsSolver.FreeRectangle> result = MaxRectsSolver.splitFreeRectangles(
                List.of(free), touching);
        check(result.equals(List.of(free)), "仅边界接触没有面积重叠，不应切割空闲矩形");
    }

    private static void exhaustiveSplitPreservesExactlyTheFreeCells() {
        MaxRectsSolver.FreeRectangle free = rect(1, 1, 5, 4);
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int width = 1; width <= 4; width++) {
                    for (int height = 1; height <= 4; height++) {
                        MaxRectsSolver.FreeRectangle occupied = rect(x, y, width, height);
                        List<MaxRectsSolver.FreeRectangle> result =
                                MaxRectsSolver.splitFreeRectangles(List.of(free), occupied);

                        for (MaxRectsSolver.FreeRectangle remaining : result) {
                            check(!overlaps(remaining, occupied),
                                    "切割结果不得覆盖占用区域：" + remaining + " / " + occupied);
                        }
                        for (int cellX = free.x(); cellX < free.right(); cellX++) {
                            for (int cellY = free.y(); cellY < free.top(); cellY++) {
                                boolean expectedFree = !containsCell(occupied, cellX, cellY);
                                boolean represented = false;
                                for (MaxRectsSolver.FreeRectangle remaining : result) {
                                    if (containsCell(remaining, cellX, cellY)) {
                                        represented = true;
                                        break;
                                    }
                                }
                                check(expectedFree == represented,
                                        "切割前后的自由区域不等价，occupied=" + occupied
                                                + "，cell=(" + cellX + "," + cellY + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void verifiesFiveScoresAndTieRules() {
        MaxRectsSolver.FreeRectangle free = rect(3, 4, 10, 7);
        List<Placement> none = List.of();

        check(score(MaxRectsHeuristic.BSSF, free, 6, 5, none).equals(
                        new MaxRectsSolver.Score(2, 4, 0)),
                "BSSF应按短边剩余2、长边剩余4评分");
        check(score(MaxRectsHeuristic.BLSF, free, 6, 5, none).equals(
                        new MaxRectsSolver.Score(4, 2, 0)),
                "BLSF应按长边剩余4、短边剩余2评分");
        check(score(MaxRectsHeuristic.BAF, free, 6, 5, none).equals(
                        new MaxRectsSolver.Score(40, 2, 4)),
                "BAF应先比较面积剩余40，再按BSSF打破平局");
        check(score(MaxRectsHeuristic.BL, free, 6, 5, none).equals(
                        new MaxRectsSolver.Score(9, 3, 0)),
                "BL应按顶边y+h=9、再按x=3评分");

        Item obstacleItem = new Item(1, 3, 4);
        List<Placement> used = List.of(new Placement(obstacleItem, 0, 0, false));
        MaxRectsSolver.FreeRectangle contactFree = rect(3, 0, 7, 10);
        check(score(MaxRectsHeuristic.CP, contactFree, 4, 4, used).first() == -8,
                "CP应得到底边接触4加相邻矩形接触4，并通过负值转为最小化比较");

        MaxRectsSolver.FreeRectangle shortSideWinner = rect(0, 0, 6, 9);
        MaxRectsSolver.FreeRectangle longSideWinner = rect(0, 0, 7, 7);
        check(score(MaxRectsHeuristic.BSSF, shortSideWinner, 4, 4, none)
                        .compareTo(score(MaxRectsHeuristic.BSSF, longSideWinner, 4, 4, none)) < 0,
                "BSSF必须优先更小的短边剩余");
        check(score(MaxRectsHeuristic.BLSF, longSideWinner, 4, 4, none)
                        .compareTo(score(MaxRectsHeuristic.BLSF, shortSideWinner, 4, 4, none)) < 0,
                "BLSF必须优先更小的长边剩余");

        MaxRectsSolver.FreeRectangle areaTieA = rect(0, 0, 3, 6);
        MaxRectsSolver.FreeRectangle areaTieB = rect(0, 0, 2, 9);
        check(score(MaxRectsHeuristic.BAF, areaTieB, 2, 2, none)
                        .compareTo(score(MaxRectsHeuristic.BAF, areaTieA, 2, 2, none)) < 0,
                "BAF面积相同时必须用BSSF的短边剩余打破平局");

        MaxRectsSolver.FreeRectangle blA = rect(5, 1, 5, 9);
        MaxRectsSolver.FreeRectangle blB = rect(2, 2, 8, 8);
        check(score(MaxRectsHeuristic.BL, blB, 2, 3, none)
                        .compareTo(score(MaxRectsHeuristic.BL, blA, 2, 4, none)) < 0,
                "BL顶边相同时必须选择更小的x");

        MaxRectsSolver.Score lowerContact = new MaxRectsSolver.Score(-6, 0, 0);
        MaxRectsSolver.Score higherContact = new MaxRectsSolver.Score(-8, 0, 0);
        check(higherContact.compareTo(lowerContact) < 0, "CP必须优先更大的接触边长");
    }

    private static MaxRectsSolver.Score score(
            MaxRectsHeuristic heuristic,
            MaxRectsSolver.FreeRectangle free,
            int width,
            int height,
            List<Placement> used) {
        return MaxRectsSolver.score(heuristic, free, width, height, used, 20, 20);
    }

    private static MaxRectsSolver.FreeRectangle rect(int x, int y, int width, int height) {
        return new MaxRectsSolver.FreeRectangle(x, y, width, height);
    }

    private static boolean overlaps(
            MaxRectsSolver.FreeRectangle a,
            MaxRectsSolver.FreeRectangle b) {
        return a.x() < b.right() && b.x() < a.right()
                && a.y() < b.top() && b.y() < a.top();
    }

    private static boolean containsCell(
            MaxRectsSolver.FreeRectangle rectangle, int cellX, int cellY) {
        return cellX >= rectangle.x() && cellX < rectangle.right()
                && cellY >= rectangle.y() && cellY < rectangle.top();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
