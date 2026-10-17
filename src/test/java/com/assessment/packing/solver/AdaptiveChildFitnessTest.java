package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 确定性评分桩隔离排列搜索，分数不是几何实验数据。 */
final class AdaptiveChildFitnessTest {
    private static final int[] AREA = {1, 2, 3, 4, 5};
    private static final int[] HEIGHT = {2, 4, 3, 5, 1};
    private static final Instance INSTANCE = new Instance("child-fitness", 100, 100,
            List.of(new Item(1, 10, 1), new Item(2, 1, 9), new Item(3, 4, 2),
                    new Item(4, 1, 7), new Item(5, 3, 2)));

    @Test
    void mutationUsesChildRatherThanCorrespondingWeakParent() {
        Scenario scenario = scenario();
        ScoreDecoder decoder = new ScoreDecoder(scenario.child);
        var solver = solver(scenario.seed, 0.0, 1.0, SearchLimit.unlimited(), decoder);
        solver.solve(INSTANCE);
        // 父代1=10，父代均值=15、最大值=20，而子代=30，必须取下界0。
        var stats = solver.lastAdaptiveStatistics();
        assertEquals(1, stats.mutationRateSamples());
        assertEquals(0.0, stats.averageMutationRate());
        assertEquals(3, solver.lastStatistics().fitnessEvaluations());
        assertEquals(1, solver.lastStatistics().bestImprovements());
        assertArrayEquals(scenario.child, decoder.finalSequence);
    }

    @Test
    void preMutationBestSurvivesMutationAndBudgetExhaustion() {
        Scenario scenario = scenario();
        for (SearchLimit limit : List.of(SearchLimit.unlimited(), SearchLimit.evaluations(3))) {
            ScoreDecoder decoder = new ScoreDecoder(scenario.child);
            var solver = solver(scenario.seed, 1.0, 1.0, limit, decoder);
            Solution result = solver.solve(INSTANCE);
            assertEquals(30, result.packedArea());
            assertArrayEquals(scenario.child, decoder.finalSequence,
                    "原地变异不能改写预评分保存的历史最好数组");
            assertTrue(solver.lastStatistics().fitnessEvaluations() <= limit.maxEvaluations());
            assertTrue(solver.lastStatistics().mutationEvents() > 0);
            assertEquals(1, solver.lastStatistics().bestImprovements());
        }
    }

    private static AdaptiveGeneticAlgorithmSolver solver(long seed, double min, double max,
                                                         SearchLimit limit, PackingDecoder decoder) {
        var ga = new GeneticAlgorithmConfig(2, 1, 1, 1, 1, 1, 1, 2,
                GeneticMutationMode.SWAP, seed);
        return new AdaptiveGeneticAlgorithmSolver(true, ItemOrder.AREA_DESC, ga,
                new AdaptiveGeneticAlgorithmConfig(1, 1, min, max), limit, decoder);
    }

    private static Scenario scenario() {
        // 初始化无随机补齐；寻找父代1较弱、父代2较强且交叉产生新排列的固定种子。
        for (long seed = 0; seed < 10_000; seed++) {
            Random random = new Random(seed);
            if (random.nextInt(2) != 1 || random.nextInt(2) != 0) continue;
            random.nextDouble(); // 交叉判断
            int first = random.nextInt(5);
            int second = random.nextInt(4);
            if (second >= first) second++;
            int[] child = GeneticAlgorithmSolver.orderCrossover(
                    AREA, HEIGHT, Math.min(first, second), Math.max(first, second));
            if (!Arrays.equals(child, AREA) && !Arrays.equals(child, HEIGHT)) {
                return new Scenario(seed, child);
            }
        }
        throw new AssertionError("找不到测试场景");
    }

    private record Scenario(long seed, int[] child) {}

    private static final class ScoreDecoder implements PackingDecoder {
        private final int[] target;
        private int[] finalSequence;

        private ScoreDecoder(int[] target) { this.target = target.clone(); }
        public String name() { return "deterministic-score-stub"; }
        public long packedAreaInPriorityOrder(Instance instance, List<Item> priority) {
            int[] sequence = priority.stream().mapToInt(Item::id).toArray();
            if (Arrays.equals(sequence, AREA)) return 10;
            if (Arrays.equals(sequence, HEIGHT)) return 20;
            return Arrays.equals(sequence, target) ? 30 : 5;
        }
        public Solution solveInPriorityOrder(Instance instance, List<Item> priority) {
            finalSequence = priority.stream().mapToInt(Item::id).toArray();
            int score = (int) packedAreaInPriorityOrder(instance, priority);
            return new Solution(name(), List.of(new Placement(new Item(99, score, 1), 0, 0, false)), 0);
        }
    }
}
