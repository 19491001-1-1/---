package com.assessment.packing.solver;

/** GA局部学习参数；两种混合版本共用评价预算和触发策略。 */
public record HybridGeneticAlgorithmConfig(
        int localSearchInterval,
        double localSearchFraction,
        int maxLocalIndividuals,
        int localEvaluationBudget,
        int localNoImprovementLimit,
        int localNeighborhoodSize,
        double annealingCoolingRate,
        double annealingInitialAcceptanceProbability,
        int tabuTenure) {

    public HybridGeneticAlgorithmConfig {
        if (localSearchInterval <= 0) throw new IllegalArgumentException("局部搜索间隔必须为正数");
        if (!(localSearchFraction > 0.0 && localSearchFraction <= 1.0)) {
            throw new IllegalArgumentException("局部搜索比例必须在0和1之间");
        }
        if (maxLocalIndividuals <= 0) throw new IllegalArgumentException("每次局部优化个体上限必须为正数");
        if (localEvaluationBudget <= 0) throw new IllegalArgumentException("局部评价预算必须为正数");
        if (localNoImprovementLimit <= 0) throw new IllegalArgumentException("局部无改进上限必须为正数");
        if (localNeighborhoodSize <= 0) throw new IllegalArgumentException("局部邻域大小必须为正数");
        if (!(annealingCoolingRate > 0.0 && annealingCoolingRate < 1.0)) {
            throw new IllegalArgumentException("局部退火降温率必须在0和1之间");
        }
        if (!(annealingInitialAcceptanceProbability > 0.0
                && annealingInitialAcceptanceProbability < 1.0)) {
            throw new IllegalArgumentException("局部退火初始接受率必须在0和1之间");
        }
        if (tabuTenure <= 0) throw new IllegalArgumentException("局部禁忌期限必须为正数");
    }

    public static HybridGeneticAlgorithmConfig defaults() {
        return new HybridGeneticAlgorithmConfig(
                5, 0.10, 10, 300, 5, 20, 0.90, 0.30, 7);
    }
}
