package com.assessment.packing.solver;

public record TabuSearchStatistics(
        int completedIterations,
        long evaluatedNeighbors,
        long tabuHits,
        int bestImprovements,
        int acceptedWorseMoves,
        int aspirationAcceptances,
        int expandedNeighborhoods,
        int stalledIterations,
        DecodingCacheStatistics cache) {

    public static TabuSearchStatistics empty() {
        return new TabuSearchStatistics(0, 0, 0, 0, 0, 0, 0, 0, DecodingCacheStatistics.empty());
    }

    /** 缓存命中率（百分数）；未启用或零查询时记为 0。 */
    public double cacheHitRatePercent() {
        return cache.hitRatePercent();
    }
}
