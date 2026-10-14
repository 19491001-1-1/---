#!/bin/zsh
set -euo pipefail

project_dir="${0:A:h:h}"
cd "$project_dir"

output_dir="out"
rm -rf "$output_dir"
mkdir -p "$output_dir"

# 原生 JUnit 测试依赖 JUnit 运行时，只通过 ./mvnw test 执行。
# 本脚本仅保留不依赖 Maven 的断言式回归测试入口。
find src/main/java src/test/java -name '*.java' \
  ! -name 'RegressionSuiteTest.java' \
  ! -name 'ModelValidationTest.java' \
  ! -name 'AlgorithmCatalogTest.java' \
  ! -name 'RandomizedInvariantTest.java' \
  ! -name 'PackingRunConfigurationTest.java' \
  ! -name 'PackingDecoderIntegrationTest.java' \
  ! -name 'ExperimentInfrastructureTest.java' \
  ! -name '*JUnitTest.java' -print0 \
  | xargs -0 javac -encoding UTF-8 -d "$output_dir"

test_classes=(
  com.assessment.packing.ShelfSolverTest
  com.assessment.packing.ShelfFloorCeilingTest
  com.assessment.packing.solver.ItemOrderingTest
  com.assessment.packing.BottomLeftSolverTest
  com.assessment.packing.ContinuousBottomLeftSolverTest
  com.assessment.packing.SkylineSolverTest
  com.assessment.packing.solver.SkylineScoringTest
  com.assessment.packing.solver.SkylineGlobalSolverTest
  com.assessment.packing.solver.GuillotineSolverTest
  com.assessment.packing.MaxRectsSolverTest
  com.assessment.packing.solver.MaxRectsGeometryTest
  com.assessment.packing.SkylineTabuSearchTest
  com.assessment.packing.SkylineSimulatedAnnealingTest
  com.assessment.packing.solver.SimulatedAnnealingMathTest
  com.assessment.packing.SkylineAntColonyTest
  com.assessment.packing.SkylineIteratedLocalSearchTest
  com.assessment.packing.solver.AntColonyMathTest
  com.assessment.packing.SkylineGeneticAlgorithmTest
  com.assessment.packing.SkylineAdaptiveGeneticAlgorithmTest
  com.assessment.packing.solver.GeneticOperatorsTest
  com.assessment.packing.HybridGeneticAlgorithmTest
  com.assessment.packing.AllAlgorithmsRobustnessTest
  com.assessment.packing.QualityBaselineTest
  com.assessment.packing.InputInfrastructureTest
  com.assessment.packing.experiment.CsvEscaperTest
  com.assessment.packing.ExperimentFrameworkTest
)

for test_class in "${test_classes[@]}"; do
  java -ea -cp "$output_dir" "$test_class"
done

echo "All ${#test_classes[@]} test suites passed."
