package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 通过可注入排样解码器搜索零件优先级排列的迭代局部搜索（ILS）。 */
public final class IteratedLocalSearchSolver
        implements PackingAlgorithm, ConvergenceTraceProvider {
    private final boolean rotationAllowed;
    private final ItemOrder initialOrder;
    private final IteratedLocalSearchConfig config;
    private final SearchLimit limit;
    private final PackingDecoder decoder;
    private IteratedLocalSearchStatistics lastStatistics = IteratedLocalSearchStatistics.empty();
    private List<ConvergencePoint> lastConvergenceTrace = List.of();

    public IteratedLocalSearchSolver(
            boolean rotationAllowed, ItemOrder initialOrder,
            IteratedLocalSearchConfig config) {
        this(rotationAllowed, initialOrder, config, SearchLimit.unlimited(),
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public IteratedLocalSearchSolver(
            boolean rotationAllowed, ItemOrder initialOrder,
            IteratedLocalSearchConfig config, SearchLimit limit) {
        this(rotationAllowed, initialOrder, config, limit,
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public IteratedLocalSearchSolver(
            boolean rotationAllowed, ItemOrder initialOrder,
            IteratedLocalSearchConfig config, SearchLimit limit, PackingDecoder decoder) {
        this.rotationAllowed = rotationAllowed;
        this.initialOrder = initialOrder;
        this.config = config;
        this.limit = limit;
        this.decoder = java.util.Objects.requireNonNull(decoder, "排样解码器不能为空");
    }

    @Override
    public String name() {
        return "ILS(decoder=" + decoder.name() + ", initial=" + initialOrder + ", rotation=" + rotationAllowed
                + ", seed=" + config.seed() + ")";
    }

    public IteratedLocalSearchStatistics lastStatistics() {
        return lastStatistics;
    }

    @Override
    public List<ConvergencePoint> lastConvergenceTrace() {
        return lastConvergenceTrace;
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        int[] initial = ItemOrdering.sort(instance.items(), initialOrder).stream()
                .mapToInt(Item::id).toArray();
        Map<Integer, Item> itemById = new HashMap<>();
        for (Item item : instance.items()) itemById.put(item.id(), item);
        EvaluationCache evaluator = new EvaluationCache(instance, itemById, decoder, limit, start);

        Evaluated current = evaluator.evaluate(initial);
        if (current == null) {
            Solution fallback = decoder.solveInPriorityOrder(
                    instance, ItemOrdering.sort(instance.items(), initialOrder));
            return new Solution(name(), fallback.placements(), System.nanoTime() - start);
        }
        Evaluated best = current;
        Random random = new Random(config.seed());
        List<ConvergencePoint> trace = new ArrayList<>();
        trace.add(new ConvergencePoint(0, evaluator.evaluations, best.solution.packedArea()));

        long perturbations = 0;
        long localImprovements = 0;
        long bestImprovements = 0;
        long acceptedWorseBasins = 0;
        int noImprovement = 0;
        int iterations = 0;

        while (iterations < config.maxIterations()
                && noImprovement < config.maxNoImprovementIterations()
                && best.solution.packedArea() < instance.binArea()
                && evaluator.canEvaluate()) {
            iterations++;
            int[] perturbed = current.sequence.clone();
            for (int i = 0; i < config.perturbationSwaps() && perturbed.length > 1; i++) {
                swapTwoDifferent(perturbed, random);
                perturbations++;
            }
            Evaluated candidate = evaluator.evaluate(perturbed);
            if (candidate == null) break;

            int examined = 0;
            boolean improved;
            do {
                improved = false;
                outer:
                for (int i = 0; i < candidate.sequence.length - 1; i++) {
                    for (int j = i + 1; j < candidate.sequence.length; j++) {
                        if (examined++ >= config.localSearchEvaluations()
                                || !evaluator.canEvaluate()) break outer;
                        int[] neighbor = candidate.sequence.clone();
                        swap(neighbor, i, j);
                        Evaluated evaluated = evaluator.evaluate(neighbor);
                        if (evaluated != null
                                && evaluated.solution.packedArea()
                                > candidate.solution.packedArea()) {
                            candidate = evaluated;
                            localImprovements++;
                            improved = true;
                            break outer; // 首个改进：从新的排列重新扫描邻域
                        }
                    }
                }
            } while (improved && examined < config.localSearchEvaluations()
                    && evaluator.canEvaluate());

            if (candidate.solution.packedArea() < current.solution.packedArea()) {
                acceptedWorseBasins++;
            }
            // ILS 必须进入扰动后的新盆地，历史最优解单独保留。
            current = candidate;
            if (candidate.solution.packedArea() > best.solution.packedArea()) {
                best = candidate;
                bestImprovements++;
                noImprovement = 0;
            } else {
                noImprovement++;
            }
            trace.add(new ConvergencePoint(iterations, evaluator.evaluations,
                    best.solution.packedArea()));
        }

        lastStatistics = new IteratedLocalSearchStatistics(
                iterations, evaluator.evaluations, evaluator.cacheHits,
                perturbations, localImprovements, bestImprovements, acceptedWorseBasins);
        lastConvergenceTrace = List.copyOf(trace);
        return new Solution(name(), best.solution.placements(), System.nanoTime() - start);
    }

    private static void swapTwoDifferent(int[] values, Random random) {
        int first = random.nextInt(values.length);
        int second = random.nextInt(values.length - 1);
        if (second >= first) second++;
        swap(values, first, second);
    }

    private static void swap(int[] values, int first, int second) {
        int temporary = values[first];
        values[first] = values[second];
        values[second] = temporary;
    }

    private record Evaluated(int[] sequence, Solution solution) {}

    private static final class EvaluationCache {
        private final Instance instance;
        private final Map<Integer, Item> itemById;
        private final PackingDecoder decoder;
        private final SearchLimit limit;
        private final long start;
        private final Map<SequenceKey, Solution> cache = new HashMap<>();
        private long evaluations;
        private long cacheHits;

        private EvaluationCache(
                Instance instance, Map<Integer, Item> itemById,
                PackingDecoder decoder, SearchLimit limit, long start) {
            this.instance = instance;
            this.itemById = itemById;
            this.decoder = decoder;
            this.limit = limit;
            this.start = start;
        }

        private boolean canEvaluate() {
            return evaluations < limit.maxEvaluations() && !limit.timeReached(start);
        }

        private Evaluated evaluate(int[] sequence) {
            SequenceKey key = new SequenceKey(sequence);
            Solution cached = cache.get(key);
            if (cached != null) {
                cacheHits++;
                return new Evaluated(sequence.clone(), cached);
            }
            if (!canEvaluate()) return null;
            List<Item> ordered = new ArrayList<>(sequence.length);
            for (int id : sequence) ordered.add(itemById.get(id));
            Solution solution = decoder.solveInPriorityOrder(instance, ordered);
            evaluations++;
            cache.put(key, solution);
            return new Evaluated(sequence.clone(), solution);
        }
    }

    private static final class SequenceKey {
        private final int[] values;
        private final int hash;

        private SequenceKey(int[] values) {
            this.values = values.clone();
            this.hash = Arrays.hashCode(this.values);
        }

        @Override public int hashCode() { return hash; }

        @Override
        public boolean equals(Object other) {
            return other instanceof SequenceKey key && Arrays.equals(values, key.values);
        }
    }
}
