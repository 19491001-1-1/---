package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.ShelfHeuristic;
import com.assessment.packing.solver.ShelfSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ShelfSolverTest {
    public static void main(String[] args) {
        packsKnownExample();
        usesRotationWhenNeeded();
        firstFitReusesAClosedShelf();
        bestAndWorstWidthChooseDifferentShelves();
        wasteMapReusesClosedShelfGap();
        allVariantsRemainLegal();
        randomizedVariantsRemainLegal();
        factoryRegistersAllVariants();
        detectsOverlap();
        System.out.println("ShelfSolverTest: 全部通过");
    }

    private static void packsKnownExample() {
        Instance in = new Instance("known", 10, 8, List.of(
                new Item(1, 6, 4), new Item(2, 4, 4), new Item(3, 5, 4)));
        Solution s = new ShelfSolver(false, ItemOrder.HEIGHT_DESC).solve(in);
        check(s.packedArea() == 60, "已知示例面积应为60");
        check(new SolutionValidator().validate(in, s, false).valid(), "已知示例必须合法");
    }

    private static void usesRotationWhenNeeded() {
        Instance in = new Instance("rotation", 4, 6, List.of(new Item(1, 6, 4)));
        Solution without = new ShelfSolver(false, ItemOrder.INPUT).solve(in);
        Solution with = new ShelfSolver(true, ItemOrder.INPUT).solve(in);
        check(without.placements().isEmpty(), "禁止旋转时不应放入");
        check(with.placements().size() == 1 && with.placements().get(0).rotated(), "允许旋转时应旋转放入");
    }

    private static void firstFitReusesAClosedShelf() {
        Instance instance = new Instance("first-fit", 10, 8, List.of(
                new Item(1, 6, 2), new Item(2, 6, 4), new Item(3, 4, 2)));
        Solution nextFit = new ShelfSolver(
                false, ItemOrder.INPUT, ShelfHeuristic.NEXT_FIT).solve(instance);
        Solution firstFit = new ShelfSolver(
                false, ItemOrder.INPUT, ShelfHeuristic.FIRST_FIT).solve(instance);
        check(placement(nextFit, 3).y() == 2, "NF只能继续使用当前开放层");
        check(placement(firstFit, 3).y() == 0, "FF应回填第一个已关闭层的层尾");
    }

    private static void bestAndWorstWidthChooseDifferentShelves() {
        Instance instance = new Instance("width-fit", 10, 8, List.of(
                new Item(1, 7, 2), new Item(2, 4, 4), new Item(3, 2, 2)));
        Solution best = new ShelfSolver(
                false, ItemOrder.INPUT, ShelfHeuristic.BEST_WIDTH_FIT).solve(instance);
        Solution worst = new ShelfSolver(
                false, ItemOrder.INPUT, ShelfHeuristic.WORST_WIDTH_FIT).solve(instance);
        check(placement(best, 3).y() == 0,
                "BWF应选择放置后剩余宽度更小的第一层");
        check(placement(worst, 3).y() == 2,
                "WWF应选择放置后剩余宽度更大的第二层");
    }

    private static void wasteMapReusesClosedShelfGap() {
        Instance instance = new Instance("shelf-waste-map", 6, 6, List.of(
                new Item(1, 3, 2),
                new Item(2, 3, 4),
                new Item(3, 6, 1),
                new Item(4, 3, 2)));
        Solution without = new ShelfSolver(
                false, ItemOrder.INPUT, ShelfHeuristic.NEXT_FIT, false).solve(instance);
        Solution with = new ShelfSolver(
                false, ItemOrder.INPUT, ShelfHeuristic.NEXT_FIT, true).solve(instance);
        check(without.packedArea() == 24, "不用WM时层顶的3×2洞无法再利用");
        check(with.packedArea() == 30, "WM应让第四个零件回填3×2层顶洞");
        check(placement(with, 4).x() == 0 && placement(with, 4).y() == 2,
                "WM回填坐标应与关闭层中的实际洞一致");
    }

    private static void allVariantsRemainLegal() {
        try {
            Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
            SolutionValidator validator = new SolutionValidator();
            for (ShelfHeuristic heuristic : ShelfHeuristic.values()) {
                for (boolean wasteMap : List.of(false, true)) {
                    Solution solution = new ShelfSolver(
                            true, ItemOrder.AREA_DESC, heuristic, wasteMap).solve(instance);
                    check(validator.validate(instance, solution, true).valid(),
                            heuristic + "/WM=" + wasteMap + "必须通过真实数据验证");
                }
            }
        } catch (Exception exception) {
            throw new AssertionError("读取Shelf真实测试数据失败", exception);
        }
    }

    private static void randomizedVariantsRemainLegal() {
        Random random = new Random(20261013L);
        SolutionValidator validator = new SolutionValidator();
        for (int sample = 0; sample < 60; sample++) {
            int binWidth = 5 + random.nextInt(26);
            int binHeight = 5 + random.nextInt(26);
            int itemCount = 1 + random.nextInt(20);
            List<Item> items = new ArrayList<>();
            for (int id = 1; id <= itemCount; id++) {
                items.add(new Item(id, 1 + random.nextInt(35), 1 + random.nextInt(35)));
            }
            Instance instance = new Instance("shelf-random-" + sample,
                    binWidth, binHeight, items);
            boolean rotationAllowed = random.nextBoolean();
            for (ShelfHeuristic heuristic : ShelfHeuristic.values()) {
                for (boolean wasteMap : List.of(false, true)) {
                    Solution solution = new ShelfSolver(
                            rotationAllowed, ItemOrder.AREA_DESC,
                            heuristic, wasteMap).solve(instance);
                    check(validator.validate(instance, solution, rotationAllowed).valid(),
                            instance.name() + "/" + heuristic + "/WM=" + wasteMap
                                    + "产生了非法布局");
                }
            }
        }
    }

    private static void factoryRegistersAllVariants() {
        check(SolverFactory.shelfAlgorithms().size() == 19,
                "Shelf工厂应登记六种基础规则、六个WM、Floor-Ceiling及六个GLOBAL版本");
        Instance instance = new Instance("factory", 4, 4, List.of(new Item(1, 4, 4)));
        for (AlgorithmId id : SolverFactory.shelfAlgorithms()) {
            Solution solution = SolverFactory.create(
                    id, false, ItemOrder.INPUT, 1L,
                    ExperimentProfile.QUICK, SearchLimit.unlimited()).solve(instance);
            check(solution.packedArea() == 16, id + "应能通过工厂正常运行");
        }
    }

    private static Placement placement(Solution solution, int itemId) {
        return solution.placements().stream()
                .filter(candidate -> candidate.item().id() == itemId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未放入零件" + itemId));
    }

    private static void detectsOverlap() {
        Item a = new Item(1, 5, 5);
        Item b = new Item(2, 5, 5);
        Instance in = new Instance("bad", 10, 10, List.of(a, b));
        Solution bad = new Solution("bad", List.of(
                new Placement(a, 0, 0, false), new Placement(b, 4, 4, false)), 0);
        check(!new SolutionValidator().validate(in, bad, false).valid(), "验证器应发现重叠");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
