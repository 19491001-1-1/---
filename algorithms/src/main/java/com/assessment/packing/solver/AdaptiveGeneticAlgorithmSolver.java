package com.assessment.packing.solver;

/** 使用种群适应度动态调整交叉率和变异率的遗传算法。 */
public final class AdaptiveGeneticAlgorithmSolver extends GeneticAlgorithmSolver {
    public AdaptiveGeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            AdaptiveGeneticAlgorithmConfig adaptiveConfig) {
        this(rotationAllowed, initialOrder, config, adaptiveConfig, SearchLimit.unlimited());
    }

    public AdaptiveGeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            AdaptiveGeneticAlgorithmConfig adaptiveConfig,
            SearchLimit limit) {
        super(rotationAllowed, initialOrder, config, adaptiveConfig, limit);
    }

    public AdaptiveGeneticAlgorithmSolver(
            boolean rotationAllowed,
            ItemOrder initialOrder,
            GeneticAlgorithmConfig config,
            AdaptiveGeneticAlgorithmConfig adaptiveConfig,
            SearchLimit limit,
            PackingDecoder decoder) {
        super(rotationAllowed, initialOrder, config, adaptiveConfig, limit, decoder);
    }
}
