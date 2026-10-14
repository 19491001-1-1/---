package com.assessment.packing;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ContinuousBottomLeftSolver;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.validation.SolutionValidator;

import java.util.List;

public final class ContinuousBottomLeftSolverTest {
    public static void main(String[] args) {
        packsKnownExample();
        fillsSpaceAboveShortRectangle();
        slidesDownThenLeft();
        usesRotationWhenNeeded();
        System.out.println("ContinuousBottomLeftSolverTest: 全部通过");
    }

    private static void packsKnownExample() {
        Instance in = new Instance("known", 10, 8, List.of(
                new Item(1, 6, 4), new Item(2, 4, 4), new Item(3, 5, 4)));
        Solution s = new ContinuousBottomLeftSolver(false, ItemOrder.INPUT).solve(in);
        check(s.packedArea() == 60, "已知示例面积应为60");
        check(new SolutionValidator().validate(in, s, false).valid(), "已知示例必须合法");
    }

    private static void fillsSpaceAboveShortRectangle() {
        Instance in = new Instance("gap", 10, 10, List.of(
                new Item(1, 6, 10), new Item(2, 4, 4), new Item(3, 4, 6)));
        Solution s = new ContinuousBottomLeftSolver(false, ItemOrder.INPUT).solve(in);
        check(s.packedArea() == 100, "连续移动式应填满矮零件上方空间");
        check(new SolutionValidator().validate(in, s, false).valid(), "空隙填充方案必须合法");
    }

    private static void slidesDownThenLeft() {
        Instance in = new Instance("slide", 10, 10, List.of(
                new Item(1, 4, 4), new Item(2, 3, 3)));
        Solution s = new ContinuousBottomLeftSolver(false, ItemOrder.INPUT).solve(in);
        Placement second = s.placements().get(1);
        check(second.x() == 4 && second.y() == 0,
                "第二个零件应从顶部下降并左移至第一个零件右侧");
    }

    private static void usesRotationWhenNeeded() {
        Instance in = new Instance("rotation", 4, 6, List.of(new Item(1, 6, 4)));
        Solution s = new ContinuousBottomLeftSolver(true, ItemOrder.INPUT).solve(in);
        check(s.placements().size() == 1 && s.placements().get(0).rotated(), "应旋转后放入");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
