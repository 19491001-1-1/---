package com.assessment.packing.solver;

import java.util.List;
import java.util.Objects;

/** 一次 MaxRects-BEST 求解中五种成员启发式的可解释统计。 */
public record MaxRectsBestStatistics(
        MaxRectsHeuristic selectedHeuristic,
        List<Entry> entries) {

    public MaxRectsBestStatistics {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) {
            if (selectedHeuristic != null) {
                throw new IllegalArgumentException("空统计不能指定获胜启发式");
            }
        } else {
            Objects.requireNonNull(selectedHeuristic, "非空统计必须指定获胜启发式");
            if (entries.stream().noneMatch(entry -> entry.heuristic() == selectedHeuristic)) {
                throw new IllegalArgumentException("获胜启发式必须出现在成员结果中");
            }
        }
    }

    public static MaxRectsBestStatistics empty() {
        return new MaxRectsBestStatistics(null, List.of());
    }

    public boolean available() {
        return !entries.isEmpty();
    }

    public long bestPackedArea() {
        return entries.stream().mapToLong(Entry::packedArea).max().orElse(0L);
    }

    public List<MaxRectsHeuristic> tiedBestHeuristics() {
        long best = bestPackedArea();
        return entries.stream()
                .filter(entry -> entry.packedArea() == best)
                .map(Entry::heuristic)
                .toList();
    }

    public Entry entry(MaxRectsHeuristic heuristic) {
        return entries.stream()
                .filter(entry -> entry.heuristic() == heuristic)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺少启发式统计：" + heuristic));
    }

    public record Entry(
            MaxRectsHeuristic heuristic,
            long packedArea,
            int placedItems,
            long elapsedNanos) {
        public Entry {
            Objects.requireNonNull(heuristic, "启发式不能为空");
            if (packedArea < 0 || placedItems < 0 || elapsedNanos < 0) {
                throw new IllegalArgumentException("MaxRects成员统计不能为负数");
            }
        }

        public double elapsedMilliseconds() {
            return elapsedNanos / 1_000_000.0;
        }
    }
}
