package com.assessment.packing.bench;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.DecodingCacheStatistics;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuSearchSolver;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/**
 * 「无缓存 / 固定缓存 / 小容量固定缓存」三种解码缓存策略的对照实验。
 *
 * <p>目的不是再证明一次「缓存能提速」，而是查清缓存对不同算法究竟是净收益还是净开销：
 * 缓存要付出算哈希、查表、存条目、占内存、增 GC 的固定成本，只有在命中率足够高时才回本。
 * 因此这里同时输出查询数、命中数、命中率、淘汰数、墙钟耗时与 GC 次数，让「该不该开」由数据回答。
 *
 * <p>刻意<b>不用 JMH</b>：JMH 的 {@code @Param} 无法在 {@code @Setup} 之后改变进程级系统属性，
 * 而策略切换正是本实验的自变量。改用确定性探针，且同一次 JVM 内逐个策略切换后再跑，
 * 保证各策略面对同一套 JIT 与堆状态。
 *
 * <p>运行：
 * <pre>{@code
 * ./mvnw -Pbench exec:exec -Dexec.mainClass=com.assessment.packing.bench.CacheStrategyProbe
 * }</pre>
 */
public final class CacheStrategyProbe {
    private static final String[] ALGORITHMS = {
            "TABU_MOVE", "SA", "ACO", "ILS", "GA", "ADAPTIVE_GA"
    };
    private static final long SEED = 20261012L;
    private static final long BUDGET = 20000;
    private static final int REPEATS = 5;

    /** 一条策略：名称 + 交给 {@code PACKING_CACHE_CAPACITY} 的容量。 */
    private record Strategy(String name, int capacity) {}

    private static final List<Strategy> STRATEGIES = List.of(
            new Strategy("固定4096", 4096),
            new Strategy("无缓存", 0),
            new Strategy("固定64", 64));

    public static void main(String[] args) {
        String[] instances = args.length > 0 ? args : new String[]{"gcut3", "gcut12"};
        String previous = System.getProperty("PACKING_CACHE_CAPACITY");
        try {
            System.out.printf("%-11s %-8s %-10s %9s %9s %8s %8s %8s %8s %8s %10s%n",
                    "算法", "实例", "缓存策略", "中位ms", "最小ms", "GC次数",
                    "查询", "命中", "命中率%", "淘汰", "面积");
            for (String instanceName : instances) {
                for (String algorithm : ALGORITHMS) {
                    for (Strategy strategy : STRATEGIES) {
                        System.setProperty("PACKING_CACHE_CAPACITY",
                                Integer.toString(strategy.capacity()));
                        report(instanceName, algorithm, strategy);
                    }
                }
            }
        } finally {
            if (previous == null) System.clearProperty("PACKING_CACHE_CAPACITY");
            else System.setProperty("PACKING_CACHE_CAPACITY", previous);
        }
    }

    private static void report(String instanceName, String algorithm, Strategy strategy) {
        Instance instance = BenchInstances.load(instanceName);
        // 三个策略共用同一份 JVM 与 JIT 状态，谁先跑谁吃亏：JIT 编译该算法的热路径要花掉
        // 头几次运行。每次 report 前先丢弃两次运行，把这份成本移出测量窗口，否则
        // 「第一个被测的策略」会被系统性高估。
        for (int warmup = 0; warmup < 2; warmup++) createSolver(algorithm).solve(instance);

        long[] times = new long[REPEATS];
        PackingAlgorithm last = null;
        long totalGc = 0;
        long area = -1;
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            long gcBefore = gcCount();
            PackingAlgorithm solver = createSolver(algorithm);
            long start = System.nanoTime();
            area = solver.solve(instance).packedArea();
            times[repeat] = System.nanoTime() - start;
            totalGc += gcCount() - gcBefore;
            last = solver;
        }
        Arrays.sort(times);
        DecodingCacheStatistics cache = statistics(last);
        System.out.printf("%-11s %-8s %-10s %9.2f %9.2f %8.1f %8d %8d %8.2f %8d %10d%n",
                algorithm, instanceName, strategy.name(),
                times[REPEATS / 2] / 1e6, times[0] / 1e6, (double) totalGc / REPEATS,
                cache.queries(), cache.hits(), cache.hitRatePercent(),
                cache.evictions(), area);
    }

    private static long gcCount() {
        long count = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionCount();
            if (value > 0) count += value;
        }
        return count;
    }

    private static PackingAlgorithm createSolver(String algorithm) {
        return SolverFactory.create(
                AlgorithmId.valueOf(algorithm), true, ItemOrder.AREA_DESC, SEED,
                ExperimentProfile.STANDARD, SearchLimit.evaluations(BUDGET));
    }

    private static DecodingCacheStatistics statistics(PackingAlgorithm solver) {
        if (solver instanceof TabuSearchSolver tabu) return tabu.lastStatistics().cache();
        if (solver instanceof SimulatedAnnealingSolver sa) return sa.lastStatistics().cache();
        if (solver instanceof AntColonySolver aco) return aco.lastStatistics().cache();
        if (solver instanceof IteratedLocalSearchSolver ils) return ils.lastStatistics().cache();
        if (solver instanceof GeneticAlgorithmSolver ga) return ga.lastStatistics().cache();
        return DecodingCacheStatistics.empty();
    }

}
