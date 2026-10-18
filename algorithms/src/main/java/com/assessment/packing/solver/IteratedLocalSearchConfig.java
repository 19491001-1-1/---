package com.assessment.packing.solver;

/** Skyline 迭代局部搜索参数。 */
public record IteratedLocalSearchConfig(
        int maxIterations,
        int localSearchEvaluations,
        int perturbationSwaps,
        int maxNoImprovementIterations,
        long seed) {

    public IteratedLocalSearchConfig {
        if (maxIterations <= 0) throw new IllegalArgumentException("最大迭代数必须为正数");
        if (localSearchEvaluations <= 0) throw new IllegalArgumentException("局部搜索评价数必须为正数");
        if (perturbationSwaps <= 0) throw new IllegalArgumentException("扰动交换数必须为正数");
        if (maxNoImprovementIterations <= 0) {
            throw new IllegalArgumentException("无改进迭代上限必须为正数");
        }
    }

    public static IteratedLocalSearchConfig defaults(long seed) {
        return new IteratedLocalSearchConfig(500, 100, 3, 100, seed);
    }
}
