package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.AntColonyConfig;
import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.List;

public final class SkylineAntColonyTest {
    public static void main(String[] args) throws Exception {
        returnsLegalHistoricalBestWithMonotoneTrace();
        fixedSeedIsExactlyReproducible();
        respectsEvaluationBudget();
        factoryCreatesIntegratedSolver();
        handlesSingleItem();
        validatesConfiguration();
        System.out.println("SkylineAntColonyTest: 全部通过");
    }

    private static void returnsLegalHistoricalBestWithMonotoneTrace() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        Solution baseline = new SkylineSolver(true, ItemOrder.AREA_DESC).solve(instance);
        AntColonySolver solver = new AntColonySolver(
                true, ItemOrder.AREA_DESC,
                new AntColonyConfig(18, 35, 1.0, 2.0, 0.20, 1.0, 3, 15, 20261013L));

        Solution solution = solver.solve(instance);
        check(solution.packedArea() >= baseline.packedArea(), "蚁群结果不能丢失初始Skyline解");
        check(new SolutionValidator().validate(instance, solution, true).valid(), "蚁群布局必须合法");
        check(solver.lastStatistics().constructedPermutations() > 0, "必须实际构造蚂蚁排列");
        check(solver.lastStatistics().pheromoneDeposits() > 1, "必须实际完成信息素增强");
        check(solver.lastStatistics().fitnessEvaluations() > 1, "必须实际评价候选排列");

        long previous = -1;
        for (ConvergencePoint point : solver.lastConvergenceTrace()) {
            check(point.bestPackedArea() >= previous, "历史最好收敛轨迹必须单调不下降");
            previous = point.bestPackedArea();
        }
        check(previous == solution.packedArea(), "收敛轨迹终点必须等于最终历史最好解");
    }

    private static void fixedSeedIsExactlyReproducible() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        AntColonyConfig config = new AntColonyConfig(
                15, 25, 1.2, 2.5, 0.25, 1.0, 3, 12, 987654321L);
        AntColonySolver first = new AntColonySolver(
                true, ItemOrder.AREA_DESC, config);
        AntColonySolver second = new AntColonySolver(
                true, ItemOrder.AREA_DESC, config);

        Solution firstSolution = first.solve(instance);
        Solution secondSolution = second.solve(instance);
        check(firstSolution.packedArea() == secondSolution.packedArea(), "固定种子应复现相同面积");
        check(firstSolution.placements().equals(secondSolution.placements()), "固定种子应复现相同布局");
        check(first.lastStatistics().equals(second.lastStatistics()), "固定种子应复现相同统计");
        check(first.lastConvergenceTrace().equals(second.lastConvergenceTrace()),
                "固定种子应复现相同收敛轨迹");
    }

    private static void respectsEvaluationBudget() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        AntColonySolver solver = new AntColonySolver(
                true, ItemOrder.AREA_DESC,
                new AntColonyConfig(20, 200, 1.0, 2.0, 0.20, 1.0, 4, 200, 7L),
                SearchLimit.evaluations(40));

        Solution solution = solver.solve(instance);
        check(solver.lastStatistics().fitnessEvaluations() <= 40, "不得越过候选解评价预算");
        check(new SolutionValidator().validate(instance, solution, true).valid(), "预算终止后仍须返回合法解");
        check(solver.lastConvergenceTrace().stream().allMatch(point -> point.evaluations() <= 40),
                "收敛轨迹不得越过评价预算");
    }

    private static void factoryCreatesIntegratedSolver() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut1.txt"));
        var algorithm = SolverFactory.create(
                AlgorithmId.ACO, true, ItemOrder.AREA_DESC, 33L,
                ExperimentProfile.QUICK, SearchLimit.evaluations(30));
        check(algorithm instanceof AntColonySolver, "统一工厂的ACO标识必须创建蚁群求解器");
        Solution solution = algorithm.solve(instance);
        check(new SolutionValidator().validate(instance, solution, true).valid(),
                "工厂创建的蚁群求解器必须输出合法布局");
    }

    private static void handlesSingleItem() {
        Instance instance = new Instance("one", 10, 10, List.of(new Item(1, 4, 7)));
        AntColonySolver solver = new AntColonySolver(
                true, ItemOrder.INPUT, AntColonyConfig.defaults(1L));
        Solution solution = solver.solve(instance);
        check(solution.packedArea() == 28, "单零件实例应正常放入");
        check(solver.lastStatistics().constructedPermutations() == 0, "单零件不需要构造蚂蚁排列");
        check(solver.lastConvergenceTrace().size() == 1, "单零件仍应保留一个收敛起点");
    }

    private static void validatesConfiguration() {
        expectFailure(() -> new AntColonyConfig(0, 10, 1, 2, .2, 1, 1, 5, 1));
        expectFailure(() -> new AntColonyConfig(10, 0, 1, 2, .2, 1, 1, 5, 1));
        expectFailure(() -> new AntColonyConfig(10, 10, -1, 2, .2, 1, 1, 5, 1));
        expectFailure(() -> new AntColonyConfig(10, 10, 1, 2, 0, 1, 1, 5, 1));
        expectFailure(() -> new AntColonyConfig(10, 10, 1, 2, 1, 1, 1, 5, 1));
        expectFailure(() -> new AntColonyConfig(10, 10, 1, 2, .2, 0, 1, 5, 1));
        expectFailure(() -> new AntColonyConfig(10, 10, 1, 2, .2, 1, 11, 5, 1));
        expectFailure(() -> new AntColonyConfig(10, 10, 1, 2, .2, 1, 1, 0, 1));
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("非法蚁群参数必须被拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期结果。
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
