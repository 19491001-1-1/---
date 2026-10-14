package com.assessment.packing.solver;

public record GeneticAlgorithmStatistics(
        int completedGenerations,
        int effectivePopulationSize,
        long fitnessEvaluations,
        long fitnessCacheHits,
        long crossoverEvents,
        long mutationEvents,
        long duplicateRejections,
        int bestImprovements) {

    public static GeneticAlgorithmStatistics empty() {
        return new GeneticAlgorithmStatistics(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public double cacheHitRate() {
        long total = fitnessEvaluations + fitnessCacheHits;
        return total == 0 ? 0.0 : (double) fitnessCacheHits / total;
    }
}
