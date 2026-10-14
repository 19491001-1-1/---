package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SimulatedAnnealingConfig;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.List;

public final class SkylineSimulatedAnnealingTest {
    public static void main(String[] args) throws Exception {
        returnsValidHistoricalBestAndAcceptsWorseMoves();
        fixedSeedIsReproducible();
        handlesSingleItem();
        System.out.println("SkylineSimulatedAnnealingTest: 全部通过");
    }

    private static void returnsValidHistoricalBestAndAcceptsWorseMoves() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        Solution baseline = new SkylineSolver(true, ItemOrder.AREA_DESC).solve(instance);
        SimulatedAnnealingSolver solver = new SimulatedAnnealingSolver(
                true,
                ItemOrder.AREA_DESC,
                new SimulatedAnnealingConfig(80, 80, 0.97, 1.0e-5, 25, 0.8, 100, 12345L));

        Solution solution = solver.solve(instance);
        check(solution.packedArea() >= baseline.packedArea(), "最终结果不能丢失初始历史最好解");
        check(new SolutionValidator().validate(instance, solution, true).valid(), "模拟退火结果必须合法");
        check(solver.lastStatistics().searchNeighborEvaluations() > 0, "必须实际评价搜索邻居");
        check(solver.lastStatistics().acceptedWorseMoves() > 0, "高温阶段应实际接受劣解");
    }

    private static void fixedSeedIsReproducible() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        SimulatedAnnealingConfig config = new SimulatedAnnealingConfig(
                50, 60, 0.96, 1.0e-5, 20, 0.8, 80, 777L);
        SimulatedAnnealingSolver first = new SimulatedAnnealingSolver(
                true, ItemOrder.AREA_DESC, config);
        SimulatedAnnealingSolver second = new SimulatedAnnealingSolver(
                true, ItemOrder.AREA_DESC, config);

        Solution firstSolution = first.solve(instance);
        Solution secondSolution = second.solve(instance);
        check(firstSolution.packedArea() == secondSolution.packedArea(), "相同随机种子应得到相同面积");
        check(firstSolution.placements().equals(secondSolution.placements()), "相同随机种子应得到相同布局");
        check(first.lastStatistics().equals(second.lastStatistics()), "相同随机种子应得到相同统计");
    }

    private static void handlesSingleItem() {
        Instance instance = new Instance("one", 10, 10, List.of(new Item(1, 5, 5)));
        SimulatedAnnealingSolver solver = new SimulatedAnnealingSolver(
                false, ItemOrder.INPUT, SimulatedAnnealingConfig.defaults(1L));
        check(solver.solve(instance).packedArea() == 25, "单零件实例应正常返回");
        check(solver.lastStatistics().searchNeighborEvaluations() == 0, "单零件不应产生交换邻居");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
