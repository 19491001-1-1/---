package com.assessment.packing.solver;

public record HybridGeneticAlgorithmStatistics(
        long localSearchCalls,
        long localCandidateEvaluations,
        long localImprovements,
        long totalAreaGain,
        long acceptedWorseMoves,
        long aspirationAcceptances,
        long duplicateLocalResults,
        long skippedPreviouslyOptimized) {

    public static HybridGeneticAlgorithmStatistics empty() {
        return new HybridGeneticAlgorithmStatistics(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public double improvementRate() {
        return localSearchCalls == 0 ? 0.0 : (double) localImprovements / localSearchCalls;
    }
}
