package com.assessment.packing.experiment;

import com.assessment.packing.solver.AdaptiveGeneticAlgorithmConfig;
import com.assessment.packing.solver.AntColonyConfig;
import com.assessment.packing.solver.GeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticMutationMode;
import com.assessment.packing.solver.GuillotineRectChoice;
import com.assessment.packing.solver.GuillotineSolver;
import com.assessment.packing.solver.GuillotineSplitRule;
import com.assessment.packing.solver.HybridGeneticAlgorithmConfig;
import com.assessment.packing.solver.HybridLocalSearchMode;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.IteratedLocalSearchConfig;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.SimulatedAnnealingConfig;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmSolver;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuSearchSolver;
import com.assessment.packing.solver.TabuMode;
import com.assessment.packing.solver.TabuSearchConfig;

/** 将命令行别名和可选参数转换为统一的求解器实例。 */
public final class CommandLineSolverFactory {
    private CommandLineSolverFactory() {}

    public static PackingAlgorithm create(
            String algorithmText, boolean rotationAllowed, ItemOrder order, String[] args) {
        AlgorithmId id;
        try {
            id = AlgorithmId.parse(algorithmText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知算法：" + algorithmText, exception);
        }

        return switch (id) {
            case GUILLOTINE -> guillotine(rotationAllowed, order, args);
            case TABU_FULL -> tabu(rotationAllowed, order, TabuMode.FULL_STATE, args);
            case TABU_MOVE -> tabu(rotationAllowed, order, TabuMode.MOVE, args);
            case TABU_POSITION -> tabu(rotationAllowed, order, TabuMode.POSITION_ATTRIBUTE, args);
            case TABU_ORDER -> tabu(rotationAllowed, order, TabuMode.RELATIVE_ORDER_ATTRIBUTE, args);
            case TABU_ADJACENCY -> tabu(rotationAllowed, order, TabuMode.ADJACENCY_ATTRIBUTE, args);
            case TABU_PLACEMENT -> tabu(rotationAllowed, order, TabuMode.PLACEMENT_ATTRIBUTE, args);
            case SA -> new SimulatedAnnealingSolver(
                    rotationAllowed, order, simulatedAnnealingConfig(args));
            case ACO -> new AntColonySolver(rotationAllowed, order, antColonyConfig(args));
            case ILS -> new IteratedLocalSearchSolver(
                    rotationAllowed, order, iteratedLocalSearchConfig(args));
            case GA -> new GeneticAlgorithmSolver(
                    rotationAllowed, order, geneticAlgorithmConfig(args));
            case ADAPTIVE_GA -> new AdaptiveGeneticAlgorithmSolver(
                    rotationAllowed, order, geneticAlgorithmConfig(args),
                    AdaptiveGeneticAlgorithmConfig.defaults());
            case GA_SA -> hybrid(rotationAllowed, order, args, HybridLocalSearchMode.ANNEALING);
            case GA_TABU -> hybrid(rotationAllowed, order, args, HybridLocalSearchMode.TABU_MOVE);
            default -> SolverFactory.create(
                    id, rotationAllowed, order, 20261012L,
                    ExperimentProfile.STANDARD, SearchLimit.unlimited());
        };
    }

    private static GuillotineSolver guillotine(
            boolean rotationAllowed, ItemOrder order, String[] args) {
        GuillotineRectChoice choice = args.length > 4
                ? GuillotineRectChoice.valueOf(args[4].toUpperCase())
                : GuillotineRectChoice.BSSF;
        GuillotineSplitRule split = args.length > 5
                ? GuillotineSplitRule.valueOf(args[5].toUpperCase())
                : GuillotineSplitRule.SAS;
        boolean merge = args.length <= 6 || parseBoolean(args[6], "是否合并自由矩形");
        return new GuillotineSolver(rotationAllowed, order, choice, split, merge);
    }

    private static TabuSearchSolver tabu(
            boolean rotationAllowed, ItemOrder order, TabuMode mode, String[] args) {
        return new TabuSearchSolver(rotationAllowed, order, mode, new TabuSearchConfig(
                intArg(args, 4, 1_000), intArg(args, 5, 50), intArg(args, 6, 10),
                intArg(args, 7, 300), longArg(args, 8, 20261012L)));
    }

    private static SimulatedAnnealingConfig simulatedAnnealingConfig(String[] args) {
        return new SimulatedAnnealingConfig(
                intArg(args, 4, 500), intArg(args, 5, 0), doubleArg(args, 6, 0.98),
                doubleArg(args, 7, 1.0e-5), intArg(args, 8, 100),
                doubleArg(args, 9, 0.8), intArg(args, 10, 200), longArg(args, 11, 20261012L));
    }

    private static AntColonyConfig antColonyConfig(String[] args) {
        return new AntColonyConfig(
                intArg(args, 4, 50), intArg(args, 5, 300), doubleArg(args, 6, 1.0),
                doubleArg(args, 7, 2.0), doubleArg(args, 8, 0.20), doubleArg(args, 9, 1.0),
                intArg(args, 10, 5), intArg(args, 11, 80), longArg(args, 12, 20261013L));
    }

    private static IteratedLocalSearchConfig iteratedLocalSearchConfig(String[] args) {
        return new IteratedLocalSearchConfig(
                intArg(args, 4, 500), intArg(args, 5, 100), intArg(args, 6, 3),
                intArg(args, 7, 100), longArg(args, 8, 20261013L));
    }

    private static GeneticAlgorithmConfig geneticAlgorithmConfig(String[] args) {
        return new GeneticAlgorithmConfig(
                intArg(args, 4, 100), intArg(args, 5, 500), intArg(args, 6, 3),
                doubleArg(args, 7, 0.9), doubleArg(args, 8, 0.2), intArg(args, 9, 5),
                intArg(args, 10, 100), intArg(args, 11, 20),
                args.length > 12
                        ? GeneticMutationMode.valueOf(args[12].toUpperCase())
                        : GeneticMutationMode.SWAP,
                longArg(args, 13, 20261012L));
    }

    private static PackingAlgorithm hybrid(
            boolean rotationAllowed, ItemOrder order, String[] args, HybridLocalSearchMode mode) {
        HybridGeneticAlgorithmConfig hybrid = new HybridGeneticAlgorithmConfig(
                intArg(args, 14, 5), doubleArg(args, 15, 0.10), intArg(args, 16, 10),
                intArg(args, 17, 300), intArg(args, 18, 5), intArg(args, 19, 20),
                doubleArg(args, 20, 0.90), doubleArg(args, 21, 0.30), intArg(args, 22, 7));
        return new GeneticAlgorithmSolver(
                rotationAllowed, order, geneticAlgorithmConfig(args), mode, hybrid);
    }

    private static int intArg(String[] args, int index, int fallback) {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static long longArg(String[] args, int index, long fallback) {
        return args.length > index ? Long.parseLong(args[index]) : fallback;
    }

    private static double doubleArg(String[] args, int index, double fallback) {
        return args.length > index ? Double.parseDouble(args[index]) : fallback;
    }

    private static boolean parseBoolean(String value, String argumentName) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(argumentName + "必须是 true 或 false，实际为：" + value);
    }
}
