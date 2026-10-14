package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.validation.SolutionValidator;

import java.util.List;

public final class AllAlgorithmsRobustnessTest {
    public static void main(String[] args) {
        List<Instance> edgeCases = List.of(
                new Instance("empty", 20, 20, List.of()),
                new Instance("single", 20, 20, List.of(new Item(1, 20, 20))),
                new Instance("oversized", 20, 20, List.of(
                        new Item(1, 21, 2), new Item(2, 2, 21))),
                new Instance("mixed", 20, 20, List.of(
                        new Item(1, 12, 7), new Item(2, 8, 13),
                        new Item(3, 5, 5), new Item(4, 30, 1))));

        SolutionValidator validator = new SolutionValidator();
        for (AlgorithmId id : AlgorithmId.values()) {
            for (Instance instance : edgeCases) {
                var solver = SolverFactory.create(
                        id, true, ItemOrder.AREA_DESC, 20261014L,
                        ExperimentProfile.QUICK, SearchLimit.evaluations(200));
                var solution = solver.solve(instance);
                var validation = validator.validate(instance, solution, true);
                check(validation.valid(), id + " 在 " + instance.name()
                        + " 上产生非法解：" + validation.errors());
            }
        }
        System.out.println("AllAlgorithmsRobustnessTest: 全部通过");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
