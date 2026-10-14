package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.GeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.GeneticMutationMode;
import com.assessment.packing.solver.HybridGeneticAlgorithmConfig;
import com.assessment.packing.solver.HybridLocalSearchMode;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SimulatedAnnealingConfig;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.SimulatedAnnealingStatistics;
import com.assessment.packing.solver.TabuMode;
import com.assessment.packing.solver.TabuSearchConfig;
import com.assessment.packing.solver.TabuSearchSolver;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索层的金标准：把固定实例、固定种子下各算法的精确面积与统计钉在一处。
 *
 * <p><b>为什么需要它</b>：各算法测试里的「同种子跑两遍一致」只能证明确定性，抓不住
 * 共享缓冲区别名 / 就地改写这类**确定性错误**——错误结果在两次运行里长得一模一样。
 * 性能优化（缓存、copy-on-accept、复用缓冲）最容易踩这个坑，所以把精确数值钉死：
 * 一旦候选数组被复用或别名，面积与统计计数会立刻偏离。
 *
 * <p>所有金标准集中在同一张表里，改搜索行为时只需在此一处核对，不会漏掉某条模式。
 * 数值由独立探针在一次运行中取得，与实现细节无关。
 */
public final class GoldenPinsTest {
    public static void main(String[] args) throws Exception {
        tabuPinsAllModes();
        simulatedAnnealingPin();
        hybridGeneticAlgorithmPin();
        System.out.println("GoldenPinsTest: 全部通过");
    }

    /** Tabu 六种模式在 gcut5 上必须产出同一面积，但各自的统计不同。 */
    private static void tabuPinsAllModes() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut5.txt"));
        long expectedArea = 233969L;
        Map<TabuMode, String> expected = Map.of(
                TabuMode.FULL_STATE,
                "TabuSearchStatistics[completedIterations=83, evaluatedNeighbors=3531, "
                        + "tabuHits=204, bestImprovements=2, acceptedWorseMoves=0, "
                        + "aspirationAcceptances=0, expandedNeighborhoods=0, stalledIterations=0]",
                TabuMode.MOVE,
                "TabuSearchStatistics[completedIterations=83, evaluatedNeighbors=3735, "
                        + "tabuHits=775, bestImprovements=2, acceptedWorseMoves=0, "
                        + "aspirationAcceptances=0, expandedNeighborhoods=0, stalledIterations=0]",
                TabuMode.POSITION_ATTRIBUTE,
                "TabuSearchStatistics[completedIterations=83, evaluatedNeighbors=3735, "
                        + "tabuHits=1195, bestImprovements=2, acceptedWorseMoves=0, "
                        + "aspirationAcceptances=0, expandedNeighborhoods=0, stalledIterations=0]",
                TabuMode.RELATIVE_ORDER_ATTRIBUTE,
                "TabuSearchStatistics[completedIterations=83, evaluatedNeighbors=3735, "
                        + "tabuHits=2829, bestImprovements=2, acceptedWorseMoves=0, "
                        + "aspirationAcceptances=1, expandedNeighborhoods=0, stalledIterations=9]",
                TabuMode.ADJACENCY_ATTRIBUTE,
                "TabuSearchStatistics[completedIterations=83, evaluatedNeighbors=3735, "
                        + "tabuHits=2794, bestImprovements=2, acceptedWorseMoves=8, "
                        + "aspirationAcceptances=0, expandedNeighborhoods=0, stalledIterations=0]",
                TabuMode.PLACEMENT_ATTRIBUTE,
                "TabuSearchStatistics[completedIterations=83, evaluatedNeighbors=3735, "
                        + "tabuHits=470, bestImprovements=2, acceptedWorseMoves=0, "
                        + "aspirationAcceptances=1, expandedNeighborhoods=0, stalledIterations=8]");

