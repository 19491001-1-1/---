package com.assessment.packing.solver;

public record IteratedLocalSearchStatistics(
        int completedIterations,
        long fitnessEvaluations,
        long fitnessCacheHits,
        long perturbations,
        long localImprovements,
        long bestImprovements,
        long acceptedWorseBasins) {

    public static IteratedLocalSearchStatistics empty() {
        return new IteratedLocalSearchStatistics(0, 0, 0, 0, 0, 0, 0);
    }
}
