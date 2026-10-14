package com.assessment.packing.solver;

public record SimulatedAnnealingStatistics(
        int completedTemperatureLevels,
        long temperatureSampleEvaluations,
        long searchNeighborEvaluations,
        long acceptedImprovingMoves,
        long acceptedEqualMoves,
        long acceptedWorseMoves,
        long rejectedMoves,
        int bestImprovements,
        double initialTemperature,
        double finalTemperature) {

    public static SimulatedAnnealingStatistics empty() {
        return new SimulatedAnnealingStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0);
    }

    public long acceptedMoves() {
        return acceptedImprovingMoves + acceptedEqualMoves + acceptedWorseMoves;
    }

    public double acceptanceRate() {
        return searchNeighborEvaluations == 0
                ? 0.0
                : (double) acceptedMoves() / searchNeighborEvaluations;
    }
}