        for (TabuMode mode : TabuMode.values()) {
            TabuSearchSolver solver = new TabuSearchSolver(
                    true, ItemOrder.AREA_DESC, mode,
                    new TabuSearchConfig(100, 50, 10, 80, 20261012L));
            Solution solution = solver.solve(instance);
            check(solution.packedArea() == expectedArea,
                    "Tabu/" + mode + " 金标准面积不符：" + solution.packedArea());
            check(solver.lastStatistics().toString().equals(expected.get(mode)),
                    "Tabu/" + mode + " 金标准统计不符：" + solver.lastStatistics());
        }
    }

    /** 模拟退火在 gcut2 上的精确计数指标与温度量级。 */
    private static void simulatedAnnealingPin() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        SimulatedAnnealingSolver solver = new SimulatedAnnealingSolver(
                true, ItemOrder.AREA_DESC,
                new SimulatedAnnealingConfig(50, 10, 0.95, 0.01, 20, 0.8, 100, 20261012L));
        Solution solution = solver.solve(instance);
        check(solution.packedArea() == 60375L, "SA 金标准面积不符：" + solution.packedArea());

        SimulatedAnnealingStatistics statistics = solver.lastStatistics();
        Map<String, Long> actual = new LinkedHashMap<>();
        actual.put("completedTemperatureLevels",
                Long.valueOf(statistics.completedTemperatureLevels()));
        actual.put("temperatureSampleEvaluations", statistics.temperatureSampleEvaluations());
        actual.put("searchNeighborEvaluations", statistics.searchNeighborEvaluations());
        actual.put("acceptedImprovingMoves", statistics.acceptedImprovingMoves());
        actual.put("acceptedEqualMoves", statistics.acceptedEqualMoves());
        actual.put("acceptedWorseMoves", statistics.acceptedWorseMoves());
        actual.put("rejectedMoves", statistics.rejectedMoves());
        actual.put("bestImprovements", Long.valueOf(statistics.bestImprovements()));
        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("completedTemperatureLevels", 28L);
        expected.put("temperatureSampleEvaluations", 100L);
        expected.put("searchNeighborEvaluations", 280L);
        expected.put("acceptedImprovingMoves", 40L);
        expected.put("acceptedEqualMoves", 180L);
        expected.put("acceptedWorseMoves", 32L);
        expected.put("rejectedMoves", 28L);
        expected.put("bestImprovements", 6L);
        check(actual.equals(expected), "SA 金标准计数不符：实际=" + actual + "，期望=" + expected);

        // 温度由面积差推导，属浮点计算：钉住量级，不做逐位比较。
        check(Math.abs(statistics.initialTemperature() - 0.23067980930322698) < 1.0e-12,
                "SA 金标准初始温度不符：" + statistics.initialTemperature());
        check(Math.abs(statistics.finalTemperature() - 0.05486186053788046) < 1.0e-12,
                "SA 金标准终止温度不符：" + statistics.finalTemperature());
    }

    /** 混合 GA 的局部搜索同时走 GA 评价器与逐候选排列复制路径，独立钉一组。 */
    private static void hybridGeneticAlgorithmPin() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        GeneticAlgorithmSolver solver = new GeneticAlgorithmSolver(
                true, ItemOrder.AREA_DESC,
                new GeneticAlgorithmConfig(
                        40, 30, 3, 0.9, 0.2, 2, 15, 10,
                        GeneticMutationMode.MIXED, 777L),
                HybridLocalSearchMode.ANNEALING,
                new HybridGeneticAlgorithmConfig(5, 0.10, 4, 100, 4, 10, 0.90, 0.30, 5));
        Solution solution = solver.solve(instance);
        check(solution.packedArea() == 60375L, "混合GA 金标准面积不符：" + solution.packedArea());
        check(solver.lastHybridStatistics().toString().equals(
                "HybridGeneticAlgorithmStatistics[localSearchCalls=12, "
                        + "localCandidateEvaluations=600, localImprovements=0, totalAreaGain=0, "
                        + "acceptedWorseMoves=24, aspirationAcceptances=0, duplicateLocalResults=0, "
                        + "skippedPreviouslyOptimized=0]"),
                "混合GA 金标准局部搜索统计不符：" + solver.lastHybridStatistics());
        check(solver.lastBestAreaHistory().equals(List.of(
                58269L, 60375L, 60375L, 60375L, 60375L, 60375L, 60375L, 60375L, 60375L,
                60375L, 60375L, 60375L, 60375L, 60375L, 60375L, 60375L, 60375L)),
                "混合GA 金标准收敛曲线不符：" + solver.lastBestAreaHistory());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
