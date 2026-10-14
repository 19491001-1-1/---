package com.assessment.packing.experiment;

import com.assessment.packing.solver.AntColonyConfig;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmConfig;
import com.assessment.packing.solver.BottomLeftSolver;
import com.assessment.packing.solver.ContinuousBottomLeftSolver;
import com.assessment.packing.solver.GeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticMutationMode;
import com.assessment.packing.solver.GuillotineRectChoice;
import com.assessment.packing.solver.GuillotineSolver;
import com.assessment.packing.solver.GuillotineSplitRule;
import com.assessment.packing.solver.HybridGeneticAlgorithmConfig;
import com.assessment.packing.solver.HybridLocalSearchMode;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.IteratedLocalSearchConfig;
import com.assessment.packing.solver.MaxRectsHeuristic;
import com.assessment.packing.solver.MaxRectsBestSolver;
import com.assessment.packing.solver.MaxRectsSolver;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.PackingDecoder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.PackingDecoders;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.ShelfHeuristic;
import com.assessment.packing.solver.ShelfFloorCeilingSolver;
import com.assessment.packing.solver.ShelfSelectionMode;
import com.assessment.packing.solver.ShelfSolver;
import com.assessment.packing.solver.SimulatedAnnealingConfig;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.SkylinePlacementHeuristic;
import com.assessment.packing.solver.SkylineGlobalSolver;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.solver.TabuSearchSolver;
import com.assessment.packing.solver.TabuMode;
import com.assessment.packing.solver.TabuSearchConfig;

import java.util.List;

public final class SolverFactory {
    private SolverFactory() {}

    public static List<AlgorithmId> allAlgorithms() {
        return List.of(AlgorithmId.values());
    }

    /** 默认报告和未来前端主界面展示的工程主入口。 */
    public static List<AlgorithmId> primaryAlgorithms() {
        return AlgorithmCatalog.primaryAlgorithms();
    }

    /** 公平预算实验入口：构造式 Skyline 基线加上六个搜索优化算法。 */
    public static List<AlgorithmId> fairAlgorithms() {
        return List.of(
                AlgorithmId.SKYLINE,
                AlgorithmId.TABU_MOVE,
                AlgorithmId.SA,
                AlgorithmId.ACO,
                AlgorithmId.ILS,
                AlgorithmId.ADAPTIVE_GA,
                AlgorithmId.GA_TABU);
    }

    public static List<AlgorithmId> shelfAlgorithms() {
        return List.of(
                AlgorithmId.SHELF,
                AlgorithmId.SHELF_FF,
                AlgorithmId.SHELF_BWF,
                AlgorithmId.SHELF_BHF,
                AlgorithmId.SHELF_BAF,
                AlgorithmId.SHELF_WWF,
                AlgorithmId.SHELF_FC,
                AlgorithmId.SHELF_NF_GLOBAL,
                AlgorithmId.SHELF_FF_GLOBAL,
                AlgorithmId.SHELF_BWF_GLOBAL,
                AlgorithmId.SHELF_BHF_GLOBAL,
                AlgorithmId.SHELF_BAF_GLOBAL,
                AlgorithmId.SHELF_WWF_GLOBAL,
                AlgorithmId.SHELF_NF_WM,
                AlgorithmId.SHELF_FF_WM,
                AlgorithmId.SHELF_BWF_WM,
                AlgorithmId.SHELF_BHF_WM,
                AlgorithmId.SHELF_BAF_WM,
                AlgorithmId.SHELF_WWF_WM);
    }

    public static List<AlgorithmId> maxRectsAlgorithms() {
        return List.of(
                AlgorithmId.MAXRECTS_BSSF,
                AlgorithmId.MAXRECTS_BLSF,
                AlgorithmId.MAXRECTS_BAF,
                AlgorithmId.MAXRECTS_BL,
                AlgorithmId.MAXRECTS_CP,
                AlgorithmId.MAXRECTS_BEST);
    }

