package com.assessment.packing.bench;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.PackingDecoder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.PackingDecoders;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuSearchSolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 估算「解码耗时占单次搜索总耗时的比例」，用于判断增量评价/加速解码是否值得做。
 *
 * <p>这里刻意<b>不用 JMH</b>：JMH 只报告实测耗时，而本探针要报告的是一个派生比值
 * （解码次数 × 单次解码成本 ÷ 总耗时），用 AverageTime 模式返回该比值会被 JMH 忽略，
 * 输出会退化成总耗时本身，产生误导。改为确定性探针，输出可以逐项核对。
 *
 * <p>运行：{@code ./mvnw -Pbench exec:exec -Dexec.mainClass=com.assessment.packing.bench.DecodeShareProbe}
 */
public final class DecodeShareProbe {
    private static final String[] ALGORITHMS = {
            "TABU_MOVE", "SA", "ACO", "ILS", "GA", "ADAPTIVE_GA"
    };

    public static void main(String[] args) {
        String[] instances = args.length > 0 ? args : new String[]{"gcut3", "gcut12"};
        System.out.printf("%-12s %-8s %8s %10s %9s %8s%n",
                "算法", "实例", "总ms", "解码ms", "解码占比", "解码率%");
        for (String instanceName : instances) {
            for (String algorithm : ALGORITHMS) {
                report(instanceName, algorithm);
            }
        }
    }

    private static void report(String instanceName, String algorithm) {
        Instance instance = BenchInstances.load(instanceName);
        PackingDecoder decoder = PackingDecoders.create(PackingDecoderType.SKYLINE, true);
        long singleDecodeNanos = measureSingleDecode(instance, decoder);
        PackingAlgorithm solver = SolverFactory.create(
                AlgorithmId.valueOf(algorithm), true, ItemOrder.AREA_DESC, 20261012L,
                ExperimentProfile.STANDARD, SearchLimit.evaluations(20000));
        long start = System.nanoTime();
        solver.solve(instance);
        long totalNanos = System.nanoTime() - start;
        long decodes = decoderCalls(solver);
        long decodeNanos = decodes * singleDecodeNanos;
        System.out.printf("%-12s %-8s %8.2f %10.2f %8.1f%% %7.2f%%%n",
                algorithm, instanceName, totalNanos / 1e6, decodeNanos / 1e6,
                100.0 * decodeNanos / totalNanos, 100.0 * evals(solver) / decodes);
    }

    /** 预热后取多次均值，作为该实例上一次真实解码的成本。 */
    private static long measureSingleDecode(Instance instance, PackingDecoder decoder) {
        List<Item> priority = new ArrayList<>(instance.items());
        for (int i = 0; i < 500; i++) decoder.solveInPriorityOrder(instance, priority);
        int repeats = 3000;
        long start = System.nanoTime();
        for (int i = 0; i < repeats; i++) decoder.solveInPriorityOrder(instance, priority);
        return (System.nanoTime() - start) / repeats;
    }

    /** 缓存命中率，用于说明缓存对哪些算法真的有效。 */
    private static double evals(PackingAlgorithm solver) {
        long hits = 0;
        if (solver instanceof GeneticAlgorithmSolver ga) hits = ga.lastStatistics().fitnessCacheHits();
        else if (solver instanceof AntColonySolver aco) hits = aco.lastStatistics().fitnessCacheHits();
        else if (solver instanceof IteratedLocalSearchSolver ils) {
            hits = ils.lastStatistics().fitnessCacheHits();
        }
        long decodes = decoderCalls(solver);
        return decodes == 0 ? 0 : 100.0 * hits / decodes;
    }

    private static long decoderCalls(PackingAlgorithm solver) {
        if (solver instanceof TabuSearchSolver tabu) return tabu.lastStatistics().evaluatedNeighbors();
        if (solver instanceof SimulatedAnnealingSolver sa) {
            return sa.lastStatistics().temperatureSampleEvaluations()
                    + sa.lastStatistics().searchNeighborEvaluations();
        }
        if (solver instanceof GeneticAlgorithmSolver ga) {
            return ga.lastStatistics().fitnessEvaluations();
        }
        if (solver instanceof AntColonySolver aco) {
            return aco.lastStatistics().fitnessEvaluations();
        }
        if (solver instanceof IteratedLocalSearchSolver ils) {
            return ils.lastStatistics().fitnessEvaluations();
        }
        return 1;
    }
}
