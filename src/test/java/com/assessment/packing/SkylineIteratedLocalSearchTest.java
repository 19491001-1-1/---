package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.IteratedLocalSearchConfig;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;

public final class SkylineIteratedLocalSearchTest {
    public static void main(String[] args) throws Exception {
        isReproducibleLegalAndBudgeted();
        System.out.println("SkylineIteratedLocalSearchTest: 全部通过");
    }

    private static void isReproducibleLegalAndBudgeted() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        IteratedLocalSearchConfig config = new IteratedLocalSearchConfig(30, 25, 2, 12, 71L);
        IteratedLocalSearchSolver first = new IteratedLocalSearchSolver(
                true, ItemOrder.AREA_DESC, config, SearchLimit.evaluations(90));
        IteratedLocalSearchSolver second = new IteratedLocalSearchSolver(
                true, ItemOrder.AREA_DESC, config, SearchLimit.evaluations(90));
        Solution a = first.solve(instance);
        Solution b = second.solve(instance);
        long baseline = new SkylineSolver(true, ItemOrder.AREA_DESC).solve(instance).packedArea();

        check(a.packedArea() == b.packedArea(), "相同种子应可复现");
        check(a.packedArea() >= baseline, "ILS必须保留初始解，不能退化");
        check(first.lastStatistics().fitnessEvaluations() <= 90, "不得超过评价预算");
        check(new SolutionValidator().validate(instance, a, true).valid(), "ILS布局必须合法");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
