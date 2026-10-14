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

public final class SkylineGlobalSolverTest {
    public static void main(String[] args) throws Exception {
        computesCandidateHeightAndLostArea();
        fillsPerfectRectangle();
        usesRotationWhenNeeded();
        wasteMapReusesASealedHole();
        realInstancesRemainLegal();
        randomizedInstancesRemainLegal();
        factoryRegistersPdfVariants();
        System.out.println("SkylineGlobalSolverTest: 全部通过");
    }

    private static void computesCandidateHeightAndLostArea() {
        List<SkylineGlobalSolver.SkylineLevel> skyline = List.of(
                new SkylineGlobalSolver.SkylineLevel(0, 4, 3),
                new SkylineGlobalSolver.SkylineLevel(3, 1, 4),
                new SkylineGlobalSolver.SkylineLevel(7, 3, 3));

        SkylineGlobalSolver.Fit fit = SkylineGlobalSolver.fitAt(
                skyline, 0, 7, 2, 10, 10);
        check(fit != null, "横跨前两段的矩形应该可放");
        check(fit.x() == 0 && fit.y() == 4 && fit.top() == 6,
                "候选高度应由横跨范围内最高天际线决定");
        check(fit.wastedArea() == 12,
                "损失面积应为低线段上方3×4的凹槽面积");
        check(SkylineGlobalSolver.fitAt(skyline, 1, 8, 2, 10, 10) == null,
                "超过板材右边界的候选必须拒绝");
    }

    private static void fillsPerfectRectangle() {
        Instance instance = new Instance("perfect", 10, 10, List.of(
                new Item(1, 5, 10), new Item(2, 5, 10)));
        for (SkylinePlacementHeuristic heuristic : SkylinePlacementHeuristic.values()) {
            Solution solution = new SkylineGlobalSolver(
                    false, ItemOrder.INPUT, heuristic).solve(instance);
            check(solution.packedArea() == 100, heuristic + "应达到100%利用率");
            check(new SolutionValidator().validate(instance, solution, false).valid(),
                    heuristic + "满箱方案必须合法");
        }
    }

    private static void usesRotationWhenNeeded() {
        Instance instance = new Instance("rotation", 4, 6, List.of(new Item(1, 6, 4)));
        for (SkylinePlacementHeuristic heuristic : SkylinePlacementHeuristic.values()) {
            Solution solution = new SkylineGlobalSolver(
                    true, ItemOrder.INPUT, heuristic).solve(instance);
            check(solution.placements().size() == 1
                            && solution.placements().get(0).rotated(),
                    heuristic + "应旋转后放入");
        }
    }

    private static void wasteMapReusesASealedHole() {
        Instance instance = new Instance("waste-map", 6, 6, List.of(
                new Item(1, 3, 2),
                new Item(2, 3, 4),
                new Item(3, 6, 1),
                new Item(4, 3, 2)));
        for (SkylinePlacementHeuristic heuristic : SkylinePlacementHeuristic.values()) {
            Solution without = new SkylineGlobalSolver(
                    false, ItemOrder.INPUT, heuristic, false,
                    SkylineSelectionMode.SEQUENTIAL).solve(instance);
            Solution with = new SkylineGlobalSolver(
                    false, ItemOrder.INPUT, heuristic, true,
                    SkylineSelectionMode.SEQUENTIAL).solve(instance);
            check(without.packedArea() == 24,
                    heuristic + "不使用WM时最后一个3×2零件应无法放入");
            check(with.packedArea() == 30,
                    heuristic + "使用WM时应回收被6×1零件封住的3×2洞");
            check(new SolutionValidator().validate(instance, with, false).valid(),
                    heuristic + "的WM回收布局必须合法");
        }
    }

    private static void realInstancesRemainLegal() throws Exception {
        InstanceReader reader = new InstanceReader();
        SolutionValidator validator = new SolutionValidator();
        for (String file : List.of("gcut1.txt", "gcut3.txt", "gcut9.txt", "gcut12.txt")) {
            Instance instance = reader.read(Path.of("gcut01", file));
            for (SkylinePlacementHeuristic heuristic : SkylinePlacementHeuristic.values()) {
                Solution solution = new SkylineGlobalSolver(
                        true, ItemOrder.AREA_DESC, heuristic).solve(instance);
                check(validator.validate(instance, solution, true).valid(),
                        file + " / " + heuristic + "必须通过独立合法性验证");
            }
        }
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
            Instance instance = new Instance("random-" + sample, binWidth, binHeight, items);
            for (boolean rotationAllowed : List.of(false, true)) {
                for (SkylinePlacementHeuristic heuristic : SkylinePlacementHeuristic.values()) {
                    Solution solution = new SkylineGlobalSolver(
                            rotationAllowed, ItemOrder.AREA_DESC, heuristic).solve(instance);
                    check(validator.validate(instance, solution, rotationAllowed).valid(),
                            instance.name() + " / " + heuristic + "产生了非法布局");
                }
            }
        }
    }

    private static void factoryRegistersPdfVariants() {
        check(SolverFactory.skylineAlgorithms().equals(List.of(
                        AlgorithmId.SKYLINE,
                        AlgorithmId.SKYLINE_BL,
                        AlgorithmId.SKYLINE_BF,
                        AlgorithmId.SKYLINE_BL_WM,
                        AlgorithmId.SKYLINE_BF_WM)),
                "Skyline工厂列表必须包含堆优化版本及GLOBAL基础版、WM版");
        Instance instance = new Instance("factory", 4, 4, List.of(new Item(1, 4, 4)));
        for (AlgorithmId id : List.of(AlgorithmId.SKYLINE_BL, AlgorithmId.SKYLINE_BF)) {
            PackingAlgorithm solver = SolverFactory.create(
                    id, false, ItemOrder.INPUT, 1L,
                    ExperimentProfile.QUICK, SearchLimit.unlimited());
            check(solver instanceof SkylineGlobalSolver, id + "必须创建GLOBAL Skyline求解器");
            check(solver.solve(instance).packedArea() == 16, id + "应能通过工厂正常运行");
        }
        for (AlgorithmId id : List.of(AlgorithmId.SKYLINE_BL_WM, AlgorithmId.SKYLINE_BF_WM)) {
            PackingAlgorithm solver = SolverFactory.create(
                    id, false, ItemOrder.INPUT, 1L,
                    ExperimentProfile.QUICK, SearchLimit.unlimited());
            check(solver instanceof SkylineGlobalSolver, id + "必须创建Skyline-WM求解器");
            check(solver.solve(instance).packedArea() == 16, id + "应能通过工厂正常运行");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
