package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** 使用相邻信息素构造零件优先级排列，再交给可注入排样解码器的蚁群算法。 */
public final class AntColonySolver
        implements PackingAlgorithm, ConvergenceTraceProvider {
    private static final double INITIAL_PHEROMONE = 1.0;
    private static final double GLOBAL_BEST_WEIGHT = 2.0;
    private static final Comparator<Ant> BEST_FIRST = (first, second) -> {
        int byArea = Long.compare(second.packedArea(), first.packedArea());
        return byArea != 0 ? byArea
                : compareLexicographically(first.sequence(), second.sequence());
    };

    private final boolean rotationAllowed;
    private final ItemOrder initialOrder;
    private final AntColonyConfig config;
    private final SearchLimit searchLimit;
    private final PackingDecoder decoder;
    private AntColonyStatistics lastStatistics = AntColonyStatistics.empty();
    private List<ConvergencePoint> lastConvergenceTrace = List.of();

    public AntColonySolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            AntColonyConfig config) {
        this(rotationAllowed, initialOrder, config, SearchLimit.unlimited(),
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public AntColonySolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            AntColonyConfig config,
            SearchLimit searchLimit) {
        this(rotationAllowed, initialOrder, config, searchLimit,
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public AntColonySolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            AntColonyConfig config,
            SearchLimit searchLimit,
            PackingDecoder decoder) {
        this.rotationAllowed = rotationAllowed;
        this.initialOrder = java.util.Objects.requireNonNull(
                initialOrder, "初始排序规则不能为空");
        this.config = java.util.Objects.requireNonNull(config, "蚁群参数不能为空");
        this.searchLimit = java.util.Objects.requireNonNull(searchLimit, "搜索上限不能为空");
        this.decoder = java.util.Objects.requireNonNull(decoder, "排样解码器不能为空");
    }

    @Override
    public String name() {
        return "Ant Colony(decoder=" + decoder.name() + ", initial=" + initialOrder
                + ", ants=" + config.antCount()
                + ", rotation=" + rotationAllowed
                + ", seed=" + config.seed() + ")";
    }

    public AntColonyStatistics lastStatistics() {
        return lastStatistics;
    }

    @Override
    public List<ConvergencePoint> lastConvergenceTrace() {
        return lastConvergenceTrace;
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Item> orderedItems = ItemOrdering.sort(instance.items(), initialOrder);
        int itemCount = orderedItems.size();

        if (itemCount < 2) {
            Solution only = decoder.solveInPriorityOrder(instance, orderedItems);
            lastStatistics = AntColonyStatistics.empty();
            lastConvergenceTrace = List.of(new ConvergencePoint(0, 0, only.packedArea()));
            return new Solution(name(), only.placements(), System.nanoTime() - start);
        }

        int[] initialSequence = new int[itemCount];
        for (int index = 0; index < itemCount; index++) initialSequence[index] = index;
        double[] desirability = desirability(orderedItems);
        double[][] pheromone = new double[itemCount + 1][itemCount];
        for (double[] row : pheromone) Arrays.fill(row, INITIAL_PHEROMONE);

        FitnessEvaluator evaluator = new FitnessEvaluator(
                instance, orderedItems, decoder, searchLimit, start);
        long initialArea = evaluator.evaluateInitial(initialSequence);
        int[] bestSequence = initialSequence.clone();
        long bestArea = initialArea;
        double maximumPheromone = Math.max(
                INITIAL_PHEROMONE,
                config.depositStrength() * (GLOBAL_BEST_WEIGHT + config.eliteAnts())
                        / config.evaporationRate());
        double minimumPheromone = maximumPheromone / 1_000.0;
        deposit(pheromone, initialSequence, config.depositStrength(), maximumPheromone);

        List<ConvergencePoint> trace = new ArrayList<>();
        trace.add(new ConvergencePoint(0, evaluator.evaluations, bestArea));
        Random random = new Random(config.seed());
        int completedIterations = 0;
        int noImprovementIterations = 0;
        int bestImprovements = 0;
        long constructedPermutations = 0;
        long pheromoneDeposits = 1;

        while (completedIterations < config.maxIterations()
                && noImprovementIterations < config.maxNoImprovementIterations()
                && bestArea < instance.binArea()
                && !searchLimit.timeReached(start)) {
            List<Ant> colony = new ArrayList<>(config.antCount());
            boolean improvedThisIteration = false;

            for (int antIndex = 0;
                    antIndex < config.antCount() && !searchLimit.timeReached(start);
                    antIndex++) {
                int[] sequence = constructPermutation(
                        pheromone, desirability,
                        config.pheromoneAlpha(), config.heuristicBeta(), random);
                constructedPermutations++;
                Long packedArea = evaluator.evaluate(sequence);
                if (packedArea == null) break;
                colony.add(new Ant(sequence, packedArea));

                int comparison = Long.compare(packedArea, bestArea);
                if (comparison > 0
                        || (comparison == 0
                        && compareLexicographically(sequence, bestSequence) < 0)) {
                    bestSequence = sequence.clone();
                    if (comparison > 0) {
                        bestArea = packedArea;
                        bestImprovements++;
                        improvedThisIteration = true;
                    }
                }
            }

            if (colony.isEmpty()) break;
            completedIterations++;
            colony.sort(BEST_FIRST);
            evaporateAndClamp(
                    pheromone, config.evaporationRate(),
                    minimumPheromone, maximumPheromone);

            int eliteCount = Math.min(config.eliteAnts(), colony.size());
            for (int rank = 0; rank < eliteCount; rank++) {
                Ant ant = colony.get(rank);
                double quality = (double) ant.packedArea() / instance.binArea();
                deposit(pheromone, ant.sequence(),
                        config.depositStrength() * quality / (rank + 1), maximumPheromone);
                pheromoneDeposits++;
            }
            double globalQuality = (double) bestArea / instance.binArea();
            deposit(pheromone, bestSequence,
                    config.depositStrength() * globalQuality * GLOBAL_BEST_WEIGHT,
                    maximumPheromone);
            pheromoneDeposits++;

            if (improvedThisIteration) noImprovementIterations = 0;
            else noImprovementIterations++;
            trace.add(new ConvergencePoint(
                    completedIterations, evaluator.evaluations, bestArea));
        }

        Solution verifiedBest = evaluator.solve(bestSequence);
        lastStatistics = new AntColonyStatistics(
                completedIterations,
                config.antCount(),
                constructedPermutations,
                evaluator.evaluations,
                evaluator.cacheHits,
                pheromoneDeposits,
                bestImprovements,
                noImprovementIterations);
        lastConvergenceTrace = List.copyOf(trace);
        return new Solution(name(), verifiedBest.placements(), System.nanoTime() - start);
    }

    private static double[] desirability(List<Item> items) {
        long maximumArea = items.stream().mapToLong(Item::area).max().orElse(1L);
        double[] values = new double[items.size()];
        for (int index = 0; index < items.size(); index++) {
            // 大零件通常更值得早放；开平方根避免尺寸差异完全压过信息素。
            values[index] = Math.max(1.0e-12,
                    Math.sqrt((double) items.get(index).area() / maximumArea));
        }
        return values;
    }

    static int[] constructPermutation(
            double[][] pheromone,
            double[] desirability,
            double alpha,
            double beta,
            Random random) {
        int itemCount = desirability.length;
        if (pheromone.length != itemCount + 1) {
            throw new IllegalArgumentException("信息素矩阵必须包含一行起点信息素");
        }
        boolean[] used = new boolean[itemCount];
        int[] sequence = new int[itemCount];
        int previous = itemCount;

        for (int position = 0; position < itemCount; position++) {
            double maximumLogWeight = Double.NEGATIVE_INFINITY;
            for (int candidate = 0; candidate < itemCount; candidate++) {
                if (used[candidate]) continue;
                double logWeight = alpha * Math.log(pheromone[previous][candidate])
                        + beta * Math.log(desirability[candidate]);
                maximumLogWeight = Math.max(maximumLogWeight, logWeight);
            }

            double total = 0.0;
            double[] weights = new double[itemCount];
            for (int candidate = 0; candidate < itemCount; candidate++) {
                if (used[candidate]) continue;
                double logWeight = alpha * Math.log(pheromone[previous][candidate])
                        + beta * Math.log(desirability[candidate]);
                weights[candidate] = Math.exp(logWeight - maximumLogWeight);
                total += weights[candidate];
            }

            int selected = -1;
            double target = random.nextDouble() * total;
            double cumulative = 0.0;
            for (int candidate = 0; candidate < itemCount; candidate++) {
                if (used[candidate]) continue;
                cumulative += weights[candidate];
                if (target < cumulative) {
                    selected = candidate;
                    break;
                }
            }
            if (selected < 0) {
                for (int candidate = itemCount - 1; candidate >= 0; candidate--) {
                    if (!used[candidate]) {
                        selected = candidate;
                        break;
                    }
                }
            }
            sequence[position] = selected;
            used[selected] = true;
            previous = selected;
        }
        return sequence;
    }

    static void evaporateAndClamp(
            double[][] pheromone,
            double evaporationRate,
            double minimum,
            double maximum) {
        double retained = 1.0 - evaporationRate;
        for (double[] row : pheromone) {
            for (int column = 0; column < row.length; column++) {
                row[column] = Math.max(minimum, Math.min(maximum, row[column] * retained));
            }
        }
    }

    static void deposit(
            double[][] pheromone,
            int[] sequence,
            double amount,
            double maximum) {
        if (sequence.length == 0 || amount <= 0.0) return;
        int previous = sequence.length;
        for (int current : sequence) {
            pheromone[previous][current] = Math.min(
                    maximum, pheromone[previous][current] + amount);
            previous = current;
        }
    }

    private static int compareLexicographically(int[] first, int[] second) {
        for (int index = 0; index < Math.min(first.length, second.length); index++) {
            int comparison = Integer.compare(first[index], second[index]);
            if (comparison != 0) return comparison;
        }
        return Integer.compare(first.length, second.length);
    }

    private record Ant(int[] sequence, long packedArea) {}

    private static final class FitnessEvaluator {
        private final List<Item> items;
        private final PackingDecoder decoder;
        private final SearchLimit limit;
        private final long startNanos;
        private final DecodingCache cache;
        private long evaluations;
        private long cacheHits;

        private FitnessEvaluator(
                Instance instance,
                List<Item> items,
                PackingDecoder decoder,
                SearchLimit limit,
                long startNanos) {
            this.items = items;
            this.decoder = decoder;
            this.limit = limit;
            this.startNanos = startNanos;
            this.cache = new DecodingCache(instance, decoder);
        }

        private long evaluateInitial(int[] sequence) {
            long area = decode(sequence);
            cache.put(toItemSequence(sequence), area);
            evaluations++;
            return area;
        }

        private Long evaluate(int[] sequence) {
            int[] ids = toItemSequence(sequence);
            Long cached = cache.get(ids);
            if (cached != null) {
                cacheHits++;
                return cached;
            }
            if (evaluations >= limit.maxEvaluations() || limit.timeReached(startNanos)) {
                return null;
            }
            long area = decode(sequence);
            cache.put(ids, area);
            evaluations++;
            return area;
        }

        private long decode(int[] indices) {
            return cache.areaOf(toItemSequence(indices));
        }

        /** 收尾时解码历史最优排列，拿到完整布局。 */
        private Solution solve(int[] indices) {
            return cache.solve(toItemSequence(indices));
        }

        /**
         * 蚁群内部使用零件在实例中的下标而非零件编号，这里转换成编号后交给共享缓存，
         * 从而与其它搜索算法共用同一套缓存键。
         */
        private int[] toItemSequence(int[] indices) {
            int[] ids = new int[indices.length];
            for (int i = 0; i < indices.length; i++) {
                int index = indices[i];
                if (index < 0 || index >= items.size()) {
                    throw new IllegalStateException("蚁群排列包含非法零件下标：" + index);
                }
                ids[i] = items.get(index).id();
            }
            return ids;
        }
    }
}
