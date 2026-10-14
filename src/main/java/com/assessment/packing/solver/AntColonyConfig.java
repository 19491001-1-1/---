package com.assessment.packing.solver;

/** 排列型蚁群算法参数。 */
public record AntColonyConfig(
        int antCount,
        int maxIterations,
        double pheromoneAlpha,
        double heuristicBeta,
        double evaporationRate,
        double depositStrength,
        int eliteAnts,
        int maxNoImprovementIterations,
        long seed) {

    public AntColonyConfig {
        if (antCount <= 0) throw new IllegalArgumentException("蚂蚁数量必须为正数");
        if (maxIterations <= 0) throw new IllegalArgumentException("最大迭代数必须为正数");
        if (!Double.isFinite(pheromoneAlpha) || pheromoneAlpha < 0.0) {
            throw new IllegalArgumentException("信息素指数必须是非负有限数");
        }
        if (!Double.isFinite(heuristicBeta) || heuristicBeta < 0.0) {
            throw new IllegalArgumentException("启发式指数必须是非负有限数");
        }
        if (!Double.isFinite(evaporationRate)
                || evaporationRate <= 0.0 || evaporationRate >= 1.0) {
            throw new IllegalArgumentException("蒸发率必须介于0和1之间");
        }
        if (!Double.isFinite(depositStrength) || depositStrength <= 0.0) {
            throw new IllegalArgumentException("信息素增强系数必须为正有限数");
        }
        if (eliteAnts <= 0 || eliteAnts > antCount) {
            throw new IllegalArgumentException("精英蚂蚁数必须介于1和蚂蚁总数之间");
        }
        if (maxNoImprovementIterations <= 0) {
            throw new IllegalArgumentException("无改进迭代上限必须为正数");
        }
    }

    public static AntColonyConfig defaults(long seed) {
        return new AntColonyConfig(50, 300, 1.0, 2.0,
                0.20, 1.0, 5, 80, seed);
    }
}