    public static List<AlgorithmId> skylineAlgorithms() {
        return List.of(
                AlgorithmId.SKYLINE,
                AlgorithmId.SKYLINE_BL,
                AlgorithmId.SKYLINE_BF,
                AlgorithmId.SKYLINE_BL_WM,
                AlgorithmId.SKYLINE_BF_WM);
    }

    public static PackingAlgorithm create(
            AlgorithmId id,
            boolean rotationAllowed,
            ItemOrder order,
            long seed,
            ExperimentProfile profile,
            SearchLimit limit) {
        return create(id, rotationAllowed, order, seed, profile, limit,
                PackingDecoderType.SKYLINE);
    }

    public static PackingAlgorithm create(
            AlgorithmId id,
            boolean rotationAllowed,
            ItemOrder order,
            long seed,
            ExperimentProfile profile,
            SearchLimit limit,
            PackingDecoderType decoderType) {
        TabuSearchConfig tabu = profile == ExperimentProfile.QUICK
                ? new TabuSearchConfig(60, 20, 7, 25, seed)
                : TabuSearchConfig.defaults(seed);
        SimulatedAnnealingConfig sa = profile == ExperimentProfile.QUICK
                ? new SimulatedAnnealingConfig(50, 30, 0.96, 1.0e-5, 15, 0.8, 50, seed)
                : SimulatedAnnealingConfig.defaults(seed);
        GeneticAlgorithmConfig ga = profile == ExperimentProfile.QUICK
                ? new GeneticAlgorithmConfig(
                        30, 40, 3, 0.9, 0.2, 2, 15, 10, GeneticMutationMode.MIXED, seed)
                : new GeneticAlgorithmConfig(
                        100, 500, 3, 0.9, 0.2, 5, 100, 20,
                        GeneticMutationMode.MIXED, seed);
        AntColonyConfig aco = profile == ExperimentProfile.QUICK
                ? new AntColonyConfig(20, 40, 1.0, 2.0, 0.20, 1.0, 3, 15, seed)
                : AntColonyConfig.defaults(seed);
        IteratedLocalSearchConfig ils = profile == ExperimentProfile.QUICK
                ? new IteratedLocalSearchConfig(40, 30, 2, 15, seed)
                : IteratedLocalSearchConfig.defaults(seed);
        HybridGeneticAlgorithmConfig hybrid = profile == ExperimentProfile.QUICK
                ? new HybridGeneticAlgorithmConfig(5, 0.10, 3, 60, 3, 10, 0.90, 0.30, 5)
                : new HybridGeneticAlgorithmConfig(5, 0.10, 10, 300, 5, 20, 0.90, 0.30, 7);

        // 时间公平实验由统一墙钟上限终止；关闭各算法不同的“无改进”提前停止，
        // 避免某个算法只用到预算的一小部分。达到100%利用率仍会合理提前结束。
        if (limit.maxNanos() != Long.MAX_VALUE) {
            tabu = new TabuSearchConfig(
                    Integer.MAX_VALUE, tabu.neighborhoodSize(), tabu.tabuTenure(),
                    Integer.MAX_VALUE, seed);
            sa = new SimulatedAnnealingConfig(
                    Integer.MAX_VALUE, sa.attemptsPerTemperature(), 0.999,
                    1.0e-300, Integer.MAX_VALUE,
                    sa.targetInitialAcceptanceProbability(), sa.initialTemperatureSamples(), seed);
            ga = new GeneticAlgorithmConfig(
                    ga.populationSize(), Integer.MAX_VALUE, ga.tournamentSize(),
                    ga.crossoverRate(), ga.mutationRate(), ga.eliteCount(),
                    Integer.MAX_VALUE, ga.duplicateRetries(), ga.mutationMode(), seed);
            aco = new AntColonyConfig(
                    aco.antCount(), Integer.MAX_VALUE,
                    aco.pheromoneAlpha(), aco.heuristicBeta(),
                    aco.evaporationRate(), aco.depositStrength(), aco.eliteAnts(),
                    Integer.MAX_VALUE, seed);
            ils = new IteratedLocalSearchConfig(
                    Integer.MAX_VALUE, ils.localSearchEvaluations(),
                    ils.perturbationSwaps(), Integer.MAX_VALUE, seed);
        }

        PackingDecoder decoder = PackingDecoders.create(decoderType, rotationAllowed);

        return switch (id) {
            case SHELF -> new ShelfSolver(rotationAllowed, order);
            case SHELF_FF -> shelf(rotationAllowed, order, ShelfHeuristic.FIRST_FIT, false);
            case SHELF_BWF -> shelf(rotationAllowed, order, ShelfHeuristic.BEST_WIDTH_FIT, false);
            case SHELF_BHF -> shelf(rotationAllowed, order, ShelfHeuristic.BEST_HEIGHT_FIT, false);
            case SHELF_BAF -> shelf(rotationAllowed, order, ShelfHeuristic.BEST_AREA_FIT, false);
            case SHELF_WWF -> shelf(rotationAllowed, order, ShelfHeuristic.WORST_WIDTH_FIT, false);
            case SHELF_FC -> new ShelfFloorCeilingSolver(rotationAllowed);
            case SHELF_NF_GLOBAL -> shelfGlobal(rotationAllowed, order, ShelfHeuristic.NEXT_FIT);
            case SHELF_FF_GLOBAL -> shelfGlobal(rotationAllowed, order, ShelfHeuristic.FIRST_FIT);
            case SHELF_BWF_GLOBAL -> shelfGlobal(rotationAllowed, order, ShelfHeuristic.BEST_WIDTH_FIT);
            case SHELF_BHF_GLOBAL -> shelfGlobal(rotationAllowed, order, ShelfHeuristic.BEST_HEIGHT_FIT);
            case SHELF_BAF_GLOBAL -> shelfGlobal(rotationAllowed, order, ShelfHeuristic.BEST_AREA_FIT);
            case SHELF_WWF_GLOBAL -> shelfGlobal(rotationAllowed, order, ShelfHeuristic.WORST_WIDTH_FIT);
            case SHELF_NF_WM -> shelf(rotationAllowed, order, ShelfHeuristic.NEXT_FIT, true);
            case SHELF_FF_WM -> shelf(rotationAllowed, order, ShelfHeuristic.FIRST_FIT, true);
            case SHELF_BWF_WM -> shelf(rotationAllowed, order, ShelfHeuristic.BEST_WIDTH_FIT, true);
            case SHELF_BHF_WM -> shelf(rotationAllowed, order, ShelfHeuristic.BEST_HEIGHT_FIT, true);
            case SHELF_BAF_WM -> shelf(rotationAllowed, order, ShelfHeuristic.BEST_AREA_FIT, true);
            case SHELF_WWF_WM -> shelf(rotationAllowed, order, ShelfHeuristic.WORST_WIDTH_FIT, true);
            case BOTTOM_LEFT -> new BottomLeftSolver(rotationAllowed, order);
            case CONTINUOUS_BOTTOM_LEFT -> new ContinuousBottomLeftSolver(rotationAllowed, order);
            case SKYLINE -> new SkylineSolver(rotationAllowed, order);
            case SKYLINE_BL -> new SkylineGlobalSolver(
                    rotationAllowed, order, SkylinePlacementHeuristic.BOTTOM_LEFT);
            case SKYLINE_BF -> new SkylineGlobalSolver(
                    rotationAllowed, order, SkylinePlacementHeuristic.BEST_FIT);
            case SKYLINE_BL_WM -> new SkylineGlobalSolver(
                    rotationAllowed, order, SkylinePlacementHeuristic.BOTTOM_LEFT, true);
            case SKYLINE_BF_WM -> new SkylineGlobalSolver(
                    rotationAllowed, order, SkylinePlacementHeuristic.BEST_FIT, true);
            case GUILLOTINE -> new GuillotineSolver(
                    rotationAllowed, order,
                    GuillotineRectChoice.BSSF, GuillotineSplitRule.SAS, true);
            case MAXRECTS_BSSF -> maxRects(rotationAllowed, order, MaxRectsHeuristic.BSSF);
            case MAXRECTS_BLSF -> maxRects(rotationAllowed, order, MaxRectsHeuristic.BLSF);
            case MAXRECTS_BAF -> maxRects(rotationAllowed, order, MaxRectsHeuristic.BAF);
            case MAXRECTS_BL -> maxRects(rotationAllowed, order, MaxRectsHeuristic.BL);
            case MAXRECTS_CP -> maxRects(rotationAllowed, order, MaxRectsHeuristic.CP);
            case MAXRECTS_BEST -> new MaxRectsBestSolver(rotationAllowed, order);
            case TABU_FULL -> tabu(rotationAllowed, order, TabuMode.FULL_STATE, tabu, limit, decoder);
            case TABU_MOVE -> tabu(rotationAllowed, order, TabuMode.MOVE, tabu, limit, decoder);
            case TABU_POSITION -> tabu(
                    rotationAllowed, order, TabuMode.POSITION_ATTRIBUTE, tabu, limit, decoder);
            case TABU_ORDER -> tabu(
                    rotationAllowed, order, TabuMode.RELATIVE_ORDER_ATTRIBUTE, tabu, limit, decoder);
            case TABU_ADJACENCY -> tabu(
                    rotationAllowed, order, TabuMode.ADJACENCY_ATTRIBUTE, tabu, limit, decoder);
            case TABU_PLACEMENT -> tabu(
                    rotationAllowed, order, TabuMode.PLACEMENT_ATTRIBUTE, tabu, limit, decoder);
            case SA -> new SimulatedAnnealingSolver(rotationAllowed, order, sa, limit, decoder);
            case ACO -> new AntColonySolver(rotationAllowed, order, aco, limit, decoder);
            case ILS -> new IteratedLocalSearchSolver(rotationAllowed, order, ils, limit, decoder);
            case GA -> new GeneticAlgorithmSolver(
                    rotationAllowed, order, ga, null, null, limit, decoder);
            case ADAPTIVE_GA -> new AdaptiveGeneticAlgorithmSolver(
                    rotationAllowed, order, ga, AdaptiveGeneticAlgorithmConfig.defaults(), limit, decoder);
            case GA_SA -> new GeneticAlgorithmSolver(
                    rotationAllowed, order, ga, HybridLocalSearchMode.ANNEALING, hybrid, limit, decoder);
            case GA_TABU -> new GeneticAlgorithmSolver(
                    rotationAllowed, order, ga, HybridLocalSearchMode.TABU_MOVE, hybrid, limit, decoder);
        };
    }

    private static PackingAlgorithm shelf(
            boolean rotationAllowed,
            ItemOrder order,
            ShelfHeuristic heuristic,
            boolean wasteMapEnabled) {
        return new ShelfSolver(rotationAllowed, order, heuristic, wasteMapEnabled);
    }

    private static PackingAlgorithm maxRects(
            boolean rotationAllowed,
            ItemOrder order,
            MaxRectsHeuristic heuristic) {
        return new MaxRectsSolver(rotationAllowed, order, heuristic);
    }

    private static PackingAlgorithm shelfGlobal(
            boolean rotationAllowed, ItemOrder order, ShelfHeuristic heuristic) {
        return new ShelfSolver(
                rotationAllowed, order, heuristic, false, ShelfSelectionMode.GLOBAL);
    }

    private static PackingAlgorithm tabu(
            boolean rotationAllowed,
            ItemOrder order,
            TabuMode mode,
            TabuSearchConfig config,
            SearchLimit limit,
            PackingDecoder decoder) {
        return new TabuSearchSolver(rotationAllowed, order, mode, config, limit, decoder);
    }
}
