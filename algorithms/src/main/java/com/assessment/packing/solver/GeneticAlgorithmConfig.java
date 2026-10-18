package com.assessment.packing.solver;

public record GeneticAlgorithmConfig(
        int populationSize,
        int maxGenerations,
        int tournamentSize,
        double crossoverRate,
        double mutationRate,
        int eliteCount,
        int maxNoImprovementGenerations,
        int duplicateRetries,
        GeneticMutationMode mutationMode,
        long seed) {

    public GeneticAlgorithmConfig {
        if (populationSize < 2) throw new IllegalArgumentException("种群大小至少为2");
        if (maxGenerations <= 0) throw new IllegalArgumentException("最大代数必须为正数");
        if (tournamentSize <= 0) throw new IllegalArgumentException("锦标赛规模必须为正数");
        if (!(crossoverRate >= 0.0 && crossoverRate <= 1.0)) {
            throw new IllegalArgumentException("交叉概率必须在0和1之间");
        }
        if (!(mutationRate >= 0.0 && mutationRate <= 1.0)) {
            throw new IllegalArgumentException("变异概率必须在0和1之间");
        }
        if (eliteCount <= 0 || eliteCount >= populationSize) {
            throw new IllegalArgumentException("精英数量必须大于0且小于种群大小");
        }
        if (maxNoImprovementGenerations <= 0) {
            throw new IllegalArgumentException("无改进代数上限必须为正数");
        }
        if (duplicateRetries <= 0) throw new IllegalArgumentException("重复重试次数必须为正数");
        if (mutationMode == null) throw new IllegalArgumentException("变异方式不能为空");
    }

    public static GeneticAlgorithmConfig defaults(long seed) {
        return new GeneticAlgorithmConfig(
                100, 500, 3, 0.9, 0.2, 5, 100, 20,
                GeneticMutationMode.SWAP, seed);
    }
}
