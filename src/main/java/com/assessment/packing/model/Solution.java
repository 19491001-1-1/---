package com.assessment.packing.model;

import java.util.List;

/**
 * 一次求解的输出：算法名称、放置列表和耗时。
 *
 * <p>这里是普通 final 类而不是 record，原因是需要在构造时把装入面积算一次并缓存。
 * 搜索算法会在循环条件里反复比较面积，逐次对放置列表求和会成为热路径上的重复开销。
 * 对外的构造签名、访问器名称和相等语义与原先的 record 完全一致。
 */
public final class Solution {
    private final String algorithm;
    private final List<Placement> placements;
    private final long elapsedNanos;
    private final long packedArea;

    public Solution(String algorithm, List<Placement> placements, long elapsedNanos) {
        if (algorithm == null || algorithm.isBlank()) throw new IllegalArgumentException("算法名称不能为空");
        if (placements == null) throw new IllegalArgumentException("放置列表不能为空");
        if (elapsedNanos < 0) throw new IllegalArgumentException("运行时间不能为负数");
        this.algorithm = algorithm;
        this.placements = List.copyOf(placements);
        this.elapsedNanos = elapsedNanos;
        long total = 0;
        for (Placement placement : this.placements) total += placement.area();
        this.packedArea = total;
    }

    public String algorithm() {
        return algorithm;
    }

    public List<Placement> placements() {
        return placements;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    /** 装入总面积，构造时已算好。 */
    public long packedArea() {
        return packedArea;
    }

    public double utilization(Instance instance) {
        return (double) packedArea / instance.binArea();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof Solution solution
                && elapsedNanos == solution.elapsedNanos
                && algorithm.equals(solution.algorithm)
                && placements.equals(solution.placements);
    }

    @Override
    public int hashCode() {
        int result = algorithm.hashCode();
        result = 31 * result + placements.hashCode();
        result = 31 * result + Long.hashCode(elapsedNanos);
        return result;
    }

    @Override
    public String toString() {
        return "Solution[algorithm=" + algorithm
                + ", placements=" + placements
                + ", elapsedNanos=" + elapsedNanos + "]";
    }
}
