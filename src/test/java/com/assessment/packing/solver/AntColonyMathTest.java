package com.assessment.packing.solver;

import java.util.Arrays;
import java.util.Random;

public final class AntColonyMathTest {
    public static void main(String[] args) {
        constructedPathIsAlwaysAPermutation();
        evaporationUsesConfiguredRateAndBounds();
        depositReinforcesOnlyTraversedEdges();
        heuristicBiasActuallyAffectsSelection();
        System.out.println("AntColonyMathTest: 全部通过");
    }

    private static void constructedPathIsAlwaysAPermutation() {
        int n = 20;
        double[][] pheromone = new double[n + 1][n];
        for (double[] row : pheromone) Arrays.fill(row, 1.0);
        double[] desirability = new double[n];
        Arrays.fill(desirability, 1.0);
        Random random = new Random(123L);

        for (int sample = 0; sample < 200; sample++) {
            int[] sequence = AntColonySolver.constructPermutation(
                    pheromone, desirability, 1.0, 2.0, random);
            boolean[] seen = new boolean[n];
            for (int value : sequence) {
                check(value >= 0 && value < n, "排列下标必须在合法范围内");
                check(!seen[value], "同一零件不能在排列中重复出现");
                seen[value] = true;
            }
            for (boolean value : seen) check(value, "排列必须包含每一个零件");
        }
    }

    private static void evaporationUsesConfiguredRateAndBounds() {
        double[][] pheromone = {{10.0, 1.0}, {0.01, 4.0}, {2.0, 8.0}};
        AntColonySolver.evaporateAndClamp(pheromone, 0.25, 0.5, 6.0);
        check(close(pheromone[0][0], 6.0), "蒸发后超过上界的值必须截断");
        check(close(pheromone[0][1], 0.75), "普通信息素应乘以保留率1-rho");
        check(close(pheromone[1][0], 0.5), "蒸发后低于下界的值必须抬升");
        check(close(pheromone[1][1], 3.0), "普通信息素蒸发计算错误");
    }

    private static void depositReinforcesOnlyTraversedEdges() {
        double[][] pheromone = new double[4][3];
        AntColonySolver.deposit(pheromone, new int[] {2, 0, 1}, 1.5, 2.0);
        check(close(pheromone[3][2], 1.5), "必须增强起点到首个零件的边");
        check(close(pheromone[2][0], 1.5), "必须增强排列中的相邻边");
        check(close(pheromone[0][1], 1.5), "必须增强排列中的相邻边");
        check(close(pheromone[1][2], 0.0), "没有经过的边不能被增强");
        AntColonySolver.deposit(pheromone, new int[] {2, 0, 1}, 1.5, 2.0);
        check(close(pheromone[3][2], 2.0), "增强后不得超过信息素上界");
    }

    private static void heuristicBiasActuallyAffectsSelection() {
        double[][] pheromone = {{1, 1}, {1, 1}, {1, 1}};
        double[] desirability = {1.0e-12, 1.0};
        int preferredFirst = 0;
        Random random = new Random(456L);
        for (int sample = 0; sample < 100; sample++) {
            int[] sequence = AntColonySolver.constructPermutation(
                    pheromone, desirability, 0.0, 2.0, random);
            if (sequence[0] == 1) preferredFirst++;
        }
        check(preferredFirst == 100, "启发式指数必须实际影响转移概率");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0e-12;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
