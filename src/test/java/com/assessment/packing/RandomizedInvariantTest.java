package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.validation.SolutionValidator;
import com.assessment.packing.validation.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RandomizedInvariantTest {
    private static final List<Instance> INSTANCES = new ArrayList<>();

    @BeforeAll
    static void generateInstances() {
        Random random = new Random(20261014L);
        for (int caseIndex = 0; caseIndex < 20; caseIndex++) {
            int binWidth = 10 + random.nextInt(41);
            int binHeight = 10 + random.nextInt(41);
            int itemCount = random.nextInt(16);
            List<Item> items = new ArrayList<>(itemCount);
            for (int id = 1; id <= itemCount; id++) {
                items.add(new Item(id, 1 + random.nextInt(binWidth * 2),
                        1 + random.nextInt(binHeight * 2)));
            }
            INSTANCES.add(new Instance("random-" + caseIndex, binWidth, binHeight, items));
        }
    }

    @ParameterizedTest
    @EnumSource(AlgorithmId.class)
    void preservesGeometricInvariantsOnRandomInstances(AlgorithmId id) {
        SolutionValidator validator = new SolutionValidator();
        for (int index = 0; index < INSTANCES.size(); index++) {
            Instance instance = INSTANCES.get(index);
            boolean rotationAllowed = index % 2 == 0;
            var solver = SolverFactory.create(
                    id, rotationAllowed, ItemOrder.AREA_DESC,
                    20261014L + index, ExperimentProfile.QUICK,
                    SearchLimit.evaluations(120));
            ValidationResult validation = validator.validate(
                    instance, solver.solve(instance), rotationAllowed);
            assertTrue(validation.valid(),
                    () -> id + " 在随机实例 " + instance.name()
                            + " 上违反几何不变量：" + validation.errors());
        }
    }
}