package com.assessment.packing.experiment;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.AntColonyConfig;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.DecodingCacheCapacity;
import com.assessment.packing.solver.DecodingCacheStatistics;
import com.assessment.packing.solver.IteratedLocalSearchConfig;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.SimulatedAnnealingConfig;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuMode;
import com.assessment.packing.solver.TabuSearchConfig;
import com.assessment.packing.solver.TabuSearchSolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「无缓存 / 固定缓存」两种策略必须产出<b>逐位相同</b>的搜索结果。
 *
 * <p>缓存是纯记忆层：它只能少解码，不能改变同一排列的解码结果，因此开与关只应影响耗时，
 * 不影响面积、布局、评价数或收敛轨迹。这条等价性是对照实验成立的前提——否则「缓存更快」
 * 这种结论会被「缓存顺带改了解」污染。
 *
 * <p>同时守住另一面：打开缓存时命中率不能是 0，否则「固定缓存」这一组测的其实是
 * 「缓存开销 + 每次都未命中」，对照实验本身就没有意义。
 */
final class CacheStrategyEquivalenceTest {

    /** 实例取自仓库自带 gcut 数据；规模足够大，缓存才会真的被查到。 */
    private static final String INSTANCE_FILE = "gcut01/gcut3.txt";
    private static final long SEED = 20261012L;

    @AfterEach
    void clearCapacity() {
        System.clearProperty(DecodingCacheCapacity.ENVIRONMENT_VARIABLE);
    }

    @Test
    void tabuProducesIdenticalResultWithAndWithoutCache() throws IOException {
        Instance instance = new InstanceReader().read(java.nio.file.Path.of(INSTANCE_FILE));
        TabuSearchConfig config = new TabuSearchConfig(60, 20, 5, 30, SEED);

        var cached = solveTabu(instance, config, DecodingCacheCapacity.DEFAULT_CAPACITY);
        var disabled = solveTabu(instance, config, 0);

        assertEquals(cached.solution().packedArea(), disabled.solution().packedArea(),
                "开关缓存不得改变装载面积");
        assertEquals(cached.solution().placements(), disabled.solution().placements(),
                "开关缓存不得改变布局");
        assertEquals(cached.evaluations(), disabled.evaluations(), "开关缓存不得改变评价次数");
        assertEquals(cached.iterations(), disabled.iterations(), "开关缓存不得改变迭代次数");
    }

    @Test
    void simulatedAnnealingProducesIdenticalResultWithAndWithoutCache() throws IOException {
        Instance instance = new InstanceReader().read(java.nio.file.Path.of(INSTANCE_FILE));
        SimulatedAnnealingConfig config =
                new SimulatedAnnealingConfig(20, 10, 0.95, 0.01, 8, 0.8, 30, SEED);

        var cached = solveAnnealing(instance, config, DecodingCacheCapacity.DEFAULT_CAPACITY);
        var disabled = solveAnnealing(instance, config, 0);

        assertEquals(cached.solution().packedArea(), disabled.solution().packedArea(),
                "开关缓存不得改变装载面积");
        assertEquals(cached.solution().placements(), disabled.solution().placements(),
                "开关缓存不得改变布局");
        assertEquals(cached.evaluations(), disabled.evaluations(), "开关缓存不得改变评价次数");
    }

