package com.assessment.packing;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.ShelfFloorCeilingSolver;
import com.assessment.packing.solver.ShelfHeuristic;
import com.assessment.packing.solver.ShelfSelectionMode;
import com.assessment.packing.solver.ShelfSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.util.List;

public final class ShelfFloorCeilingTest {
    public static void main(String[] args) {
        ceilingBackfillIsRealAndLegal();
        globalShelfIsRegisteredBehaviorNotJustAName();
        System.out.println("ShelfFloorCeilingTest: 全部通过");
    }

    private static void ceilingBackfillIsRealAndLegal() {
        Instance instance = new Instance("floor-ceiling", 10, 10, List.of(
                new Item(1, 6, 6), new Item(2, 5, 4),
                new Item(3, 5, 4), new Item(4, 4, 2)));
        Solution solution = new ShelfFloorCeilingSolver(false).solve(instance);
        check(solution.placements().size() == 4, "天花板回填应放入普通NF遗漏的第4件");
        check(solution.placements().stream().anyMatch(p -> p.item().id() == 4
                        && p.x() == 6 && p.y() == 4),
                "第4件应从第一层右上角向左回填");
        check(new SolutionValidator().validate(instance, solution, false).valid(),
                "Floor-Ceiling布局必须合法");
    }

    private static void globalShelfIsRegisteredBehaviorNotJustAName() {
        Instance instance = new Instance("global", 10, 10, List.of(
                new Item(1, 6, 6), new Item(2, 5, 4), new Item(3, 4, 4)));
        ShelfSolver solver = new ShelfSolver(
                false, ItemOrder.INPUT, ShelfHeuristic.NEXT_FIT,
                false, ShelfSelectionMode.GLOBAL);
        Solution solution = solver.solve(instance);
        check(solver.name().contains("GLOBAL"), "GLOBAL模式名称必须明确");
        check(new SolutionValidator().validate(instance, solution, false).valid(),
                "Shelf-GLOBAL布局必须合法");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
