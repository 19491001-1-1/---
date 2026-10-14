package com.assessment.packing.model;

import java.util.List;

public record Solution(String algorithm, List<Placement> placements, long elapsedNanos) {
    public Solution {
        if (algorithm == null || algorithm.isBlank()) throw new IllegalArgumentException("算法名称不能为空");
        if (placements == null) throw new IllegalArgumentException("放置列表不能为空");
        placements = List.copyOf(placements);
        if (elapsedNanos < 0) throw new IllegalArgumentException("运行时间不能为负数");
    }

    public long packedArea() {
        return placements.stream().mapToLong(Placement::area).sum();
    }

    public double utilization(Instance instance) {
        return (double) packedArea() / instance.binArea();
    }
}
