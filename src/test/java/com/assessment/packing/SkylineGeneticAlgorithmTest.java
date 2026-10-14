package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.GeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticMutationMode;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.List;

public final class SkylineGeneticAlgorithmTest {
    public static void main(String[] args) throws Exception {
        returnsValidHistoricalBestAndMonotoneHistory();
        fixedSeedIsReproducible();
        handlesTinyPermutationSpaces();
        System.out.println("SkylineGeneticAlgorithmTest: 全部通过");
    }

    private static void returnsValidHistoricalBestAndMonotoneHistory() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        Solution baseline = new SkylineSolver(true, ItemOrder.AREA_DESC).solve(instance);
        GeneticAlgorithmSolver solver = new GeneticAlgorithmSolver(
                true,
                ItemOrder.AREA_DESC,
                new GeneticAlgorithmConfig(
                        60, 80, 3, 0.9, 0.25, 3, 25, 15,
                        GeneticMutationMode.MIXED, 12345L));

        Solution solution = solver.solve(instance);
        check(solution.packedArea() >= baseline.packedArea(), "遗传算法不能丢失启发式初始解");
        check(new SolutionValidator().validate(instance, solution, true).valid(), "遗传算法结果必须合法");
        check(solver.lastStatistics().fitnessEvaluations() > 0, "必须实际进行适应度评价");
        List<Long> history = solver.lastBestAreaHistory();
        check(!history.isEmpty(), "必须保存收敛历史");
        for (int i = 1; i < history.size(); i++) {
            check(history.get(i) >= history.get(i - 1), "历史最好面积必须单调不下降");
        }
        check(history.get(history.size() - 1) == solution.packedArea(), "收敛历史终点必须对应最终解");
    }

    private static void fixedSeedIsReproducible() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(
                40, 50, 3, 0.9, 0.2, 2, 20, 10,
                GeneticMutationMode.SWAP, 777L);
        GeneticAlgorithmSolver first = new GeneticAlgorithmSolver(
                true, ItemOrder.AREA_DESC, config);
        GeneticAlgorithmSolver second = new GeneticAlgorithmSolver(
                true, ItemOrder.AREA_DESC, config);

        Solution firstSolution = first.solve(instance);
        Solution secondSolution = second.solve(instance);
        check(firstSolution.packedArea() == secondSolution.packedArea(), "相同随机种子应得到相同面积");
        check(firstSolution.placements().equals(secondSolution.placements()), "相同随机种子应得到相同布局");
        check(first.lastStatistics().equals(second.lastStatistics()), "相同随机种子应得到相同统计");
        check(first.lastBestAreaHistory().equals(second.lastBestAreaHistory()), "相同随机种子应得到相同收敛过程");
    }

    private static void handlesTinyPermutationSpaces() {
        GeneticAlgorithmConfig config = new GeneticAlgorithmConfig(
                100, 10, 3, 0.9, 0.2, 5, 5, 10,
                GeneticMutationMode.SWAP, 1L);

        Instance one = new Instance("one", 10, 10, List.of(new Item(1, 5, 5)));
        GeneticAlgorithmSolver oneSolver = new GeneticAlgorithmSolver(
                false, ItemOrder.INPUT, config);
        check(oneSolver.solve(one).packedArea() == 25, "单零件实例应正常返回");

        Instance two = new Instance("two", 10, 10, List.of(
                new Item(1, 6, 10), new Item(2, 4, 10)));
        GeneticAlgorithmSolver twoSolver = new GeneticAlgorithmSolver(
                false, ItemOrder.INPUT, config);
        check(twoSolver.solve(two).packedArea() == 100, "双零件实例应正常搜索完整排列空间");
        check(twoSolver.lastStatistics().effectivePopulationSize() == 2,
                "两个零件最多只有两个不同排列");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
