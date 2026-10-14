package com.assessment.packing.solver;

import java.util.Arrays;

public final class GeneticOperatorsTest {
    public static void main(String[] args) {
        orderCrossoverMatchesWorkedExample();
        everyMutationPreservesPermutation();
        System.out.println("GeneticOperatorsTest: 全部通过");
    }

    private static void orderCrossoverMatchesWorkedExample() {
        int[] first = {1, 2, 3, 4, 5, 6, 7, 8};
        int[] second = {5, 7, 2, 1, 8, 4, 6, 3};
        int[] child = GeneticAlgorithmSolver.orderCrossover(first, second, 2, 4);
        check(Arrays.equals(child, new int[] {1, 8, 3, 4, 5, 6, 7, 2}),
                "OX结果与手算示例不一致：" + Arrays.toString(child));
        check(isPermutation(child), "OX子代必须保持合法排列");
        check(Arrays.equals(first, new int[] {1, 2, 3, 4, 5, 6, 7, 8}), "交叉不能修改父代");
        check(Arrays.equals(second, new int[] {5, 7, 2, 1, 8, 4, 6, 3}), "交叉不能修改父代");
    }

    private static void everyMutationPreservesPermutation() {
        int[] swapped = {1, 2, 3, 4};
        GeneticAlgorithmSolver.swapMutation(swapped, 0, 3);
        check(Arrays.equals(swapped, new int[] {4, 2, 3, 1}), "交换变异错误");

        int[] inserted = {1, 2, 3, 4};
        GeneticAlgorithmSolver.insertionMutation(inserted, 3, 1);
        check(Arrays.equals(inserted, new int[] {1, 4, 2, 3}), "插入变异错误");

        int[] reversed = {1, 2, 3, 4};
        GeneticAlgorithmSolver.reversalMutation(reversed, 1, 3);
        check(Arrays.equals(reversed, new int[] {1, 4, 3, 2}), "反转变异错误");

        check(isPermutation(swapped) && isPermutation(inserted) && isPermutation(reversed),
                "所有变异都必须保持合法排列");
    }

    private static boolean isPermutation(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < sorted.length; i++) {
            if (sorted[i] != i + 1) return false;
        }
        return true;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