    /**
     * 缓存对 Tabu 到底有没有价值，答案取决于命中率而不是信念：命中率近零时它纯属开销。
     * 这里只固定「能查到」这一事实与账本自洽性，具体的收益判断留给基准数据。
     */
    @Test
    void enabledCacheReportsConsistentHitAccounting() throws IOException {
        Instance instance = new InstanceReader().read(java.nio.file.Path.of(INSTANCE_FILE));
        TabuSearchConfig config = new TabuSearchConfig(60, 20, 5, 30, SEED);

        var cached = solveTabu(instance, config, DecodingCacheCapacity.DEFAULT_CAPACITY);

        DecodingCacheStatistics statistics = cached.cache();
        assertTrue(statistics.enabled(), "固定容量下统计应报告缓存已启用");
        assertEquals(statistics.queries() - statistics.hits(), statistics.misses(),
                "未命中数必须等于查询数减命中数");
        assertTrue(statistics.queries() > 0, "实际评价过就必须有查询记账");
        assertTrue(statistics.hits() > 0,
                "Tabu 会反复碰到同一排列，固定缓存下命中数不应为 0");
        assertTrue(statistics.hitRatePercent() >= 0.0 && statistics.hitRatePercent() <= 100.0,
                "命中率必须落在 0~100 之间，实际=" + statistics.hitRatePercent());

        assertFalse(solveTabu(instance, config, 0).cache().enabled(),
                "容量为 0 时必须报告缓存未启用");
    }

    /**
     * ILS 与 ACO 的缓存命中率明显高于 Tabu/SA，一并守住它们不会因开关而变解。
     *
     * <p>关键判据是<b>总评价次数</b>（真实解码 + 缓存命中），而不是单独的解码次数：这两族算法
     * 只对真实解码扣预算，命中免费，所以同一段搜索轨迹在开缓存时表现为「较少的解码 + 若干命中」，
     * 关缓存时表现为「等量的解码 + 零命中」。两者总和相同，轨迹才相同，解才逐位相同。
     * 若拿解码次数去比，会把「缓存省下的工作量」误判成「结果被改动」。
     */
    @Test
    void iteratedLocalSearchAndAntColonyKeepIdenticalResults() throws IOException {
        Instance instance = new InstanceReader().read(java.nio.file.Path.of(INSTANCE_FILE));
        IteratedLocalSearchConfig ilsConfig =
                new IteratedLocalSearchConfig(30, 20, 4, 10, SEED);
        AntColonyConfig acoConfig = AntColonyConfig.defaults(SEED);

        var ilsCached = solveIls(instance, ilsConfig, DecodingCacheCapacity.DEFAULT_CAPACITY);
        var ilsDisabled = solveIls(instance, ilsConfig, 0);
        assertEquals(ilsCached.solution().packedArea(), ilsDisabled.solution().packedArea(),
                "ILS 开关缓存不得改变装载面积");
        assertEquals(ilsCached.solution().placements(), ilsDisabled.solution().placements(),
                "ILS 开关缓存不得改变布局");
        assertEquals(ilsCached.totalEvaluations(), ilsDisabled.totalEvaluations(),
                "ILS 总评价次数必须相同，轨迹才相同");
        assertEquals(ilsCached.iterations(), ilsDisabled.iterations(),
                "ILS 开关缓存不得改变迭代次数");
        assertTrue(ilsCached.evaluations() < ilsDisabled.evaluations(),
                "开缓存应确实省下真实解码：实际 " + ilsCached.evaluations()
                        + " 对 " + ilsDisabled.evaluations());

        var acoCached = solveAntColony(instance, acoConfig, DecodingCacheCapacity.DEFAULT_CAPACITY);
        var acoDisabled = solveAntColony(instance, acoConfig, 0);
        assertEquals(acoCached.solution().packedArea(), acoDisabled.solution().packedArea(),
                "ACO 开关缓存不得改变装载面积");
        assertEquals(acoCached.solution().placements(), acoDisabled.solution().placements(),
                "ACO 开关缓存不得改变布局");
        assertEquals(acoCached.totalEvaluations(), acoDisabled.totalEvaluations(),
                "ACO 总评价次数必须相同，轨迹才相同");
        assertEquals(acoCached.iterations(), acoDisabled.iterations(),
                "ACO 开关缓存不得改变迭代次数");
        assertTrue(acoCached.evaluations() < acoDisabled.evaluations(),
                "开缓存应确实省下真实解码：实际 " + acoCached.evaluations()
                        + " 对 " + acoDisabled.evaluations());
    }

