package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 在可注入排样解码器上运行的完整状态、移动和多种属性禁忌搜索。 */
public final class TabuSearchSolver implements PackingAlgorithm, ConvergenceTraceProvider {
    private final boolean rotationAllowed;
    private final ItemOrder initialOrder;
    private final TabuMode mode;
    private final TabuSearchConfig config;
    private final SearchLimit searchLimit;
    private final PackingDecoder decoder;
    private TabuSearchStatistics lastStatistics = TabuSearchStatistics.empty();
    private List<ConvergencePoint> lastConvergenceTrace = List.of();

    public TabuSearchSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            TabuMode mode,
            TabuSearchConfig config) {
        this(rotationAllowed, initialOrder, mode, config, SearchLimit.unlimited(),
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public TabuSearchSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            TabuMode mode,
            TabuSearchConfig config,
            SearchLimit searchLimit) {
        this(rotationAllowed, initialOrder, mode, config, searchLimit,
                PackingDecoders.create(PackingDecoderType.SKYLINE, rotationAllowed));
    }

    public TabuSearchSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            TabuMode mode,
            TabuSearchConfig config,
            SearchLimit searchLimit,
            PackingDecoder decoder) {
        this.rotationAllowed = rotationAllowed;
        this.initialOrder = initialOrder;
        this.mode = mode;
        this.config = config;
        this.searchLimit = searchLimit;
        this.decoder = java.util.Objects.requireNonNull(decoder, "排样解码器不能为空");
    }

    @Override
    public String name() {
        return "Tabu Search(" + mode + ", decoder=" + decoder.name() + ", initial=" + initialOrder
                + ", rotation=" + rotationAllowed + ", seed=" + config.seed() + ")";
    }

    public TabuSearchStatistics lastStatistics() {
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
            lastStatistics = TabuSearchStatistics.empty();
            lastConvergenceTrace = List.of(new ConvergencePoint(0, 0, only.packedArea()));
            return new Solution(name(), only.placements(), System.nanoTime() - start);
        }

        int maxId = 0;
        for (Item item : instance.items()) maxId = Math.max(maxId, item.id());

        // 重复排列的复用集中在这里；预算与计数口径仍由下面的循环条件决定。
        // 容量默认与既有行为一致，可用 PACKING_CACHE_CAPACITY 做「无缓存/固定缓存」对照实验。
        DecodingCache cache = new DecodingCache(
                instance, decoder, DecodingCacheCapacity.configured());
        // 只有 PLACEMENT_ATTRIBUTE 需要知道每个零件落在哪里，其余模式只比较面积即可。
        boolean needsPlacements = mode == TabuMode.PLACEMENT_ATTRIBUTE;
        Solution currentSolution = needsPlacements ? cache.solve(currentSequence) : null;
        long currentArea = needsPlacements
                ? currentSolution.packedArea()
                : cache.areaOf(currentSequence);
        int[] bestSequence = currentSequence.clone();
        // 候选排列的复用缓冲区：邻域里每个交换原本都要克隆整个排列，这是每评价一次的最大一笔
        // 分配。改为把候选写进这块固定缓冲，评价完即被下一次覆盖；只有真正胜出的候选才复制出
        // 自己的副本再交给 Neighbor（Neighbor 会被长期持有并提升为 currentSequence，
        // 绝不能指向共享缓冲）。
        int[] candidateScratch = new int[currentSequence.length];
        // 交换集合的形状只由长度决定，每次迭代只是重新洗牌，无需重建。
        List<Swap> swaps = allSwaps(currentSequence.length);
        long bestArea = currentArea;
        List<ConvergencePoint> trace = new ArrayList<>();
        trace.add(new ConvergencePoint(0, 0, bestArea));

        FullStateMemory fullStateMemory = new FullStateMemory(config.tabuTenure());
        if (mode == TabuMode.FULL_STATE) {
            // 初始排列也是已经访问过的完整状态，防止搜索第一步后立即原路返回。
            fullStateMemory.add(currentSequence);
        }
        int[][] moveTabuUntil = new int[maxId + 1][maxId + 1];
        int[][] positionTabuUntil = new int[maxId + 1][currentSequence.length];
        Map<Long, Integer> orderTabuUntil = new HashMap<>();
        Map<Long, Integer> adjacencyTabuUntil = new HashMap<>();
        Map<PlacementAttribute, Integer> placementTabuUntil = new HashMap<>();
        Random random = new Random(config.seed());

        long evaluated = 0;
        long tabuHits = 0;
        int improvements = 0;
        int acceptedWorse = 0;
        int aspirations = 0;
        int expanded = 0;
        int stalled = 0;
        int noImprovement = 0;
        int completedIterations = 0;

        for (int iteration = 0;
                iteration < config.maxIterations()
                        && noImprovement < config.maxNoImprovement()
                        && bestArea < instance.binArea()
                        && evaluated < searchLimit.maxEvaluations()
                        && !searchLimit.timeReached(start)
                        && !SearchLimit.cancelled();
                iteration++) {

            completedIterations++;
            pruneExpired(orderTabuUntil, iteration);
            pruneExpired(adjacencyTabuUntil, iteration);
            pruneExpired(placementTabuUntil, iteration);
            Collections.shuffle(swaps, random);
            int primaryLimit = Math.min(config.neighborhoodSize(), swaps.size());

            Neighbor bestNeighbor = null;
            int examined = 0;
            for (; examined < primaryLimit
                    && evaluated < searchLimit.maxEvaluations()
                    && !searchLimit.timeReached(start)
                    && !SearchLimit.cancelled(); examined++) {
                Neighbor candidate = evaluateSwap(
                        swaps.get(examined), currentSequence, candidateScratch, iteration, instance,
                        cache, bestArea, fullStateMemory,
                        moveTabuUntil, positionTabuUntil, orderTabuUntil,
                        adjacencyTabuUntil, placementTabuUntil);
                if (candidate.evaluated) evaluated++;
                if (candidate.tabu) tabuHits++;
                if (candidate.admissible && betterNeighbor(candidate, candidateScratch, bestNeighbor)) {
                    bestNeighbor = candidate.owned(candidateScratch);
                }
            }

            // 抽样邻域全被禁忌时，扩大到剩余完整交换邻域，避免搜索停滞。
            if (bestNeighbor == null && examined < swaps.size()
                    && evaluated < searchLimit.maxEvaluations()
                    && !searchLimit.timeReached(start)
                    && !SearchLimit.cancelled()) {
                expanded++;
                for (; examined < swaps.size()
                        && evaluated < searchLimit.maxEvaluations()
                        && !searchLimit.timeReached(start)
                        && !SearchLimit.cancelled(); examined++) {
                    Neighbor candidate = evaluateSwap(
                            swaps.get(examined), currentSequence, candidateScratch, iteration, instance,
                            cache, bestArea, fullStateMemory,
                            moveTabuUntil, positionTabuUntil, orderTabuUntil,
                            adjacencyTabuUntil, placementTabuUntil);
                    if (candidate.evaluated) evaluated++;
                    if (candidate.tabu) tabuHits++;
                    if (candidate.admissible && betterNeighbor(candidate, candidateScratch, bestNeighbor)) {
                        bestNeighbor = candidate.owned(candidateScratch);
                    }
                }
            }

            if (bestNeighbor == null) {
                // 强属性可能暂时封锁全部交换；让禁忌时钟前进，但不伪造特赦或强行违规。
                stalled++;
                noImprovement++;
                trace.add(new ConvergencePoint(completedIterations, evaluated, bestArea));
                continue;
            }
            if (bestNeighbor.area < currentArea) acceptedWorse++;
            if (bestNeighbor.aspiration) aspirations++;

            registerAcceptedMove(
                    bestNeighbor, currentSequence, currentSolution, iteration,
                    fullStateMemory, moveTabuUntil, positionTabuUntil,
                    orderTabuUntil, adjacencyTabuUntil, placementTabuUntil);
            currentSequence = bestNeighbor.sequence;
            currentArea = bestNeighbor.area;
            if (needsPlacements) currentSolution = bestNeighbor.solution;

            if (currentArea > bestArea) {
                bestSequence = currentSequence.clone();
                bestArea = currentArea;
                improvements++;
                noImprovement = 0;
            } else {
                noImprovement++;
            }
            trace.add(new ConvergencePoint(completedIterations, evaluated, bestArea));
        }

        // 重新解码保存的历史最好序列，确保最终布局与最终序列严格对应。
        Solution verifiedBest = cache.solve(bestSequence);
        lastStatistics = new TabuSearchStatistics(
                completedIterations, evaluated, tabuHits, improvements, acceptedWorse,
                aspirations, expanded, stalled, cache.statistics());
        lastConvergenceTrace = List.copyOf(trace);
        return new Solution(name(), verifiedBest.placements(), System.nanoTime() - start);
    }

    /**
     * 评价一个交换候选。候选排列写进复用的 {@code scratch}，返回的 {@link Neighbor} 不持有它
     * ——只有胜出的候选才由调用方复制出副本。
     */
    private Neighbor evaluateSwap(
            Swap swap,
            int[] current,
            int[] scratch,
            int iteration,
            Instance instance,
            DecodingCache cache,
            long bestArea,
            FullStateMemory fullStateMemory,
            int[][] moveTabuUntil,
            int[][] positionTabuUntil,
            Map<Long, Integer> orderTabuUntil,
            Map<Long, Integer> adjacencyTabuUntil,
            Map<PlacementAttribute, Integer> placementTabuUntil) {

        int itemA = current[swap.firstPosition];
        int itemB = current[swap.secondPosition];
        // 当前排列在位置 swap 上本就互不相同，只需交换这两处即可得到候选，无需整表克隆。
        System.arraycopy(current, 0, scratch, 0, current.length);
        scratch[swap.firstPosition] = itemB;
        scratch[swap.secondPosition] = itemA;

        if (mode == TabuMode.FULL_STATE && fullStateMemory.contains(scratch)) {
            return Neighbor.rejected(swap, itemA, itemB);
        }

        Solution candidateSolution = mode == TabuMode.PLACEMENT_ATTRIBUTE
                ? cache.solve(scratch)
                : null;
        long candidateArea = candidateSolution != null
                ? candidateSolution.packedArea()
                : cache.areaOf(scratch);
        boolean tabu = switch (mode) {
            case FULL_STATE -> false;
            case MOVE -> moveTabuUntil[itemA][itemB] > iteration;
            case POSITION_ATTRIBUTE ->
                    positionTabuUntil[itemA][swap.secondPosition] > iteration
                            || positionTabuUntil[itemB][swap.firstPosition] > iteration;
            case RELATIVE_ORDER_ATTRIBUTE -> containsForbiddenOrder(
                    scratch, iteration, orderTabuUntil);
            case ADJACENCY_ATTRIBUTE -> containsForbiddenAdjacency(
                    scratch, iteration, adjacencyTabuUntil);
            case PLACEMENT_ATTRIBUTE -> containsForbiddenPlacement(
                    scratch, candidateSolution, iteration, placementTabuUntil);
        };
        boolean aspiration = tabu && candidateArea > bestArea;
        boolean admissible = !tabu || aspiration;
        return new Neighbor(
                swap, null, candidateArea, candidateSolution, itemA, itemB,
                tabu, true, admissible, aspiration);
    }

    private void registerAcceptedMove(
            Neighbor accepted,
            int[] currentSequence,
            Solution currentSolution,
            int iteration,
            FullStateMemory fullStateMemory,
            int[][] moveTabuUntil,
            int[][] positionTabuUntil,
            Map<Long, Integer> orderTabuUntil,
            Map<Long, Integer> adjacencyTabuUntil,
            Map<PlacementAttribute, Integer> placementTabuUntil) {

        int expire = iteration + config.tabuTenure() + 1;
        switch (mode) {
            case FULL_STATE -> fullStateMemory.add(accepted.sequence);
            case MOVE -> {
                moveTabuUntil[accepted.itemA][accepted.itemB] = expire;
                moveTabuUntil[accepted.itemB][accepted.itemA] = expire;
            }
            case POSITION_ATTRIBUTE -> {
                // A和B离开原位置后，暂时禁止它们返回原位置。
                positionTabuUntil[accepted.itemA][accepted.swap.firstPosition] = expire;
                positionTabuUntil[accepted.itemB][accepted.swap.secondPosition] = expire;
            }
            case RELATIVE_ORDER_ATTRIBUTE -> registerRemovedOrders(
                    currentSequence, accepted.sequence, expire, orderTabuUntil);
            case ADJACENCY_ATTRIBUTE -> registerRemovedAdjacencies(
                    currentSequence, accepted.sequence, expire, adjacencyTabuUntil);
            case PLACEMENT_ATTRIBUTE -> registerChangedPlacements(
                    currentSequence, currentSolution, accepted.solution,
                    expire, placementTabuUntil);
        }
    }

    private static boolean containsForbiddenOrder(
            int[] sequence, int iteration, Map<Long, Integer> tabuUntil) {
        if (tabuUntil.isEmpty()) return false;
        int maxId = Arrays.stream(sequence).max().orElse(0);
        int[] position = new int[maxId + 1];
        for (int i = 0; i < sequence.length; i++) position[sequence[i]] = i;
        for (Map.Entry<Long, Integer> entry : tabuUntil.entrySet()) {
            if (entry.getValue() <= iteration) continue;
            int first = firstOfPair(entry.getKey());
            int second = secondOfPair(entry.getKey());
            if (position[first] < position[second]) return true;
        }
        return false;
    }

    private static void registerRemovedOrders(
            int[] oldSequence,
            int[] newSequence,
            int expire,
            Map<Long, Integer> tabuUntil) {
        int maxId = Arrays.stream(newSequence).max().orElse(0);
        int[] newPosition = new int[maxId + 1];
        for (int i = 0; i < newSequence.length; i++) newPosition[newSequence[i]] = i;
        for (int i = 0; i < oldSequence.length; i++) {
            for (int j = i + 1; j < oldSequence.length; j++) {
                int first = oldSequence[i];
                int second = oldSequence[j];
                if (newPosition[first] > newPosition[second]) {
                    tabuUntil.put(orderedPair(first, second), expire);
                }
            }
        }
    }

    private static boolean containsForbiddenAdjacency(
            int[] sequence, int iteration, Map<Long, Integer> tabuUntil) {
        for (int i = 0; i + 1 < sequence.length; i++) {
            if (tabuUntil.getOrDefault(orderedPair(sequence[i], sequence[i + 1]), 0) > iteration) {
                return true;
            }
        }
        return false;
    }

    private static void registerRemovedAdjacencies(
            int[] oldSequence,
            int[] newSequence,
            int expire,
            Map<Long, Integer> tabuUntil) {
        Set<Long> newAdjacencies = new HashSet<>();
        for (int i = 0; i + 1 < newSequence.length; i++) {
            newAdjacencies.add(orderedPair(newSequence[i], newSequence[i + 1]));
        }
        for (int i = 0; i + 1 < oldSequence.length; i++) {
            long oldAdjacency = orderedPair(oldSequence[i], oldSequence[i + 1]);
            if (!newAdjacencies.contains(oldAdjacency)) tabuUntil.put(oldAdjacency, expire);
        }
    }

    private static boolean containsForbiddenPlacement(
            int[] sequence,
            Solution solution,
            int iteration,
            Map<PlacementAttribute, Integer> tabuUntil) {
        Map<Integer, PlacementAttribute> attributes = placementAttributes(sequence, solution);
        for (PlacementAttribute attribute : attributes.values()) {
            if (tabuUntil.getOrDefault(attribute, 0) > iteration) return true;
        }
        return false;
    }

    private static void registerChangedPlacements(
            int[] sequence,
            Solution oldSolution,
            Solution newSolution,
            int expire,
            Map<PlacementAttribute, Integer> tabuUntil) {
        Map<Integer, PlacementAttribute> oldAttributes = placementAttributes(sequence, oldSolution);
        Map<Integer, PlacementAttribute> newAttributes = placementAttributes(sequence, newSolution);
        for (int itemId : sequence) {
            PlacementAttribute oldAttribute = oldAttributes.get(itemId);
            if (!oldAttribute.equals(newAttributes.get(itemId))) {
                tabuUntil.put(oldAttribute, expire);
            }
        }
    }

    private static Map<Integer, PlacementAttribute> placementAttributes(
            int[] sequence, Solution solution) {
        Map<Integer, PlacementAttribute> attributes = new HashMap<>();
        for (int itemId : sequence) attributes.put(itemId, PlacementAttribute.unplaced(itemId));
        for (Placement placement : solution.placements()) {
            attributes.put(placement.item().id(), PlacementAttribute.placed(placement));
        }
        return attributes;
    }

    private static long orderedPair(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    private static int firstOfPair(long pair) {
        return (int) (pair >>> 32);
    }

    private static int secondOfPair(long pair) {
        return (int) pair;
    }

    private static <K> void pruneExpired(Map<K, Integer> tabuUntil, int iteration) {
        tabuUntil.entrySet().removeIf(entry -> entry.getValue() <= iteration);
    }

    /**
     * 比较候选与当前最优。候选排列仍写在共享缓冲 {@code candidateSequence} 里
     * （{@link Neighbor#owned} 之前），因此平局时从缓冲读取；{@code incumbent} 一定已持有副本。
     */
    private static boolean betterNeighbor(
            Neighbor candidate, int[] candidateSequence, Neighbor incumbent) {
        if (incumbent == null) return true;
        if (candidate.area != incumbent.area) {
            return candidate.area > incumbent.area;
        }
        // 面积相同时，稳定地选择字典序更小的完整排列。
        for (int i = 0; i < candidateSequence.length; i++) {
            if (candidateSequence[i] != incumbent.sequence[i]) {
                return candidateSequence[i] < incumbent.sequence[i];
            }
        }
        return false;
    }

    private static List<Swap> allSwaps(int length) {
        List<Swap> swaps = new ArrayList<>(length * (length - 1) / 2);
        for (int i = 0; i < length; i++) {
            for (int j = i + 1; j < length; j++) swaps.add(new Swap(i, j));
        }
        return swaps;
    }

    private record Swap(int firstPosition, int secondPosition) {}

    /**
     * {@code solution} 仅在 PLACEMENT_ATTRIBUTE 模式下非空——只有该模式需要放置位置。
     * {@code sequence} 为 {@code null} 表示该候选尚未持有排列副本（评价时只写在共享缓冲里），
     * 胜出后必须经 {@link #owned} 复制出生效的排列。
     */
    private record Neighbor(
            Swap swap,
            int[] sequence,
            long area,
            Solution solution,
            int itemA,
            int itemB,
            boolean tabu,
            boolean evaluated,
            boolean admissible,
            boolean aspiration) {
        /** 复制出候选自己的排列副本；只有被选中的候选才调用，一次评价最多一次。 */
        private Neighbor owned(int[] candidateScratch) {
            return new Neighbor(
                    swap, candidateScratch.clone(), area, solution, itemA, itemB,
                    tabu, evaluated, admissible, aspiration);
        }

        private static Neighbor rejected(Swap swap, int itemA, int itemB) {
            return new Neighbor(swap, null, 0, null, itemA, itemB, true, false, false, false);
        }
    }

    private record PlacementAttribute(
            int itemId, boolean placed, int x, int y, boolean rotated) {
        private static PlacementAttribute unplaced(int itemId) {
            return new PlacementAttribute(itemId, false, 0, 0, false);
        }

        private static PlacementAttribute placed(Placement placement) {
            return new PlacementAttribute(
                    placement.item().id(), true, placement.x(), placement.y(), placement.rotated());
        }
    }

    private static final class FullStateMemory {
        private final int capacity;
        private final Deque<List<Integer>> queue = new ArrayDeque<>();
        private final Set<List<Integer>> set = new HashSet<>();

        private FullStateMemory(int capacity) {
            this.capacity = capacity;
        }

        private boolean contains(int[] sequence) {
            return set.contains(key(sequence));
        }

        private void add(int[] sequence) {
            List<Integer> key = key(sequence);
            if (set.add(key)) queue.addLast(key);
            while (queue.size() > capacity) set.remove(queue.removeFirst());
        }

        private static List<Integer> key(int[] sequence) {
            return Arrays.stream(sequence).boxed().toList();
        }
    }
}
