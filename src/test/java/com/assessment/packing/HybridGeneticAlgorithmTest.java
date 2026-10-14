package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.GeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticMutationMode;
import com.assessment.packing.solver.HybridGeneticAlgorithmConfig;
import com.assessment.packing.solver.HybridLocalSearchMode;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.List;

public final class HybridGeneticAlgorithmTest {
    public static void main(String[] args) throws Exception {
        bothModesRunLocalSearchAndReturnValidHistoricalBest();
        annealingActuallyAcceptsWorseMoves();
        tabuActuallyImprovesLocalIndividuals();
        fixedSeedIsReproducible();
        System.out.println("HybridGeneticAlgorithmTest: 全部通过");
    }

    private static void bothModesRunLocalSearchAndReturnValidHistoricalBest() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        Solution baseline = new SkylineSolver(true, ItemOrder.AREA_DESC).solve(instance);
        for (HybridLocalSearchMode mode : HybridLocalSearchMode.values()) {
            GeneticAlgorithmSolver solver = solver(mode, 123L);
            Solution solution = solver.solve(instance);
            check(solution.packedArea() >= baseline.packedArea(), mode + "不能丢失启发式初始解");
            check(new SolutionValidator().validate(instance, solution, true).valid(), mode + "结果必须合法");
            check(solver.lastHybridStatistics().localSearchCalls() > 0, mode + "必须调用局部搜索");
            check(solver.lastHybridStatistics().localCandidateEvaluations() > 0, mode + "必须评价局部邻居");
            check(monotone(solver.lastBestAreaHistory()), mode + "历史最好曲线必须单调不下降");
        }
    }

    private static void annealingActuallyAcceptsWorseMoves() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        GeneticAlgorithmSolver solver = solver(HybridLocalSearchMode.ANNEALING, 123L);
        solver.solve(instance);
        check(solver.lastHybridStatistics().acceptedWorseMoves() > 0,
                "GA+SA局部搜索必须实际按概率接受劣解");
    }

    private static void tabuActuallyImprovesLocalIndividuals() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        GeneticAlgorithmSolver solver = solver(HybridLocalSearchMode.TABU_MOVE, 123L);
        solver.solve(instance);
        check(solver.lastHybridStatistics().localImprovements() > 0,
                "GA+Tabu应在确定性测试中产生严格局部改进");
    }

    private static void fixedSeedIsReproducible() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        GeneticAlgorithmSolver first = solver(HybridLocalSearchMode.ANNEALING, 777L);
        GeneticAlgorithmSolver second = solver(HybridLocalSearchMode.ANNEALING, 777L);
        Solution firstSolution = first.solve(instance);
        Solution secondSolution = second.solve(instance);
        check(firstSolution.placements().equals(secondSolution.placements()), "相同种子应复现混合算法布局");
        check(first.lastStatistics().equals(second.lastStatistics()), "相同种子应复现GA统计");
        check(first.lastHybridStatistics().equals(second.lastHybridStatistics()), "相同种子应复现局部统计");
        check(first.lastBestAreaHistory().equals(second.lastBestAreaHistory()), "相同种子应复现收敛过程");
    }

    private static GeneticAlgorithmSolver solver(HybridLocalSearchMode mode, long seed) {
        GeneticAlgorithmConfig genetic = new GeneticAlgorithmConfig(
                40, 30, 3, 0.9, 0.2, 2, 15, 10,
                GeneticMutationMode.MIXED, seed);
        HybridGeneticAlgorithmConfig hybrid = new HybridGeneticAlgorithmConfig(
                5, 0.10, 4, 100, 4, 10, 0.90, 0.30, 5);
        return new GeneticAlgorithmSolver(
                true, ItemOrder.AREA_DESC, genetic, mode, hybrid);
    }

    private static boolean monotone(List<Long> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) < values.get(i - 1)) return false;
        }
        return true;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
