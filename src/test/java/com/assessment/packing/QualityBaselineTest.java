package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public final class QualityBaselineTest {
    private static final Map<AlgorithmId, Long> MINIMUM_GCUT3_AREA = Map.ofEntries(
            Map.entry(AlgorithmId.SHELF_BAF_GLOBAL, 54_000L),
            Map.entry(AlgorithmId.SHELF_FC, 50_000L),
            Map.entry(AlgorithmId.BOTTOM_LEFT, 55_000L),
            Map.entry(AlgorithmId.CONTINUOUS_BOTTOM_LEFT, 55_000L),
            Map.entry(AlgorithmId.SKYLINE, 58_000L),
            Map.entry(AlgorithmId.SKYLINE_BL_WM, 55_000L),
            Map.entry(AlgorithmId.GUILLOTINE, 49_000L),
            Map.entry(AlgorithmId.MAXRECTS_BEST, 57_000L),
            Map.entry(AlgorithmId.TABU_MOVE, 58_000L),
            Map.entry(AlgorithmId.SA, 58_000L),
            Map.entry(AlgorithmId.ACO, 58_000L),
            Map.entry(AlgorithmId.ILS, 58_000L),
            Map.entry(AlgorithmId.ADAPTIVE_GA, 59_000L),
            Map.entry(AlgorithmId.GA_TABU, 59_000L));

    public static void main(String[] args) throws Exception {
        var instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        var validator = new SolutionValidator();
        check(MINIMUM_GCUT3_AREA.keySet().equals(
                Set.copyOf(SolverFactory.primaryAlgorithms())),
                "质量基线必须完整覆盖工程主入口");

        for (AlgorithmId id : SolverFactory.primaryAlgorithms()) {
            var solver = SolverFactory.create(
                    id, true, ItemOrder.AREA_DESC, 20261012L,
                    ExperimentProfile.QUICK, SearchLimit.unlimited());
            var solution = solver.solve(instance);
            check(validator.validate(instance, solution, true).valid(), id + " 基线解不合法");
            check(solution.packedArea() >= MINIMUM_GCUT3_AREA.get(id),
                    id + " 质量退化：面积=" + solution.packedArea()
                            + "，最低要求=" + MINIMUM_GCUT3_AREA.get(id));
        }
        System.out.println("QualityBaselineTest: 全部通过");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