    /**
     * 钉住两族算法在「缓存是否占预算」上的差别，免得日后被当成计数器 bug「修」掉。
     *
     * <p>Tabu/SA 以「尝试次数」扣预算，命中不减免，因此开关缓存不改变评价总数；
     * ILS/ACO/GA 以「真实解码次数」扣预算，命中免费，因此同样预算下开缓存能多跑若干次总评价。
     * 后者是缓存收益的一部分（在固定解码成本下搜索得更充分），不是副作用。
     */
    @Test
    void decodeCountingBudgetDiffersBetweenAlgorithmFamilies() throws IOException {
        Instance instance = new InstanceReader().read(java.nio.file.Path.of(INSTANCE_FILE));
        TabuSearchConfig tabuConfig = new TabuSearchConfig(60, 20, 5, 30, SEED);

        var tabuCached = solveTabu(instance, tabuConfig, DecodingCacheCapacity.DEFAULT_CAPACITY,
                SearchLimit.evaluations(1200));
        var tabuDisabled = solveTabu(instance, tabuConfig, 0, SearchLimit.evaluations(1200));
        assertEquals(tabuCached.totalEvaluations(), tabuDisabled.totalEvaluations(),
                "Tabu 以尝试次数计预算，命中不减免，故开关缓存不改变评价总数");
        assertTrue(tabuCached.cache().hits() > 0, "该实例上 Tabu 应确实命中过缓存");

        IteratedLocalSearchConfig ilsConfig =
                new IteratedLocalSearchConfig(200, 30, 4, 60, SEED);
        var ilsCached = solveIls(instance, ilsConfig, DecodingCacheCapacity.DEFAULT_CAPACITY,
                SearchLimit.evaluations(400));
        var ilsDisabled = solveIls(instance, ilsConfig, 0, SearchLimit.evaluations(400));
        assertEquals(400, ilsCached.evaluations(), "开缓存时解码预算应被用满");
        assertEquals(400, ilsDisabled.evaluations(), "关缓存时解码预算同样被用满");
        assertTrue(ilsCached.totalEvaluations() > ilsDisabled.totalEvaluations(),
                "ILS 命中免费：同一解码预算内开缓存能多跑总评价，实际="
                        + ilsCached.totalEvaluations() + " 对 " + ilsDisabled.totalEvaluations());
        assertTrue(ilsCached.iterations() >= ilsDisabled.iterations(),
                "多出的免费评价不应让 ILS 少跑迭代");
    }

    /** ACO 内部用零件下标排序，这里换个零件编号不连续的实例，顺带盖住编号映射。 */
    @Test
    void cacheStrategyEquivalenceHoldsForSparseItemIds() {
        Instance instance = new Instance("sparse", 40, 40, java.util.List.of(
                new Item(3, 12, 9), new Item(8, 7, 15), new Item(11, 20, 6),
                new Item(14, 9, 9), new Item(21, 6, 18), new Item(30, 15, 15)));
        TabuSearchConfig config = new TabuSearchConfig(40, 15, 4, 20, SEED);

        var cached = solveTabu(instance, config, DecodingCacheCapacity.DEFAULT_CAPACITY);
        var disabled = solveTabu(instance, config, 0);

        assertEquals(cached.solution().packedArea(), disabled.solution().packedArea(),
                "编号不连续时开关缓存仍不得改变装载面积");
        assertEquals(cached.solution().placements(), disabled.solution().placements(),
                "编号不连续时开关缓存仍不得改变布局");
    }

    /**
     * 一次求解的可比对结果。
     *
     * <p>{@code evaluations} 是算法自己定义的「评价次数」；{@code totalEvaluations} 是
     * <b>总评价次数</b>（真实解码 + 缓存命中），它与缓存开关无关，才是决定搜索轨迹的量。
     *
     * <p>两者不能一概相加：Tabu/SA 的 {@code evaluatedNeighbors}/{@code searchNeighborEvaluations}
     * 本就把命中计在内，而 ILS/ACO/GA 的 {@code fitnessEvaluations} 只数真实解码、命中另记
     * {@code fitnessCacheHits}。因此总数由各 helper 按自己算法的口径显式给出，不在这里做统一推导。
     */
    private record Result(
            com.assessment.packing.model.Solution solution,
            long evaluations,
            long totalEvaluations,
            int iterations,
            DecodingCacheStatistics cache) {}

