package com.assessment.packing.solver;

/**
 * 自适应 GA 概率边界：低于平均适应度者使用较强探索，高于平均者随质量提高而减少扰动。
 */
public record AdaptiveGeneticAlgorithmConfig(
        double minimumCrossoverRate,
        double maximumCrossoverRate,
        double minimumMutationRate,
        double maximumMutationRate) {

    public AdaptiveGeneticAlgorithmConfig {
        checkRate(minimumCrossoverRate, "最小交叉率");
        checkRate(maximumCrossoverRate, "最大交叉率");
        checkRate(minimumMutationRate, "最小变异率");
        checkRate(maximumMutationRate, "最大变异率");
        if (minimumCrossoverRate > maximumCrossoverRate) {
            throw new IllegalArgumentException("最小交叉率不能大于最大交叉率");
        }
        if (minimumMutationRate > maximumMutationRate) {
            throw new IllegalArgumentException("最小变异率不能大于最大变异率");
        }
    }

    public static AdaptiveGeneticAlgorithmConfig defaults() {
        return new AdaptiveGeneticAlgorithmConfig(0.60, 0.95, 0.05, 0.45);
    }

    private static void checkRate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + "必须在0和1之间");
        }
    }
}
