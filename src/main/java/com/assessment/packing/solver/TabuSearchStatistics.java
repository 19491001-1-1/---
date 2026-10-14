package com.assessment.packing.solver;

public record TabuSearchStatistics(
        int completedIterations,
        long evaluatedNeighbors,
        long tabuHits,
        int bestImprovements,
        int acceptedWorseMoves,
        int aspirationAcceptances,
        int expandedNeighborhoods,
        int stalledIterations) {
    public static TabuSearchStatistics empty() {
        return new TabuSearchStatistics(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
