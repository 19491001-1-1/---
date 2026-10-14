package com.assessment.packing.solver;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GuillotineSolverTest {
    public static void main(String[] args) throws Exception {
        splitPreservesAreaAndNonOverlap();
        verifiesAllSixSplitRules();
        mergesOnlyFullSharedEdges();
        worstFitStillPrefersPerfectMatch();
        freeSpaceInvariantSurvivesPlacements();
        allConfigurationsReturnLegalLayouts();
        randomizedConfigurationsRemainLegal();
        factoryRegistersDefaultVariant();
        System.out.println("GuillotineSolverTest: 全部通过");
    }

    private static void splitPreservesAreaAndNonOverlap() {
        GuillotineFreeSpace.FreeRectangle free =
                new GuillotineFreeSpace.FreeRectangle(0, 0, 10, 8);
        for (boolean horizontal : List.of(false, true)) {
            List<GuillotineFreeSpace.FreeRectangle> children =
                    GuillotineFreeSpace.split(free, 4, 3, horizontal);
            check(children.size() == 2, "非贴边放置应产生两个自由矩形");
            check(!GuillotineFreeSpace.overlaps(children.get(0), children.get(1)),
                    "两个Guillotine子矩形必须互不重叠");
            long remainingArea = children.stream()
                    .mapToLong(GuillotineFreeSpace.FreeRectangle::area).sum();
            check(remainingArea + 12 == free.area(), "分割前后面积必须守恒");
        }
    }

    private static void verifiesAllSixSplitRules() {
        GuillotineFreeSpace.FreeRectangle free =
                new GuillotineFreeSpace.FreeRectangle(0, 0, 10, 8);
        check(!horizontal(GuillotineSplitRule.SAS, free), "短轴为高度时SAS应垂直切");
        check(horizontal(GuillotineSplitRule.LAS, free), "LAS应与SAS相反");
        check(!horizontal(GuillotineSplitRule.SLAS, free), "较短剩余轴为高度时SLAS应垂直切");
        check(horizontal(GuillotineSplitRule.LLAS, free), "LLAS应与SLAS相反");
        check(!horizontal(GuillotineSplitRule.MAXAS, free),
                "MAXAS应把右上区域接到面积较小的右下区域");
        check(horizontal(GuillotineSplitRule.MINAS, free),
                "MINAS应把右上区域接到面积较大的左上区域");
    }

    private static boolean horizontal(
            GuillotineSplitRule rule, GuillotineFreeSpace.FreeRectangle free) {
        return GuillotineFreeSpace.splitHorizontally(rule, free, 4, 3);
    }

    private static void mergesOnlyFullSharedEdges() {
        GuillotineFreeSpace.FreeRectangle left =
                new GuillotineFreeSpace.FreeRectangle(0, 0, 3, 4);
        GuillotineFreeSpace.FreeRectangle right =
                new GuillotineFreeSpace.FreeRectangle(3, 0, 5, 4);
        GuillotineFreeSpace.FreeRectangle merged = GuillotineFreeSpace.merge(left, right);
        check(merged != null && merged.equals(
                        new GuillotineFreeSpace.FreeRectangle(0, 0, 8, 4)),
                "共享完整竖边且高度相同的矩形应合并");
        check(GuillotineFreeSpace.merge(
                        left, new GuillotineFreeSpace.FreeRectangle(3, 1, 5, 3)) == null,
                "只共享部分边界的矩形不能合并成矩形");
    }

    private static void worstFitStillPrefersPerfectMatch() {
        GuillotineFreeSpace freeSpace = new GuillotineFreeSpace(
                GuillotineRectChoice.WAF, GuillotineSplitRule.SAS, false);
        freeSpace.addFreeRectangle(new GuillotineFreeSpace.FreeRectangle(0, 0, 2, 2));
        freeSpace.addFreeRectangle(new GuillotineFreeSpace.FreeRectangle(5, 0, 10, 10));
        GuillotineFreeSpace.Selection selection = freeSpace.findBest(
                List.of(new Item(1, 2, 2)), false);
        check(selection != null && selection.x() == 0 && selection.y() == 0,
                "最差适配遇到完全匹配也必须立即优先选择");
    }

    private static void freeSpaceInvariantSurvivesPlacements() {
        GuillotineFreeSpace freeSpace = new GuillotineFreeSpace(
                GuillotineRectChoice.BSSF, GuillotineSplitRule.MAXAS, true);
        freeSpace.addFreeRectangle(new GuillotineFreeSpace.FreeRectangle(0, 0, 12, 10));
        List<Item> items = new ArrayList<>(List.of(
                new Item(1, 5, 4), new Item(2, 4, 3),
                new Item(3, 3, 3), new Item(4, 2, 6)));
        long placedArea = 0;
        while (true) {
            GuillotineFreeSpace.Selection selection = freeSpace.findBest(items, true);
            if (selection == null) break;
            Item item = items.remove(selection.itemIndex());
            placedArea += item.area();
            freeSpace.place(selection);
            assertPairwiseDisjoint(freeSpace.freeRectangles());
            long freeArea = freeSpace.freeRectangles().stream()
                    .mapToLong(GuillotineFreeSpace.FreeRectangle::area).sum();
            check(freeArea + placedArea == 120, "每次放置后已用面积与自由面积之和应守恒");
        }
    }

    private static void assertPairwiseDisjoint(
            List<GuillotineFreeSpace.FreeRectangle> rectangles) {
        for (int i = 0; i < rectangles.size(); i++) {
            for (int j = i + 1; j < rectangles.size(); j++) {
                check(!GuillotineFreeSpace.overlaps(rectangles.get(i), rectangles.get(j)),
                        "Guillotine自由矩形必须两两不重叠");
            }
        }
    }

    private static void allConfigurationsReturnLegalLayouts() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        SolutionValidator validator = new SolutionValidator();
        for (GuillotineRectChoice choice : GuillotineRectChoice.values()) {
            for (GuillotineSplitRule split : GuillotineSplitRule.values()) {
                for (boolean merge : List.of(false, true)) {
                    Solution solution = new GuillotineSolver(
                            true, ItemOrder.AREA_DESC, choice, split, merge).solve(instance);
                    check(validator.validate(instance, solution, true).valid(),
                            choice + "/" + split + "/merge=" + merge + "必须合法");
                }
            }
        }
    }

    private static void randomizedConfigurationsRemainLegal() {
        Random random = new Random(20261014L);
        SolutionValidator validator = new SolutionValidator();
        GuillotineRectChoice[] choices = GuillotineRectChoice.values();
        GuillotineSplitRule[] splits = GuillotineSplitRule.values();
        for (int sample = 0; sample < 100; sample++) {
            int binWidth = 5 + random.nextInt(26);
            int binHeight = 5 + random.nextInt(26);
            int itemCount = 1 + random.nextInt(20);
            List<Item> items = new ArrayList<>();
            for (int id = 1; id <= itemCount; id++) {
                items.add(new Item(id, 1 + random.nextInt(35), 1 + random.nextInt(35)));
            }
            Instance instance = new Instance("random-" + sample, binWidth, binHeight, items);
            boolean rotationAllowed = random.nextBoolean();
            GuillotineSolver solver = new GuillotineSolver(
                    rotationAllowed, ItemOrder.AREA_DESC,
                    choices[sample % choices.length], splits[sample % splits.length],
                    sample % 2 == 0);
            Solution solution = solver.solve(instance);
            check(validator.validate(instance, solution, rotationAllowed).valid(),
                    instance.name() + "产生了非法Guillotine布局");
        }
    }

    private static void factoryRegistersDefaultVariant() {
        PackingAlgorithm solver = SolverFactory.create(
                AlgorithmId.GUILLOTINE, true, ItemOrder.AREA_DESC, 1L,
                ExperimentProfile.QUICK, SearchLimit.unlimited());
        check(solver instanceof GuillotineSolver, "工厂必须创建Guillotine求解器");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