    private static Result solveTabu(Instance instance, TabuSearchConfig config, int capacity) {
        return solveTabu(instance, config, capacity, SearchLimit.evaluations(5000));
    }

    private static Result solveTabu(
            Instance instance, TabuSearchConfig config, int capacity, SearchLimit limit) {
        withCapacity(capacity);
        TabuSearchSolver solver = new TabuSearchSolver(
                true, ItemOrder.AREA_DESC, TabuMode.MOVE, config, limit);
        var solution = solver.solve(instance);
        var statistics = solver.lastStatistics();
        // Tabu 的 evaluatedNeighbors 已包含命中，总数即它本身。
        return new Result(solution, statistics.evaluatedNeighbors(),
                statistics.evaluatedNeighbors(), statistics.completedIterations(),
                statistics.cache());
    }

    private static Result solveAnnealing(
            Instance instance, SimulatedAnnealingConfig config, int capacity) {
        withCapacity(capacity);
        SimulatedAnnealingSolver solver = new SimulatedAnnealingSolver(
                true, ItemOrder.AREA_DESC, config, SearchLimit.evaluations(5000));
        var solution = solver.solve(instance);
        var statistics = solver.lastStatistics();
        // SA 与 Tabu 同口径：每次尝试都计一次评价，命中不减免。
        long attempts = statistics.temperatureSampleEvaluations()
                + statistics.searchNeighborEvaluations();
        return new Result(solution, attempts, attempts,
                statistics.completedTemperatureLevels(), statistics.cache());
    }

    private static Result solveIls(
            Instance instance, IteratedLocalSearchConfig config, int capacity) {
        return solveIls(instance, config, capacity, SearchLimit.unlimited());
    }

    private static Result solveIls(
            Instance instance, IteratedLocalSearchConfig config, int capacity, SearchLimit limit) {
        withCapacity(capacity);
        IteratedLocalSearchSolver solver = new IteratedLocalSearchSolver(
                true, ItemOrder.AREA_DESC, config, limit);
        var solution = solver.solve(instance);
        var statistics = solver.lastStatistics();
        // ILS/ACO 的 fitnessEvaluations 只数真实解码，命中另记，故总数需相加。
        return new Result(solution, statistics.fitnessEvaluations(),
                statistics.fitnessEvaluations() + statistics.fitnessCacheHits(),
                statistics.completedIterations(), statistics.cache());
    }

    private static Result solveAntColony(
            Instance instance, AntColonyConfig config, int capacity) {
        return solveAntColony(instance, config, capacity, SearchLimit.unlimited());
    }

    private static Result solveAntColony(
            Instance instance, AntColonyConfig config, int capacity, SearchLimit limit) {
        withCapacity(capacity);
        AntColonySolver solver = new AntColonySolver(
                true, ItemOrder.AREA_DESC, config, limit);
        var solution = solver.solve(instance);
        var statistics = solver.lastStatistics();
        // ILS/ACO 的 fitnessEvaluations 只数真实解码，命中另记，故总数需相加。
        return new Result(solution, statistics.fitnessEvaluations(),
                statistics.fitnessEvaluations() + statistics.fitnessCacheHits(),
                statistics.completedIterations(), statistics.cache());
    }

    /** 以系统属性下达容量，跑完即恢复，避免污染同 JVM 内的其它测试。 */
    private static void withCapacity(int capacity) {
        System.setProperty(DecodingCacheCapacity.ENVIRONMENT_VARIABLE, Integer.toString(capacity));
    }
}
