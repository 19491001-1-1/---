package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmCatalog;
import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.PackingDecoders;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.validation.SolutionValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PackingDecoderIntegrationTest {
    private static final Instance INSTANCE = new Instance(
            "decoder-integration", 10, 8,
            List.of(new Item(1, 6, 4), new Item(2, 4, 4),
                    new Item(3, 5, 3), new Item(4, 3, 2)));

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("optimizerDecoderPairs")
    void everyOptimizerSupportsEveryDecoder(
            AlgorithmId algorithm, PackingDecoderType decoderType) {
        var solver = SolverFactory.create(
                algorithm, true, ItemOrder.AREA_DESC, 20261014L,
                ExperimentProfile.QUICK, SearchLimit.evaluations(80), decoderType);
        var solution = solver.solve(INSTANCE);
        var validation = new SolutionValidator().validate(INSTANCE, solution, true);

        assertTrue(validation.valid(),
                () -> algorithm + " + " + decoderType + ": " + validation.errors());
        assertTrue(solution.packedArea() > 0,
                () -> algorithm + " + " + decoderType + " 未放置任何零件");
    }

    @ParameterizedTest
    @EnumSource(PackingDecoderType.class)
    void everyRegisteredDecoderHonorsPriorityOrder(PackingDecoderType decoderType) {
        Item first = new Item(1, 10, 6);
        Item second = new Item(2, 10, 5);
        Instance instance = new Instance("priority", 10, 10, List.of(first, second));
        var decoder = PackingDecoders.create(decoderType, false);

        var firstResult = decoder.solveInPriorityOrder(instance, List.of(first, second));
        var secondResult = decoder.solveInPriorityOrder(instance, List.of(second, first));

        assertEquals(List.of(1), firstResult.placements().stream()
                .map(placement -> placement.item().id()).toList(), decoderType.toString());
        assertEquals(List.of(2), secondResult.placements().stream()
                .map(placement -> placement.item().id()).toList(), decoderType.toString());
    }

    private static Stream<Arguments> optimizerDecoderPairs() {
        return AlgorithmCatalog.optimizerAlgorithms().stream()
                .flatMap(algorithm -> Stream.of(PackingDecoderType.values())
                        .map(decoder -> Arguments.of(algorithm, decoder)));
    }
}
