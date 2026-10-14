package com.assessment.packing.bench;

import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.ItemOrder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * 每个搜索算法在固定评价预算下完成一次求解的耗时。
 * 分母是评价数，因此可以直接换算「每秒评价次数」，并与解码器吞吐对照。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SearchEvaluationBenchmark {

    @State(Scope.Benchmark)
    public static class SearchState {
        @Param({"gcut3", "gcut12"})
        public String instanceName;

        @Param({"TABU_MOVE", "SA", "ACO", "ILS", "GA", "ADAPTIVE_GA"})
        public String algorithm;

        @Param({"20000"})
        public long budget;

        Instance instance;

        @Setup(Level.Trial)
        public void setUp() {
            instance = BenchInstances.load(instanceName);
        }
    }

    @Benchmark
    public long solveWithEvaluationBudget(SearchState state) {
        var solver = SolverFactory.create(
                com.assessment.packing.experiment.AlgorithmId.valueOf(state.algorithm),
                true,
                ItemOrder.AREA_DESC,
                20261012L,
                ExperimentProfile.STANDARD,
                SearchLimit.evaluations(state.budget));
        return solver.solve(state.instance).packedArea();
    }
}
