package com.assessment.packing.solver;

/**
 * 元启发式搜索的可选硬限制。最大评价数统计算法实际评价的候选排列；
 * 最大时间从solve开始计时。Long.MAX_VALUE表示不限制。
 */
public record SearchLimit(long maxEvaluations, long maxNanos) {
    public SearchLimit {
        if (maxEvaluations <= 0) throw new IllegalArgumentException("评价上限必须为正数");
        if (maxNanos <= 0) throw new IllegalArgumentException("时间上限必须为正数");
    }

    public static SearchLimit unlimited() {
        return new SearchLimit(Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public static SearchLimit evaluations(long maximum) {
        return new SearchLimit(maximum, Long.MAX_VALUE);
    }

    public static SearchLimit milliseconds(long milliseconds) {
        if (milliseconds <= 0) throw new IllegalArgumentException("毫秒上限必须为正数");
        long nanos = milliseconds > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE : milliseconds * 1_000_000L;
        return new SearchLimit(Long.MAX_VALUE, nanos);
    }

    public boolean timeReached(long startNanos) {
        return maxNanos != Long.MAX_VALUE && System.nanoTime() - startNanos >= maxNanos;
    }
}
