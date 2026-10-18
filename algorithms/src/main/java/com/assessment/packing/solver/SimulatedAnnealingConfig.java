package com.assessment.packing.solver;

/** 模拟退火参数。attemptsPerTemperature为0时自动使用max(50, 10×零件数)。 */
public record SimulatedAnnealingConfig(
        int maxTemperatureLevels,
        int attemptsPerTemperature,
        double coolingRate,
        double minimumTemperature,
        int maxNoImprovementLevels,
        double targetInitialAcceptanceProbability,
        int initialTemperatureSamples,
        long seed) {

    public SimulatedAnnealingConfig {
        if (maxTemperatureLevels <= 0) throw new IllegalArgumentException("最大温度层数必须为正数");
        if (attemptsPerTemperature < 0) throw new IllegalArgumentException("每层尝试次数不能为负数");
        if (!(coolingRate > 0.0 && coolingRate < 1.0)) {
            throw new IllegalArgumentException("降温系数必须在0和1之间");
        }
        if (!(minimumTemperature > 0.0) || !Double.isFinite(minimumTemperature)) {
            throw new IllegalArgumentException("最低温度必须是有限正数");
        }
        if (maxNoImprovementLevels <= 0) throw new IllegalArgumentException("无改进层数上限必须为正数");
        if (!(targetInitialAcceptanceProbability > 0.0
                && targetInitialAcceptanceProbability < 1.0)) {
            throw new IllegalArgumentException("初始劣解接受率必须在0和1之间");
        }
        if (initialTemperatureSamples <= 0) throw new IllegalArgumentException("初温采样次数必须为正数");
    }

    public int attemptsFor(int itemCount) {
        return attemptsPerTemperature == 0 ? Math.max(50, 10 * itemCount) : attemptsPerTemperature;
    }

    public static SimulatedAnnealingConfig defaults(long seed) {
        return new SimulatedAnnealingConfig(
                500, 0, 0.98, 1.0e-5, 100, 0.8, 200, seed);
    }
}
