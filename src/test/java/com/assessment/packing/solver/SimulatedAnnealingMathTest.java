package com.assessment.packing.solver;

public final class SimulatedAnnealingMathTest {
    public static void main(String[] args) {
        probabilityMatchesMetropolisFormula();
        lowerTemperatureReducesWorseMoveProbability();
        nonWorseMoveIsAlwaysAccepted();
        System.out.println("SimulatedAnnealingMathTest: 全部通过");
    }

    private static void probabilityMatchesMetropolisFormula() {
        double actual = SimulatedAnnealingSolver.acceptanceProbability(-0.04, 0.05);
        check(Math.abs(actual - Math.exp(-0.8)) < 1.0e-12, "Metropolis概率公式错误");
    }

    private static void lowerTemperatureReducesWorseMoveProbability() {
        double high = SimulatedAnnealingSolver.acceptanceProbability(-0.04, 0.05);
        double low = SimulatedAnnealingSolver.acceptanceProbability(-0.04, 0.005);
        check(low < high, "相同劣解在低温下的接受概率应更小");
    }

    private static void nonWorseMoveIsAlwaysAccepted() {
        check(SimulatedAnnealingSolver.acceptanceProbability(0.0, 0.01) == 1.0,
                "等值解必须接受");
        check(SimulatedAnnealingSolver.acceptanceProbability(0.01, 0.01) == 1.0,
                "改进解必须接受");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
