package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SimulatedAnnealingConfig;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.SimulatedAnnealingStatistics;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.List;

public final class SkylineSimulatedAnnealingTest {
    public static void main(String[] args) throws Exception {
        returnsValidHistoricalBestAndAcceptsWorseMoves();
        fixedSeedIsReproducible();
        goldenPinsExactAreaAndStatistics();
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

    /**
     * 金标准：固定实例/种子的精确面积与统计必须逐位不变。
     *
     * <p>「同种子跑两遍一致」只能证明确定性，抓不住共享缓冲区别名这类**确定性错误**；
     * 这里把精确数值钉死。
     */
    private static void goldenPinsExactAreaAndStatistics() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        SimulatedAnnealingSolver solver = new SimulatedAnnealingSolver(
                true, ItemOrder.AREA_DESC,
                new SimulatedAnnealingConfig(50, 10, 0.95, 0.01, 20, 0.8, 100, 20261012L));
        Solution solution = solver.solve(instance);
        check(solution.packedArea() == 60375L, "金标准面积不符：" + solution.packedArea());

        SimulatedAnnealingStatistics statistics = solver.lastStatistics();
        check(statistics.completedTemperatureLevels() == 28,
                "金标准降温层数不符：" + statistics.completedTemperatureLevels());
        check(statistics.temperatureSampleEvaluations() == 100,
                "金标准温度采样数不符：" + statistics.temperatureSampleEvaluations());
        check(statistics.searchNeighborEvaluations() == 280,
                "金标准邻居评价数不符：" + statistics.searchNeighborEvaluations());
        check(statistics.acceptedImprovingMoves() == 40,
                "金标准接受优解数不符：" + statistics.acceptedImprovingMoves());
        check(statistics.acceptedEqualMoves() == 180,
                "金标准接受等解数不符：" + statistics.acceptedEqualMoves());
        check(statistics.acceptedWorseMoves() == 32,
                "金标准接受劣解数不符：" + statistics.acceptedWorseMoves());
        check(statistics.rejectedMoves() == 28,
                "金标准拒绝步数不符：" + statistics.rejectedMoves());
        check(statistics.bestImprovements() == 6,
                "金标准最好解更新次数不符：" + statistics.bestImprovements());
        // 温度由面积差推导，属浮点计算：钉住数量级与单调性，不做逐位比较。
        check(Math.abs(statistics.initialTemperature() - 0.23067980930322698) < 1.0e-12,
                "金标准初始温度不符：" + statistics.initialTemperature());
        check(Math.abs(statistics.finalTemperature() - 0.05486186053788046) < 1.0e-12,
                "金标准终止温度不符：" + statistics.finalTemperature());
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
