package com.assessment.packing.solver;

public record GeneticAlgorithmStatistics(
        int completedGenerations,
        int effectivePopulationSize,
        long fitnessEvaluations,
        long fitnessCacheHits,
        long crossoverEvents,
        long mutationEvents,
        long duplicateRejections,
        int bestImprovements,
        DecodingCacheStatistics cache) {

    public static GeneticAlgorithmStatistics empty() {
        return new GeneticAlgorithmStatistics(
                0, 0, 0, 0, 0, 0, 0, 0, DecodingCacheStatistics.empty());
    }

    public double cacheHitRate() {
        long total = fitnessEvaluations + fitnessCacheHits;
        return total == 0 ? 0.0 : (double) fitnessCacheHits / total;
    }
}
