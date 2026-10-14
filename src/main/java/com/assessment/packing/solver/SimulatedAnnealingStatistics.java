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
        double finalTemperature,
        DecodingCacheStatistics cache) {

    public static SimulatedAnnealingStatistics empty() {
        return new SimulatedAnnealingStatistics(
                0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, DecodingCacheStatistics.empty());
    }

    /** 缓存命中率（百分数）；未启用或零查询时记为 0。 */
    public double cacheHitRatePercent() {
        return cache.hitRatePercent();
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
