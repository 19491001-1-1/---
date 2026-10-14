package com.assessment.packing;

import com.assessment.packing.solver.AntColonyMathTest;
import com.assessment.packing.experiment.CsvEscaperTest;
import com.assessment.packing.solver.GeneticOperatorsTest;
import com.assessment.packing.solver.GuillotineSolverTest;
import com.assessment.packing.solver.ItemOrderingTest;
import com.assessment.packing.solver.MaxRectsGeometryTest;
import com.assessment.packing.solver.SimulatedAnnealingMathTest;
import com.assessment.packing.solver.SkylineGlobalSolverTest;
import com.assessment.packing.solver.SkylineScoringTest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;

/** Bridges the dependency-free regression programs into the JUnit test lifecycle. */
final class RegressionSuiteTest {
    @TestFactory
    List<DynamicTest> regressionSuites() {
        return List.of(
                suite("ShelfSolver", ShelfSolverTest::main),
                suite("ShelfFloorCeiling", ShelfFloorCeilingTest::main),
                suite("ItemOrdering", ItemOrderingTest::main),
                suite("BottomLeftSolver", BottomLeftSolverTest::main),
                suite("ContinuousBottomLeftSolver", ContinuousBottomLeftSolverTest::main),
                suite("SkylineSolver", SkylineSolverTest::main),
                suite("SkylineScoring", SkylineScoringTest::main),
                suite("SkylineGlobalSolver", SkylineGlobalSolverTest::main),
                suite("GuillotineSolver", GuillotineSolverTest::main),
                suite("MaxRectsSolver", MaxRectsSolverTest::main),
                suite("MaxRectsGeometry", MaxRectsGeometryTest::main),
                suite("SkylineTabuSearch", SkylineTabuSearchTest::main),
                suite("SkylineSimulatedAnnealing", SkylineSimulatedAnnealingTest::main),
                suite("SimulatedAnnealingMath", SimulatedAnnealingMathTest::main),
                suite("SkylineAntColony", SkylineAntColonyTest::main),
                suite("SkylineIteratedLocalSearch", SkylineIteratedLocalSearchTest::main),
                suite("AntColonyMath", AntColonyMathTest::main),
                suite("SkylineGeneticAlgorithm", SkylineGeneticAlgorithmTest::main),
                suite("SkylineAdaptiveGeneticAlgorithm", SkylineAdaptiveGeneticAlgorithmTest::main),
                suite("GeneticOperators", GeneticOperatorsTest::main),
                suite("HybridGeneticAlgorithm", HybridGeneticAlgorithmTest::main),
                suite("AllAlgorithmsRobustness", AllAlgorithmsRobustnessTest::main),
                suite("QualityBaseline", QualityBaselineTest::main),
                suite("InputInfrastructure", InputInfrastructureTest::main),
                suite("CsvEscaper", CsvEscaperTest::main),
                suite("ExperimentFramework", ExperimentFrameworkTest::main));
    }

    private static DynamicTest suite(String name, MainMethod main) {
        return DynamicTest.dynamicTest(name, () -> main.run(new String[0]));
    }

    @FunctionalInterface
    private interface MainMethod {
        void run(String[] args) throws Exception;
    }
}
