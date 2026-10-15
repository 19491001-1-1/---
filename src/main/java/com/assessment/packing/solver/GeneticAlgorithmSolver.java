package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 排列编码、OX交叉和可注入排样解码的遗传算法。 */
public class GeneticAlgorithmSolver implements PackingAlgorithm, ConvergenceTraceProvider {
    private static final Comparator<Individual> BEST_FIRST = (first, second) -> {
        int areaComparison = Long.compare(second.packedArea, first.packedArea);
        return areaComparison != 0
                ? areaComparison
                : compareLexicographically(first.sequence, second.sequence);
    };

    private final boolean rotationAllowed;
    private final ItemOrder initialOrder;
    private final GeneticAlgorithmConfig config;
    private final HybridLocalSearchMode hybridMode;
    private final HybridGeneticAlgorithmConfig hybridConfig;
    private final AdaptiveGeneticAlgorithmConfig adaptiveConfig;
    private final SearchLimit searchLimit;
    private final PackingDecoder decoder;
    private GeneticAlgorithmStatistics lastStatistics = GeneticAlgorithmStatistics.empty();
    private HybridGeneticAlgorithmStatistics lastHybridStatistics = HybridGeneticAlgorithmStatistics.empty();
    private AdaptiveGeneticAlgorithmStatistics lastAdaptiveStatistics = AdaptiveGeneticAlgorithmStatistics.empty();
    private List<Long> lastBestAreaHistory = List.of();
    private List<ConvergencePoint> lastConvergenceTrace = List.of();

