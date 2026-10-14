package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 使用零件优先级排列和可注入排样解码器的经典模拟退火。 */
public final class SimulatedAnnealingSolver implements PackingAlgorithm, ConvergenceTraceProvider {
    private static final double FALLBACK_INITIAL_TEMPERATURE = 0.01;

    private final boolean rotationAllowed;
    private final ItemOrder initialOrder;
    private final SimulatedAnnealingConfig config;
    private final SearchLimit searchLimit;
    private final PackingDecoder decoder;
    private SimulatedAnnealingStatistics lastStatistics = SimulatedAnnealingStatistics.empty();
    private List<ConvergencePoint> lastConvergenceTrace = List.of();

    public SimulatedAnnealingSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            SimulatedAnnealingConfig config) {
        this(rotationAllowed, initialOrder, config, SearchLimit.unlimited(),
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public SimulatedAnnealingSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            SimulatedAnnealingConfig config,
            SearchLimit searchLimit) {
        this(rotationAllowed, initialOrder, config, searchLimit,
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public SimulatedAnnealingSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            SimulatedAnnealingConfig config,
            SearchLimit searchLimit,
            PackingDecoder decoder) {
        this.rotationAllowed = rotationAllowed;
        this.initialOrder = initialOrder;
        this.config = config;
        this.searchLimit = searchLimit;
        this.decoder = java.util.Objects.requireNonNull(decoder, "排样解码器不能为空");
    }

    @Override
    public String name() {
        return "Simulated Annealing(decoder=" + decoder.name() + ", initial=" + initialOrder
                + ", rotation=" + rotationAllowed + ", seed=" + config.seed() + ")";
    }

    public SimulatedAnnealingStatistics lastStatistics() {
        return lastStatistics;
    }

    @Override
    public List<ConvergencePoint> lastConvergenceTrace() {
        return lastConvergenceTrace;
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Item> initialItems = ItemOrdering.sort(instance.items(), initialOrder);
        int[] currentSequence = initialItems.stream().mapToInt(Item::id).toArray();

        if (currentSequence.length < 2) {
            Solution only = decoder.solveInPriorityOrder(instance, initialItems);
            lastStatistics = SimulatedAnnealingStatistics.empty();
            lastConvergenceTrace = List.of(new ConvergencePoint(0, 0, only.packedArea()));
            return new Solution(name(), only.placements(), System.nanoTime() - start);
        }

        // 重复排列的复用集中在这里；预算与计数口径仍由下面的循环条件决定。
        // 容量默认与既有行为一致，可用 PACKING_CACHE_CAPACITY 做「无缓存/固定缓存」对照实验。
        DecodingCache cache = new DecodingCache(
                instance, decoder, DecodingCacheCapacity.configured());
        Random random = new Random(config.seed());
        long currentArea = cache.areaOf(currentSequence);
        // currentSequence 只在接受候选时整体换新，从不就地改写，因此可以直接共享引用。
        int[] bestSequence = currentSequence;
        long bestArea = currentArea;

        List<ConvergencePoint> trace = new ArrayList<>();
        trace.add(new ConvergencePoint(0, 0, bestArea));

        // 候选排列的复用缓冲区：每个 attempt 原本都要克隆整个排列，这是每评价一次的最大一笔
        // 分配。改为把候选写进这块固定缓冲，只有被接受时才复制出生效的排列。
        int[] candidateScratch = new int[currentSequence.length];
        TemperatureEstimate estimate = estimateInitialTemperature(
                instance, currentSequence, candidateScratch, currentArea, cache, random, start);
        double initialTemperature = Math.max(
                estimate.temperature(), config.minimumTemperature() / config.coolingRate());
        double temperature = initialTemperature;
        int attempts = config.attemptsFor(currentSequence.length);

        long evaluated = 0;
        long acceptedImproving = 0;
        long acceptedEqual = 0;
        long acceptedWorse = 0;
        long rejected = 0;
        int bestImprovements = 0;
        int noImprovementLevels = 0;
        int completedLevels = 0;

        while (completedLevels < config.maxTemperatureLevels()
                && temperature >= config.minimumTemperature()
                && noImprovementLevels < config.maxNoImprovementLevels()
                && bestArea < instance.binArea()
                && !searchLimit.timeReached(start)
                && estimate.evaluations() + evaluated < searchLimit.maxEvaluations()) {

            boolean improvedBestThisLevel = false;
            completedLevels++;

            for (int attempt = 0;
                    attempt < attempts
                            && bestArea < instance.binArea()
                            && !searchLimit.timeReached(start)
                            && estimate.evaluations() + evaluated < searchLimit.maxEvaluations();
                    attempt++) {
                randomSwapInto(currentSequence, candidateScratch, random);
                long candidateArea = cache.areaOf(candidateScratch);
                evaluated++;

                long areaDifference = candidateArea - currentArea;
                double normalizedDifference = (double) areaDifference / instance.binArea();
                boolean accept = areaDifference >= 0
                        || random.nextDouble() < acceptanceProbability(normalizedDifference, temperature);

                if (!accept) {
                    rejected++;
                    continue;
                }

                if (areaDifference > 0) acceptedImproving++;
                else if (areaDifference == 0) acceptedEqual++;
                else acceptedWorse++;

                // 只有接受的候选才复制出生效排列。
                currentSequence = candidateScratch.clone();
                currentArea = candidateArea;

                if (currentArea > bestArea) {
                    // currentSequence 从不就地改写，这里直接共享引用即可（原实现是再克隆一次）。
                    bestSequence = currentSequence;
                    bestArea = currentArea;
                    bestImprovements++;
                    improvedBestThisLevel = true;
                }
            }

            if (improvedBestThisLevel) noImprovementLevels = 0;
            else noImprovementLevels++;
            trace.add(new ConvergencePoint(
                    completedLevels, estimate.evaluations() + evaluated, bestArea));
            temperature *= config.coolingRate();
        }

        Solution verifiedBest = cache.solve(bestSequence);
        lastStatistics = new SimulatedAnnealingStatistics(
                completedLevels,
                estimate.evaluations(),
                evaluated,
                acceptedImproving,
                acceptedEqual,
                acceptedWorse,
                rejected,
                bestImprovements,
                initialTemperature,
                temperature,
                cache.statistics());
        lastConvergenceTrace = List.copyOf(trace);
        return new Solution(name(), verifiedBest.placements(), System.nanoTime() - start);
    }

    private TemperatureEstimate estimateInitialTemperature(
            Instance instance,
            int[] initialSequence,
            int[] candidateScratch,
            long initialArea,
            DecodingCache cache,
            Random random,
            long startNanos) {

        double totalLoss = 0.0;
        int worseningSamples = 0;
        int evaluations = 0;
        for (int sample = 0;
                sample < config.initialTemperatureSamples()
                        && evaluations < searchLimit.maxEvaluations()
                        && !searchLimit.timeReached(startNanos);
                sample++) {
            randomSwapInto(initialSequence, candidateScratch, random);
            long candidateArea = cache.areaOf(candidateScratch);
            evaluations++;
            long areaLoss = initialArea - candidateArea;
            if (areaLoss > 0) {
                totalLoss += (double) areaLoss / instance.binArea();
                worseningSamples++;
            }
        }

        double temperature = worseningSamples == 0
                ? FALLBACK_INITIAL_TEMPERATURE
                : -(totalLoss / worseningSamples)
                        / Math.log(config.targetInitialAcceptanceProbability());
        return new TemperatureEstimate(temperature, evaluations);
    }

    static double acceptanceProbability(double normalizedDifference, double temperature) {
        if (normalizedDifference >= 0.0) return 1.0;
        if (!(temperature > 0.0)) return 0.0;
        return Math.exp(normalizedDifference / temperature);
    }

    /**
     * 把 {@code sequence} 复制进复用的 {@code candidate} 缓冲，再对其做一次随机交换。
     *
     * <p>两次 {@code nextInt} 抽取的顺序与次数保持原样，因此随机数流逐位不变。
     * 之所以复制整表再改，而不是就地交换后回滚：本类的随机交换没有「撤销」路径
     * （候选被拒时无操作），整表复制是最省事且绝对正确的写法，且比原先每次新建数组少一次分配。
     */
    private static void randomSwapInto(int[] sequence, int[] candidate, Random random) {
        System.arraycopy(sequence, 0, candidate, 0, sequence.length);
        int first = random.nextInt(candidate.length);
        int second = random.nextInt(candidate.length - 1);
        if (second >= first) second++;
        int temporary = candidate[first];
        candidate[first] = candidate[second];
        candidate[second] = temporary;
    }

    private record TemperatureEstimate(double temperature, long evaluations) {}
}
