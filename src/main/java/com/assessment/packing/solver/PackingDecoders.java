package com.assessment.packing.solver;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;

import java.util.List;

public final class PackingDecoders {
    private PackingDecoders() {}

    public static PackingDecoder create(PackingDecoderType type, boolean rotationAllowed) {
        return switch (type) {
            case SHELF_BAF -> adapter("Shelf-BAF", () ->
                    new ShelfSolver(rotationAllowed, ItemOrder.INPUT,
                            ShelfHeuristic.BEST_AREA_FIT, false));
            case FLOOR_CEILING -> adapter("Shelf-Floor-Ceiling", () ->
                    new ShelfFloorCeilingSolver(rotationAllowed, ItemOrder.INPUT));
            case BOTTOM_LEFT -> adapter("Bottom-Left", () ->
                    new BottomLeftSolver(rotationAllowed, ItemOrder.INPUT));
            case CONTINUOUS_BOTTOM_LEFT -> adapter("Continuous Bottom-Left", () ->
                    new ContinuousBottomLeftSolver(rotationAllowed, ItemOrder.INPUT));
            case SKYLINE -> new SkylineSolver(rotationAllowed, ItemOrder.INPUT);
            case SKYLINE_BL_WM -> adapter("Skyline-BL-WM", () ->
                    new SkylineGlobalSolver(rotationAllowed, ItemOrder.INPUT,
                            SkylinePlacementHeuristic.BOTTOM_LEFT, true));
            case MAXRECTS -> adapter("MaxRects-BEST", () ->
                    new MaxRectsBestSolver(rotationAllowed, ItemOrder.INPUT, false));
            case GUILLOTINE -> adapter("Guillotine-BSSF-SAS-RM", () ->
                    new GuillotineSolver(rotationAllowed, ItemOrder.INPUT,
                            GuillotineRectChoice.BSSF, GuillotineSplitRule.SAS, true, false));
        };
    }

    private static PackingDecoder adapter(
            String name, java.util.function.Supplier<PackingAlgorithm> solverFactory) {
        return new PackingDecoder() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Solution solveInPriorityOrder(Instance instance, List<Item> priority) {
                Instance ordered = new Instance(
                        instance.name(), instance.binWidth(), instance.binHeight(), priority);
                PackingAlgorithm solver = solverFactory.get();
                Solution decoded = solver.solve(ordered);
                return new Solution(name + " decoder", decoded.placements(), decoded.elapsedNanos());
            }
        };
    }
}
