package com.assessment.packing.experiment;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.MaxRectsBestStatistics;

import java.util.List;

public record RunObservation(
        String experiment,
        String budgetKind,
        long budgetValue,
        AlgorithmId algorithm,
        Instance instance,
        int repetition,
        long seed,
        Solution solution,
        boolean valid,
        String validationErrors,
        long evaluations,
        long iterations,
        MaxRectsBestStatistics maxRectsBestStatistics,
        List<ConvergencePoint> trace) {

    public RunObservation {
        if (maxRectsBestStatistics == null) {
            maxRectsBestStatistics = MaxRectsBestStatistics.empty();
        }
        trace = List.copyOf(trace);
    }

    public double utilizationPercent() {
        return solution.utilization(instance) * 100.0;
    }

    public double elapsedMilliseconds() {
        return solution.elapsedNanos() / 1_000_000.0;
    }
}
