package com.assessment.packing.solver;

/** 蚁群搜索完成后的可解释统计。 */
public record AntColonyStatistics(
        int completedIterations,
        int antCount,
        long constructedPermutations,
        long fitnessEvaluations,
        long fitnessCacheHits,
        long pheromoneDeposits,
        int bestImprovements,
        int noImprovementIterations) {

    public static AntColonyStatistics empty() {
        return new AntColonyStatistics(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public double cacheHitRate() {
        long total = fitnessEvaluations + fitnessCacheHits;
        return total == 0 ? 0.0 : (double) fitnessCacheHits / total;
    }
}
