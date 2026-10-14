package com.assessment.packing.solver;

public record TabuSearchConfig(
        int maxIterations,
        int neighborhoodSize,
        int tabuTenure,
        int maxNoImprovement,
        long seed) {

    public TabuSearchConfig {
        if (maxIterations <= 0) throw new IllegalArgumentException("最大迭代数必须为正数");
        if (neighborhoodSize <= 0) throw new IllegalArgumentException("邻居数量必须为正数");
        if (tabuTenure <= 0) throw new IllegalArgumentException("禁忌期限必须为正数");
        if (maxNoImprovement <= 0) throw new IllegalArgumentException("无改进上限必须为正数");
    }

    public static TabuSearchConfig defaults(long seed) {
        return new TabuSearchConfig(1_000, 50, 10, 300, seed);
    }
}
