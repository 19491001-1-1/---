package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticMutationMode;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;

public final class SkylineAdaptiveGeneticAlgorithmTest {
    public static void main(String[] args) throws Exception {
        adaptsRatesAndRespectsBudget();
        System.out.println("SkylineAdaptiveGeneticAlgorithmTest: 全部通过");
    }

    private static void adaptsRatesAndRespectsBudget() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        GeneticAlgorithmConfig ga = new GeneticAlgorithmConfig(
                16, 20, 3, 0.9, 0.2, 2, 10, 8, GeneticMutationMode.MIXED, 19L);
        AdaptiveGeneticAlgorithmConfig adaptive =
                new AdaptiveGeneticAlgorithmConfig(0.55, 0.95, 0.05, 0.50);
        AdaptiveGeneticAlgorithmSolver solver =
                new AdaptiveGeneticAlgorithmSolver(
                        true, ItemOrder.AREA_DESC, ga, adaptive, SearchLimit.evaluations(100));
        Solution solution = solver.solve(instance);
        var statistics = solver.lastAdaptiveStatistics();

        check(solver.adaptiveEnabled(), "必须启用自适应模式");
        check(statistics.crossoverRateSamples() > 0, "必须实际计算自适应交叉率");
        check(statistics.mutationRateSamples() > 0, "必须实际计算自适应变异率");
        check(statistics.minimumObservedCrossoverRate() >= 0.55 - 1.0e-12
                        && statistics.maximumObservedCrossoverRate() <= 0.95 + 1.0e-12,
                "交叉率必须处于配置边界");
        check(statistics.minimumObservedMutationRate() >= 0.05 - 1.0e-12
                        && statistics.maximumObservedMutationRate() <= 0.50 + 1.0e-12,
                "变异率必须处于配置边界");
        check(solver.lastStatistics().fitnessEvaluations() <= 100, "不得超过评价预算");
        check(new SolutionValidator().validate(instance, solution, true).valid(),
                "自适应GA布局必须合法");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