    public GeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config) {
        this(rotationAllowed, initialOrder, config, null, null, null, SearchLimit.unlimited(),
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public GeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            HybridLocalSearchMode hybridMode,
            HybridGeneticAlgorithmConfig hybridConfig) {
        this(rotationAllowed, initialOrder, config, hybridMode, hybridConfig, null,
                SearchLimit.unlimited(),
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public GeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            HybridLocalSearchMode hybridMode,
            HybridGeneticAlgorithmConfig hybridConfig,
            SearchLimit searchLimit) {
        this(rotationAllowed, initialOrder, config, hybridMode, hybridConfig, null, searchLimit,
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public GeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            HybridLocalSearchMode hybridMode,
            HybridGeneticAlgorithmConfig hybridConfig,
            SearchLimit searchLimit,
            PackingDecoder decoder) {
        this(rotationAllowed, initialOrder, config, hybridMode, hybridConfig, null,
                searchLimit, decoder);
    }

    protected GeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            AdaptiveGeneticAlgorithmConfig adaptiveConfig,
            SearchLimit searchLimit) {
        this(rotationAllowed, initialOrder, config, null, null, adaptiveConfig, searchLimit,
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    protected GeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            AdaptiveGeneticAlgorithmConfig adaptiveConfig,
            SearchLimit searchLimit,
            PackingDecoder decoder) {
        this(rotationAllowed, initialOrder, config, null, null, adaptiveConfig, searchLimit, decoder);
    }

    private GeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            HybridLocalSearchMode hybridMode,
            HybridGeneticAlgorithmConfig hybridConfig,
            AdaptiveGeneticAlgorithmConfig adaptiveConfig,
            SearchLimit searchLimit,
            PackingDecoder decoder) {
        this.rotationAllowed = rotationAllowed;
        this.initialOrder = initialOrder;
        this.config = config;
        if ((hybridMode == null) != (hybridConfig == null)) {
            throw new IllegalArgumentException("混合模式和混合参数必须同时提供");
        }
        this.hybridMode = hybridMode;
        this.hybridConfig = hybridConfig;
        this.adaptiveConfig = adaptiveConfig;
        this.searchLimit = searchLimit;
        this.decoder = java.util.Objects.requireNonNull(decoder, "排样解码器不能为空");
    }

    @Override
    public String name() {
        String algorithm = adaptiveConfig != null
                ? "Adaptive Genetic Algorithm"
                : hybridMode == null
                ? "Genetic Algorithm"
                : "Memetic Algorithm(GA+" + hybridMode + ")";
        return algorithm + "(decoder=" + decoder.name() + ", initial=" + initialOrder
                + ", mutation=" + config.mutationMode()
                + ", rotation=" + rotationAllowed + ", seed=" + config.seed() + ")";
    }

    public GeneticAlgorithmStatistics lastStatistics() {
        return lastStatistics;
    }

    public List<Long> lastBestAreaHistory() {
        return lastBestAreaHistory;
    }

    @Override
    public List<ConvergencePoint> lastConvergenceTrace() {
        return lastConvergenceTrace;
    }

    public boolean hybridEnabled() {
        return hybridMode != null;
    }

    public HybridGeneticAlgorithmStatistics lastHybridStatistics() {
        return lastHybridStatistics;
    }

    public boolean adaptiveEnabled() {
        return adaptiveConfig != null;
    }

    public AdaptiveGeneticAlgorithmStatistics lastAdaptiveStatistics() {
        return lastAdaptiveStatistics;
    }

    @Override
    public Solution solve(Instance instance) {
        long start = System.nanoTime();
        List<Item> orderedItems = ItemOrdering.sort(instance.items(), initialOrder);
        int[] initialSequence = orderedItems.stream().mapToInt(Item::id).toArray();

        if (initialSequence.length < 2) {
            Solution only = decoder.solveInPriorityOrder(instance, orderedItems);
            lastStatistics = GeneticAlgorithmStatistics.empty();
            lastHybridStatistics = HybridGeneticAlgorithmStatistics.empty();
            lastAdaptiveStatistics = AdaptiveGeneticAlgorithmStatistics.empty();
            lastBestAreaHistory = List.of(only.packedArea());
            lastConvergenceTrace = List.of(new ConvergencePoint(0, 0, only.packedArea()));
            return new Solution(name(), only.placements(), System.nanoTime() - start);
        }

        FitnessEvaluator evaluator = new FitnessEvaluator(
                instance, decoder, searchLimit, start);
        Random random = new Random(config.seed());
        Random localRandom = new Random(config.seed() ^ 0x6A09E667F3BCC909L
                ^ (hybridMode == null ? 0L : hybridMode.ordinal()));
        int effectivePopulationSize = Math.min(
                config.populationSize(), distinctPermutationCountCapped(initialSequence.length, config.populationSize()));

        List<int[]> initialSequences = createInitialPopulation(
                instance, initialSequence, effectivePopulationSize, random);
        List<Individual> population = new ArrayList<>(effectivePopulationSize);
        for (int[] sequence : initialSequences) {
            population.add(new Individual(sequence, evaluator.packedArea(sequence)));
        }
        population.sort(BEST_FIRST);

        Individual historicalBest = population.get(0).copy();
        List<Long> bestAreaHistory = new ArrayList<>();
        bestAreaHistory.add(historicalBest.packedArea);
        List<ConvergencePoint> trace = new ArrayList<>();
        trace.add(new ConvergencePoint(0, evaluator.evaluations, historicalBest.packedArea));

        long crossoverEvents = 0;
        long mutationEvents = 0;
        long duplicateRejections = 0;
        int bestImprovements = 0;
        int noImprovementGenerations = 0;
        int completedGenerations = 0;
        Set<SequenceKey> locallyOptimized = new HashSet<>();
        HybridCounters hybridCounters = new HybridCounters();
        AdaptiveCounters adaptiveCounters = new AdaptiveCounters();

        while (completedGenerations < config.maxGenerations()
                && noImprovementGenerations < config.maxNoImprovementGenerations()
                && historicalBest.packedArea < instance.binArea()
                && evaluator.evaluations < searchLimit.maxEvaluations()
                && !searchLimit.timeReached(start)
                && !SearchLimit.cancelled()) {
            completedGenerations++;
            population.sort(BEST_FIRST);
            PopulationFitness fitness = PopulationFitness.of(population);

            List<Individual> nextPopulation = new ArrayList<>(effectivePopulationSize);
            Set<SequenceKey> nextKeys = new HashSet<>();
            int eliteCount = Math.min(config.eliteCount(), effectivePopulationSize);
            for (int i = 0; i < eliteCount; i++) {
                Individual elite = population.get(i).copy();
                nextPopulation.add(elite);
                nextKeys.add(new SequenceKey(elite.sequence));
            }

            int generationAttempts = 0;
            int attemptLimit = Math.max(1_000, effectivePopulationSize * config.duplicateRetries() * 20);
            generationFill:
            while (nextPopulation.size() < effectivePopulationSize && generationAttempts < attemptLimit) {
                generationAttempts++;
                Individual firstParent = tournamentSelect(population, random);
                Individual secondParent = tournamentSelect(population, random);
                int[][] children;
                double crossoverRate = adaptiveConfig == null
                        ? config.crossoverRate()
                        : adaptiveRate(
                                Math.max(firstParent.packedArea, secondParent.packedArea),
                                fitness,
                                adaptiveConfig.minimumCrossoverRate(),
                                adaptiveConfig.maximumCrossoverRate());
                adaptiveCounters.recordCrossover(crossoverRate);
                if (random.nextDouble() < crossoverRate) {
                    int firstCut = random.nextInt(initialSequence.length);
                    int secondCut = random.nextInt(initialSequence.length - 1);
                    if (secondCut >= firstCut) secondCut++;
                    int startCut = Math.min(firstCut, secondCut);
                    int endCut = Math.max(firstCut, secondCut);
                    children = new int[][] {
                            orderCrossover(firstParent.sequence, secondParent.sequence, startCut, endCut),
                            orderCrossover(secondParent.sequence, firstParent.sequence, startCut, endCut)
                    };
                    crossoverEvents++;
                } else {
                    children = new int[][] {
                            firstParent.sequence.clone(), secondParent.sequence.clone()
                    };
                }

                for (int childIndex = 0; childIndex < children.length; childIndex++) {
                    int[] child = children[childIndex];
                    long referenceFitness = childIndex == 0
                            ? firstParent.packedArea : secondParent.packedArea;
                    double mutationRate = adaptiveConfig == null
                            ? config.mutationRate()
                            : adaptiveRate(
                                    referenceFitness,
                                    fitness,
                                    adaptiveConfig.minimumMutationRate(),
                                    adaptiveConfig.maximumMutationRate());
                    adaptiveCounters.recordMutation(mutationRate);
                    if (random.nextDouble() < mutationRate) {
                        mutate(child, config.mutationMode(), random);
                        mutationEvents++;
                    }

                    int retries = 0;
                    SequenceKey key = new SequenceKey(child);
                    while (nextKeys.contains(key) && retries < config.duplicateRetries()) {
                        duplicateRejections++;
                        mutate(child, config.mutationMode(), random);
                        mutationEvents++;
                        retries++;
                        key = new SequenceKey(child);
                    }
                    if (nextKeys.contains(key)) continue;

                    long childArea = evaluator.packedArea(child);
                    if (evaluator.limitReached) break generationFill;
                    nextKeys.add(key);
                    nextPopulation.add(new Individual(child, childArea));
                    if (nextPopulation.size() == effectivePopulationSize) break;
                }
            }

            // 极小排列空间或种群高度收敛时，用尚未出现的字典序排列可靠补齐。
            if (!evaluator.limitReached && nextPopulation.size() < effectivePopulationSize) {
                int[] candidate = initialSequence.clone();
                Arrays.sort(candidate);
                do {
                    SequenceKey key = new SequenceKey(candidate);
                    if (nextKeys.add(key)) {
                        int[] added = candidate.clone();
                        nextPopulation.add(new Individual(added, evaluator.packedArea(added)));
                    }
                } while (nextPopulation.size() < effectivePopulationSize && nextPermutation(candidate));
            }

            if (hybridMode != null
                    && !evaluator.limitReached
                    && completedGenerations % hybridConfig.localSearchInterval() == 0) {
                applyLocalSearch(
                        nextPopulation,
                        nextKeys,
                        evaluator,
                        localRandom,
                        locallyOptimized,
                        hybridCounters,
                        instance.binArea());
            }

            population = nextPopulation;
            population.sort(BEST_FIRST);
            Individual generationBest = population.get(0);
            if (generationBest.packedArea > historicalBest.packedArea) {
                historicalBest = generationBest.copy();
                bestImprovements++;
                noImprovementGenerations = 0;
            } else {
                noImprovementGenerations++;
            }
            bestAreaHistory.add(historicalBest.packedArea);
            trace.add(new ConvergencePoint(
                    completedGenerations, evaluator.evaluations, historicalBest.packedArea));
        }

        Solution verifiedBest = evaluator.solve(historicalBest.sequence);
        lastStatistics = new GeneticAlgorithmStatistics(
                completedGenerations,
                effectivePopulationSize,
                evaluator.evaluations,
                evaluator.cacheHits,
                crossoverEvents,
                mutationEvents,
                duplicateRejections,
                bestImprovements,
                evaluator.cache.statistics());
        lastHybridStatistics = hybridCounters.toStatistics();
        lastAdaptiveStatistics = adaptiveCounters.toStatistics();
        lastBestAreaHistory = List.copyOf(bestAreaHistory);
        lastConvergenceTrace = List.copyOf(trace);
        return new Solution(name(), verifiedBest.placements(), System.nanoTime() - start);
    }

    /** 适应度不高于均值时取最大概率；越接近本代最好个体，概率线性降到最小值。 */
    private static double adaptiveRate(
            long individualFitness, PopulationFitness population,
            double minimum, double maximum) {
        if (population.maximum <= population.average || individualFitness <= population.average) {
            return maximum;
        }
        double quality = (individualFitness - population.average)
                / (population.maximum - population.average);
        return maximum - Math.min(1.0, quality) * (maximum - minimum);
    }

    private void applyLocalSearch(
            List<Individual> population,
            Set<SequenceKey> populationKeys,
            FitnessEvaluator evaluator,
            Random random,
            Set<SequenceKey> locallyOptimized,
            HybridCounters counters,
            long binArea) {
        population.sort(BEST_FIRST);
        int targetCalls = Math.min(
                hybridConfig.maxLocalIndividuals(),
                Math.max(1, (int) Math.ceil(population.size() * hybridConfig.localSearchFraction())));
        int calls = 0;

        for (int index = 0; index < population.size() && calls < targetCalls; index++) {
            Individual original = population.get(index);
            SequenceKey originalKey = new SequenceKey(original.sequence);
            if (!locallyOptimized.add(originalKey)) {
                counters.skippedPreviouslyOptimized++;
                continue;
            }

            counters.localSearchCalls++;
            calls++;
            LocalSearchResult result = switch (hybridMode) {
                case ANNEALING -> annealingImprove(original, evaluator, random, counters, binArea);
                case TABU_MOVE -> tabuImprove(original, evaluator, random, counters);
            };
            if (result.packedArea <= original.packedArea) continue;

            SequenceKey improvedKey = new SequenceKey(result.sequence);
            locallyOptimized.add(improvedKey);
            if (populationKeys.contains(improvedKey)) {
                counters.duplicateLocalResults++;
                continue;
            }

            populationKeys.remove(originalKey);
            populationKeys.add(improvedKey);
            population.set(index, new Individual(result.sequence, result.packedArea));
            counters.localImprovements++;
            counters.totalAreaGain += result.packedArea - original.packedArea;
        }
    }

    private LocalSearchResult annealingImprove(
            Individual initial,
            FitnessEvaluator evaluator,
            Random random,
            HybridCounters counters,
            long binArea) {
        int budget = hybridConfig.localEvaluationBudget();
        int[] currentSequence = initial.sequence.clone();
        long currentArea = initial.packedArea;
        int[] bestSequence = currentSequence.clone();
        long bestArea = currentArea;

        int sampleCount = Math.min(
                hybridConfig.localNeighborhoodSize(), Math.max(1, budget / 5));
        double totalLoss = 0.0;
        int worseningSamples = 0;
        int evaluated = 0;
        for (; evaluated < sampleCount; evaluated++) {
            int[] candidate = randomSwap(currentSequence, random);
            long candidateArea = evaluator.packedArea(candidate);
            if (evaluator.limitReached) break;
            counters.localCandidateEvaluations++;
            long loss = currentArea - candidateArea;
            if (loss > 0) {
                totalLoss += (double) loss / binArea;
                worseningSamples++;
            }
        }

        double temperature = worseningSamples == 0
                ? 0.005
                : -(totalLoss / worseningSamples)
                        / Math.log(hybridConfig.annealingInitialAcceptanceProbability());
        temperature = Math.max(temperature, 1.0e-8);
        int noImprovementLevels = 0;

        while (evaluated < budget
                && noImprovementLevels < hybridConfig.localNoImprovementLimit()
                && !evaluator.limitReached) {
            boolean improvedThisLevel = false;
            int levelAttempts = Math.min(
                    hybridConfig.localNeighborhoodSize(), budget - evaluated);
            for (int attempt = 0; attempt < levelAttempts; attempt++) {
                int[] candidate = randomSwap(currentSequence, random);
                long candidateArea = evaluator.packedArea(candidate);
                if (evaluator.limitReached) break;
                counters.localCandidateEvaluations++;
                evaluated++;

                long difference = candidateArea - currentArea;
                double normalizedDifference = (double) difference / binArea;
                boolean accept = difference >= 0
                        || random.nextDouble() < SimulatedAnnealingSolver.acceptanceProbability(
                                normalizedDifference, temperature);
                if (!accept) continue;
                if (difference < 0) counters.acceptedWorseMoves++;
                currentSequence = candidate;
                currentArea = candidateArea;
                if (currentArea > bestArea) {
                    bestSequence = currentSequence.clone();
                    bestArea = currentArea;
                    improvedThisLevel = true;
                }
            }
            if (improvedThisLevel) noImprovementLevels = 0;
            else noImprovementLevels++;
            temperature *= hybridConfig.annealingCoolingRate();
        }
        return new LocalSearchResult(bestSequence, bestArea);
    }

    private LocalSearchResult tabuImprove(
            Individual initial,
            FitnessEvaluator evaluator,
            Random random,
            HybridCounters counters) {
        int[] currentSequence = initial.sequence.clone();
        long currentArea = initial.packedArea;
        int[] bestSequence = currentSequence.clone();
        long bestArea = currentArea;
        List<Swap> swaps = allSwaps(currentSequence.length);
        Map<Long, Integer> tabuUntil = new HashMap<>();
        int evaluated = 0;
        int noImprovement = 0;
        int iteration = 0;

        while (evaluated < hybridConfig.localEvaluationBudget()
                && noImprovement < hybridConfig.localNoImprovementLimit()
                && !evaluator.limitReached) {
            Collections.shuffle(swaps, random);
            int limit = Math.min(
                    Math.min(hybridConfig.localNeighborhoodSize(), swaps.size()),
                    hybridConfig.localEvaluationBudget() - evaluated);
            TabuCandidate selected = null;

            for (int i = 0; i < limit; i++) {
                Swap swap = swaps.get(i);
                int itemA = currentSequence[swap.firstPosition];
                int itemB = currentSequence[swap.secondPosition];
                int[] candidateSequence = currentSequence.clone();
                swapMutation(candidateSequence, swap.firstPosition, swap.secondPosition);
                long candidateArea = evaluator.packedArea(candidateSequence);
                if (evaluator.limitReached) break;
                counters.localCandidateEvaluations++;
                evaluated++;

                boolean tabu = tabuUntil.getOrDefault(unorderedPair(itemA, itemB), 0) > iteration;
                boolean aspiration = tabu && candidateArea > bestArea;
                if (tabu && !aspiration) continue;
                TabuCandidate candidate = new TabuCandidate(
                        candidateSequence, candidateArea, itemA, itemB, aspiration);
                if (selected == null
                        || candidateArea > selected.packedArea
                        || candidateArea == selected.packedArea
                                && compareLexicographically(candidateSequence, selected.sequence) < 0) {
                    selected = candidate;
                }
            }

            iteration++;
            if (selected == null) {
                noImprovement++;
                continue;
            }
            if (selected.packedArea < currentArea) counters.acceptedWorseMoves++;
            if (selected.aspiration) counters.aspirationAcceptances++;
            tabuUntil.put(
                    unorderedPair(selected.itemA, selected.itemB),
                    iteration + hybridConfig.tabuTenure());
            currentSequence = selected.sequence;
            currentArea = selected.packedArea;
            if (currentArea > bestArea) {
                bestSequence = currentSequence.clone();
                bestArea = currentArea;
                noImprovement = 0;
            } else {
                noImprovement++;
            }
        }
        return new LocalSearchResult(bestSequence, bestArea);
    }

    private static int[] randomSwap(int[] sequence, Random random) {
        int[] candidate = sequence.clone();
        int first = random.nextInt(candidate.length);
        int second = random.nextInt(candidate.length - 1);
        if (second >= first) second++;
        swapMutation(candidate, first, second);
        return candidate;
    }

    private static List<Swap> allSwaps(int length) {
        List<Swap> swaps = new ArrayList<>(length * (length - 1) / 2);
        for (int first = 0; first < length; first++) {
            for (int second = first + 1; second < length; second++) {
                swaps.add(new Swap(first, second));
            }
        }
        return swaps;
    }

    private static long unorderedPair(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ((long) low << 32) | (high & 0xffffffffL);
    }

    private List<int[]> createInitialPopulation(
            Instance instance,
            int[] initialSequence,
            int targetSize,
            Random random) {
        LinkedHashSet<SequenceKey> keys = new LinkedHashSet<>();
        addOrdering(keys, ItemOrdering.sort(instance.items(), initialOrder));
        addOrdering(keys, ItemOrdering.sort(instance.items(), ItemOrder.AREA_DESC));
        addOrdering(keys, ItemOrdering.sort(instance.items(), ItemOrder.HEIGHT_DESC));
        addOrdering(keys, ItemOrdering.sort(instance.items(), ItemOrder.INPUT));

        int randomAttempts = 0;
        int randomAttemptLimit = Math.max(1_000, targetSize * config.duplicateRetries() * 20);
        while (keys.size() < targetSize && randomAttempts++ < randomAttemptLimit) {
            int[] shuffled = initialSequence.clone();
            shuffle(shuffled, random);
            keys.add(new SequenceKey(shuffled));
        }

        if (keys.size() < targetSize) {
            int[] candidate = initialSequence.clone();
            Arrays.sort(candidate);
            do {
                keys.add(new SequenceKey(candidate));
            } while (keys.size() < targetSize && nextPermutation(candidate));
        }

        List<int[]> population = new ArrayList<>(targetSize);
        for (SequenceKey key : keys) {
            if (population.size() == targetSize) break;
            population.add(key.values.clone());
        }
        return population;
    }

    private static void addOrdering(Set<SequenceKey> keys, List<Item> items) {
        keys.add(new SequenceKey(items.stream().mapToInt(Item::id).toArray()));
    }

    private Individual tournamentSelect(List<Individual> population, Random random) {
        Individual best = null;
        int size = Math.min(config.tournamentSize(), population.size());
        for (int i = 0; i < size; i++) {
            Individual candidate = population.get(random.nextInt(population.size()));
            if (best == null || BEST_FIRST.compare(candidate, best) < 0) best = candidate;
        }
        return best;
    }

    static int[] orderCrossover(int[] segmentParent, int[] orderParent, int start, int end) {
        if (segmentParent.length != orderParent.length) {
            throw new IllegalArgumentException("父代长度必须相同");
        }
        if (start < 0 || end < start || end >= segmentParent.length) {
            throw new IllegalArgumentException("交叉区间非法");
        }

        int length = segmentParent.length;
        int[] child = new int[length];
        Arrays.fill(child, -1);
        Set<Integer> used = new HashSet<>();
        for (int i = start; i <= end; i++) {
            child[i] = segmentParent[i];
            used.add(segmentParent[i]);
        }

        int write = (end + 1) % length;
        for (int offset = 1; offset <= length; offset++) {
            int value = orderParent[(end + offset) % length];
            if (used.contains(value)) continue;
            while (child[write] != -1) write = (write + 1) % length;
            child[write] = value;
            used.add(value);
        }
        return child;
    }

    private static void mutate(int[] sequence, GeneticMutationMode mode, Random random) {
        GeneticMutationMode selected = mode == GeneticMutationMode.MIXED
                ? GeneticMutationMode.values()[random.nextInt(3)]
                : mode;
        int first = random.nextInt(sequence.length);
        int second = random.nextInt(sequence.length - 1);
        if (second >= first) second++;
        switch (selected) {
            case SWAP -> swapMutation(sequence, first, second);
            case INSERTION -> insertionMutation(sequence, first, second);
            case REVERSAL -> reversalMutation(sequence, first, second);
            case MIXED -> throw new IllegalStateException("MIXED必须先解析为具体变异");
        }
    }

    static void swapMutation(int[] sequence, int first, int second) {
        int temporary = sequence[first];
        sequence[first] = sequence[second];
        sequence[second] = temporary;
    }

    static void insertionMutation(int[] sequence, int from, int to) {
        if (from == to) return;
        int value = sequence[from];
        if (from < to) {
            System.arraycopy(sequence, from + 1, sequence, from, to - from);
        } else {
            System.arraycopy(sequence, to, sequence, to + 1, from - to);
        }
        sequence[to] = value;
    }

    static void reversalMutation(int[] sequence, int first, int second) {
        int left = Math.min(first, second);
        int right = Math.max(first, second);
        while (left < right) swapMutation(sequence, left++, right--);
    }

    private static void shuffle(int[] sequence, Random random) {
        for (int i = sequence.length - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            swapMutation(sequence, i, other);
        }
    }

    private static int distinctPermutationCountCapped(int length, int cap) {
        long count = 1;
        for (int factor = 2; factor <= length && count < cap; factor++) {
            count = Math.min(cap, count * factor);
        }
        return (int) count;
    }

    private static boolean nextPermutation(int[] values) {
        int pivot = values.length - 2;
        while (pivot >= 0 && values[pivot] >= values[pivot + 1]) pivot--;
        if (pivot < 0) return false;
        int successor = values.length - 1;
        while (values[successor] <= values[pivot]) successor--;
        swapMutation(values, pivot, successor);
        for (int left = pivot + 1, right = values.length - 1; left < right; left++, right--) {
            swapMutation(values, left, right);
        }
        return true;
    }

    private static int compareLexicographically(int[] first, int[] second) {
        for (int i = 0; i < first.length; i++) {
            int comparison = Integer.compare(first[i], second[i]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private record Swap(int firstPosition, int secondPosition) {}

    private record LocalSearchResult(int[] sequence, long packedArea) {}

    private record TabuCandidate(
            int[] sequence,
            long packedArea,
            int itemA,
            int itemB,
            boolean aspiration) {}

    private static final class HybridCounters {
        private long localSearchCalls;
        private long localCandidateEvaluations;
        private long localImprovements;
        private long totalAreaGain;
        private long acceptedWorseMoves;
        private long aspirationAcceptances;
        private long duplicateLocalResults;
        private long skippedPreviouslyOptimized;

        private HybridGeneticAlgorithmStatistics toStatistics() {
            return new HybridGeneticAlgorithmStatistics(
                    localSearchCalls,
                    localCandidateEvaluations,
                    localImprovements,
                    totalAreaGain,
                    acceptedWorseMoves,
                    aspirationAcceptances,
                    duplicateLocalResults,
                    skippedPreviouslyOptimized);
        }
    }

    private record PopulationFitness(long maximum, double average) {
        private static PopulationFitness of(List<Individual> population) {
            long maximum = Long.MIN_VALUE;
            double sum = 0.0;
            for (Individual individual : population) {
                maximum = Math.max(maximum, individual.packedArea);
                sum += individual.packedArea;
            }
            return new PopulationFitness(maximum, sum / population.size());
        }
    }

    private static final class AdaptiveCounters {
        private long crossoverSamples;
        private long mutationSamples;
        private double crossoverSum;
        private double mutationSum;
        private double minimumCrossover = Double.POSITIVE_INFINITY;
        private double maximumCrossover = Double.NEGATIVE_INFINITY;
        private double minimumMutation = Double.POSITIVE_INFINITY;
        private double maximumMutation = Double.NEGATIVE_INFINITY;

        private void recordCrossover(double rate) {
            crossoverSamples++;
            crossoverSum += rate;
            minimumCrossover = Math.min(minimumCrossover, rate);
            maximumCrossover = Math.max(maximumCrossover, rate);
        }

        private void recordMutation(double rate) {
            mutationSamples++;
            mutationSum += rate;
            minimumMutation = Math.min(minimumMutation, rate);
            maximumMutation = Math.max(maximumMutation, rate);
        }

        private AdaptiveGeneticAlgorithmStatistics toStatistics() {
            if (crossoverSamples == 0 && mutationSamples == 0) {
                return AdaptiveGeneticAlgorithmStatistics.empty();
            }
            return new AdaptiveGeneticAlgorithmStatistics(
                    crossoverSamples,
                    mutationSamples,
                    crossoverSamples == 0 ? 0.0 : crossoverSum / crossoverSamples,
                    mutationSamples == 0 ? 0.0 : mutationSum / mutationSamples,
                    crossoverSamples == 0 ? 0.0 : minimumCrossover,
                    crossoverSamples == 0 ? 0.0 : maximumCrossover,
                    mutationSamples == 0 ? 0.0 : minimumMutation,
                    mutationSamples == 0 ? 0.0 : maximumMutation);
        }
    }

    private record Individual(int[] sequence, long packedArea) {
        private Individual copy() {
            return new Individual(sequence.clone(), packedArea);
        }
    }

    private static final class SequenceKey {
        private final int[] values;
        private final int hash;

        private SequenceKey(int[] sequence) {
            values = sequence.clone();
            hash = Arrays.hashCode(values);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SequenceKey key && Arrays.equals(values, key.values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class FitnessEvaluator {
        private final SearchLimit searchLimit;
        private final long startNanos;
        private final DecodingCache cache;
        private long evaluations;
        private long cacheHits;
        private boolean limitReached;

        private FitnessEvaluator(
                Instance instance,
                PackingDecoder decoder,
                SearchLimit searchLimit,
                long startNanos) {
            this.searchLimit = searchLimit;
            this.startNanos = startNanos;
            this.cache = new DecodingCache(
                    instance, decoder, DecodingCacheCapacity.configured());
        }

        private long packedArea(int[] sequence) {
            Long cached = cache.get(sequence);
            if (cached != null) {
                cacheHits++;
                return cached;
            }
            if (evaluations >= searchLimit.maxEvaluations()
                    || evaluations > 0 && searchLimit.timeReached(startNanos)
                    || SearchLimit.cancelled()) {
                limitReached = true;
                return Long.MIN_VALUE;
            }
            long area = cache.decodeAndStore(sequence);
            evaluations++;
            return area;
        }

        /** 收尾时解码历史最优排列，拿到完整布局。 */
        private Solution solve(int[] sequence) {
            return cache.solve(sequence);
        }
    }
}
