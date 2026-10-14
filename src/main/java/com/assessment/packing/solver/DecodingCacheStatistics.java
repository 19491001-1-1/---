package com.assessment.packing.solver;

/**
 * 一次搜索里 {@link DecodingCache} 的工作量账本。
 *
 * <p>记录它<b>是否启用</b>以及启用时的查询/命中/淘汰次数。这组数字是判断缓存究竟是净收益
 * 还是净开销的依据：缓存命中率长期接近零时，算哈希、查表、存条目、占内存、增 GC 都是白花，
 * 此时应当关闭缓存而不是继续为它买单。
 *
 * <p>统计口径只覆盖「由搜索循环发起的评价」：收尾时那一次 {@link DecodingCache#solve}
 * 真实解码不计入，避免污染评价计数口径。
 *
 * <p>{@code enabled=false} 时全部计数为 0，其语义就是「不查表」而非「查了没命中」。
 */
public record DecodingCacheStatistics(
        boolean enabled,
        long queries,
        long hits,
        long evictions) {

    public static DecodingCacheStatistics empty() {
        return new DecodingCacheStatistics(false, 0, 0, 0);
    }

    public long misses() {
        return queries - hits;
    }

    /** 命中率（百分数）；零查询或未启用时记为 0。 */
    public double hitRatePercent() {
        if (!enabled || queries == 0) return 0.0;
        return 100.0 * hits / queries;
    }
}
