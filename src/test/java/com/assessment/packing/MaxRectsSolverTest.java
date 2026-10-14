package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.MaxRectsBestSolver;
import com.assessment.packing.solver.MaxRectsBestStatistics;
import com.assessment.packing.solver.MaxRectsHeuristic;
import com.assessment.packing.solver.MaxRectsSolver;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MaxRectsSolverTest {
    public static void main(String[] args) throws Exception {
        usesRotationWhenNeeded();
        skipsImpossibleItemAndContinues();
        batchSelectionComparesAllRemainingItems();
        bestVariantMatchesBestIndividualHeuristic();
        everyHeuristicReturnsLegalLayout();
        randomizedInstancesRemainLegal();
        factoryRegistersAllMaxRectsVariants();
        System.out.println("MaxRectsSolverTest: 全部通过");
    }

    private static void usesRotationWhenNeeded() {
        Instance instance = new Instance("rotation", 4, 6, List.of(new Item(1, 6, 4)));
        Solution without = new MaxRectsSolver(
                false, ItemOrder.INPUT, MaxRectsHeuristic.BSSF).solve(instance);
        Solution with = new MaxRectsSolver(
                true, ItemOrder.INPUT, MaxRectsHeuristic.BSSF).solve(instance);

        check(without.placements().isEmpty(), "禁止旋转时不应放入");
        check(with.placements().size() == 1 && with.placements().get(0).rotated(),
                "允许旋转时应旋转后放入");
    }

    private static void skipsImpossibleItemAndContinues() {
        Instance instance = new Instance("skip", 5, 5, List.of(
                new Item(1, 6, 6), new Item(2, 5, 5)));
        Solution solution = new MaxRectsSolver(
                false, ItemOrder.INPUT, MaxRectsHeuristic.BAF).solve(instance);
        check(solution.packedArea() == 25, "不可放零件不应阻止后续可行零件");
    }

    private static void batchSelectionComparesAllRemainingItems() {
        Instance instance = new Instance("global-selection", 10, 10, List.of(
                new Item(1, 6, 6),
                new Item(2, 10, 2)));

        Solution bssf = new MaxRectsSolver(
                false, ItemOrder.INPUT, MaxRectsHeuristic.BSSF).solve(instance);
        Solution baf = new MaxRectsSolver(
                false, ItemOrder.INPUT, MaxRectsHeuristic.BAF).solve(instance);

        check(bssf.placements().get(0).item().id() == 2,
                "BSSF应在全部未放零件中先选择短边剩余为0的零件2");
        check(baf.placements().get(0).item().id() == 1,
                "BAF应在全部未放零件中先选择面积剩余更小的零件1");
    }

    private static void everyHeuristicReturnsLegalLayout() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        SolutionValidator validator = new SolutionValidator();
        for (MaxRectsHeuristic heuristic : MaxRectsHeuristic.values()) {
            Solution solution = new MaxRectsSolver(
                    true, ItemOrder.AREA_DESC, heuristic).solve(instance);
            check(!solution.placements().isEmpty(), heuristic + "应产生非空布局");
            check(validator.validate(instance, solution, true).valid(),
                    heuristic + "布局必须通过独立合法性验证");
        }
    }

    private static void bestVariantMatchesBestIndividualHeuristic() throws Exception {
        InstanceReader reader = new InstanceReader();
        SolutionValidator validator = new SolutionValidator();
        for (String file : List.of("gcut3.txt", "gcut5.txt", "gcut11.txt")) {
            Instance instance = reader.read(Path.of("gcut01", file));
            long expected = 0;
            for (MaxRectsHeuristic heuristic : MaxRectsHeuristic.values()) {
                Solution solution = new MaxRectsSolver(
                        true, ItemOrder.AREA_DESC, heuristic).solve(instance);
                expected = Math.max(expected, solution.packedArea());
            }

            Solution best = new MaxRectsBestSolver(
                    true, ItemOrder.AREA_DESC).solve(instance);
            check(best.packedArea() == expected,
                    file + "的BEST结果必须等于五种启发式中的最大装入面积");
            check(validator.validate(instance, best, true).valid(),
                    file + "的BEST布局必须通过独立合法性验证");
        }

        MaxRectsBestSolver solver = new MaxRectsBestSolver(true, ItemOrder.AREA_DESC);
        Solution solution = solver.solve(reader.read(Path.of("gcut01/gcut3.txt")));
        MaxRectsBestStatistics statistics = solver.lastStatistics();
        check(statistics.entries().size() == MaxRectsHeuristic.values().length,
                "BEST统计必须包含全部五种成员启发式");
        check(statistics.bestPackedArea() == solution.packedArea(),
                "BEST统计中的最大面积必须等于返回布局面积");
        check(statistics.tiedBestHeuristics().contains(statistics.selectedHeuristic()),
                "稳定选择的启发式必须属于并列最优集合");
    }

    private static void randomizedInstancesRemainLegal() {
        Random random = new Random(20261013L);
        SolutionValidator validator = new SolutionValidator();
        for (int sample = 0; sample < 100; sample++) {
            int binWidth = 5 + random.nextInt(26);
            int binHeight = 5 + random.nextInt(26);
            int itemCount = 1 + random.nextInt(20);
            ArrayList<Item> items = new ArrayList<>();
            for (int id = 1; id <= itemCount; id++) {
                items.add(new Item(id, 1 + random.nextInt(35), 1 + random.nextInt(35)));
            }
            Instance instance = new Instance(
                    "random-" + sample, binWidth, binHeight, items);

            for (boolean rotationAllowed : List.of(false, true)) {
                for (MaxRectsHeuristic heuristic : MaxRectsHeuristic.values()) {
                    Solution solution = new MaxRectsSolver(
                            rotationAllowed, ItemOrder.AREA_DESC, heuristic).solve(instance);
                    check(validator.validate(instance, solution, rotationAllowed).valid(),
                            instance.name() + " / " + heuristic + "产生了非法布局");
                }
                Solution best = new MaxRectsBestSolver(
                        rotationAllowed, ItemOrder.AREA_DESC).solve(instance);
                check(validator.validate(instance, best, rotationAllowed).valid(),
                        instance.name() + " / BEST产生了非法布局");
            }
        }
    }

    private static void factoryRegistersAllMaxRectsVariants() {
        List<AlgorithmId> ids = SolverFactory.maxRectsAlgorithms();
        check(SolverFactory.allAlgorithms().containsAll(ids),
                "统一实验算法列表必须包含五种MaxRects启发式与BEST组合版");
        Instance instance = new Instance("factory", 4, 4, List.of(new Item(1, 4, 4)));

        for (AlgorithmId id : ids) {
            PackingAlgorithm solver = SolverFactory.create(
                    id, false, ItemOrder.INPUT, 1L,
                    ExperimentProfile.QUICK, SearchLimit.unlimited());
            check(solver instanceof MaxRectsSolver || solver instanceof MaxRectsBestSolver,
                    id + "必须由工厂创建MaxRects求解器");
            check(solver.solve(instance).packedArea() == 16, id + "应能通过工厂正常运行");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
