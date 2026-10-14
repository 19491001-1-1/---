package com.assessment.packing;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.util.List;

public final class SkylineSolverTest {
    public static void main(String[] args) {
        packsKnownExampleAndMergesEqualSegments();
        fillsPerfectRectangle();
        usesRotationWhenNeeded();
        skipsImpossibleItemAndContinues();
        System.out.println("SkylineSolverTest: 全部通过");
    }

    private static void packsKnownExampleAndMergesEqualSegments() {
        Instance in = new Instance("known", 10, 10, List.of(
                new Item(1, 6, 4), new Item(2, 4, 4), new Item(3, 4, 6)));
        Solution s = new SkylineSolver(false, ItemOrder.INPUT).solve(in);
        check(s.packedArea() == 64, "已知示例面积应为64");
        check(s.placements().size() == 3, "三个零件都应放入");
        check(new SolutionValidator().validate(in, s, false).valid(), "已知示例必须合法");
    }

    private static void fillsPerfectRectangle() {
        Instance in = new Instance("perfect", 10, 10, List.of(
                new Item(1, 5, 10), new Item(2, 5, 10)));
        Solution s = new SkylineSolver(false, ItemOrder.INPUT).solve(in);
        check(s.packedArea() == 100, "应达到100%利用率");
        check(new SolutionValidator().validate(in, s, false).valid(), "满箱方案必须合法");
    }

    private static void usesRotationWhenNeeded() {
        Instance in = new Instance("rotation", 4, 6, List.of(new Item(1, 6, 4)));
        Solution s = new SkylineSolver(true, ItemOrder.INPUT).solve(in);
        check(s.placements().size() == 1 && s.placements().get(0).rotated(), "应旋转后放入");
    }

    private static void skipsImpossibleItemAndContinues() {
        Instance in = new Instance("skip", 5, 5, List.of(
                new Item(1, 6, 6), new Item(2, 5, 5)));
        Solution s = new SkylineSolver(false, ItemOrder.INPUT).solve(in);
        check(s.packedArea() == 25, "不可放零件不应阻止后续可行零件");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
