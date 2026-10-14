package com.assessment.packing.solver;

public record AdaptiveGeneticAlgorithmStatistics(
        long crossoverRateSamples,
        long mutationRateSamples,
        double averageCrossoverRate,
        double averageMutationRate,
        double minimumObservedCrossoverRate,
        double maximumObservedCrossoverRate,
        double minimumObservedMutationRate,
        double maximumObservedMutationRate) {

    public static AdaptiveGeneticAlgorithmStatistics empty() {
        return new AdaptiveGeneticAlgorithmStatistics(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
