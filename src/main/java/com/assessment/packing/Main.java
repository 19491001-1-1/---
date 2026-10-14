package com.assessment.packing;

import com.assessment.packing.solver.AntColonyStatistics;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmStatistics;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.experiment.CommandLineSolverFactory;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.IteratedLocalSearchStatistics;
import com.assessment.packing.solver.MaxRectsBestSolver;
import com.assessment.packing.solver.GeneticAlgorithmStatistics;
import com.assessment.packing.solver.HybridGeneticAlgorithmStatistics;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuSearchSolver;
import com.assessment.packing.solver.SimulatedAnnealingStatistics;
import com.assessment.packing.solver.TabuSearchStatistics;
import com.assessment.packing.validation.SolutionValidator;
import com.assessment.packing.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    public static void main(String[] args) throws IOException {
        if (args.length > 0 && (args[0].equalsIgnoreCase("help")
                || args[0].equals("--help") || args[0].equals("-h"))) {
            printHelp();
            return;
        }
        Path dataDirectory;
        boolean rotationAllowed;
        ItemOrder order;
        PackingAlgorithm solver;
        if (args.length > 0 && args[0].equals("--config")) {
            if (args.length != 2) throw new IllegalArgumentException("--config 后必须提供一个配置文件");
            PackingRunConfiguration configuration = PackingRunConfiguration.read(Path.of(args[1]));
            dataDirectory = configuration.dataDirectory();
            rotationAllowed = configuration.rotationAllowed();
            order = configuration.order();
            solver = SolverFactory.create(
                    configuration.algorithm(), rotationAllowed, order, configuration.seed(),
                    configuration.profile(), configuration.searchLimit(), configuration.decoder());
        } else {
            dataDirectory = Path.of(args.length > 0 ? args[0] : "gcut01");
            rotationAllowed = args.length <= 1 || parseBoolean(args[1], "是否允许旋转");
            order = args.length > 2 ? ItemOrder.parse(args[2]) : ItemOrder.AREA_DESC;
            String algorithm = args.length > 3 ? args[3].toUpperCase() : "ADAPTIVE_GA";
            solver = args.length <= 3
                    ? SolverFactory.create(
                            com.assessment.packing.experiment.AlgorithmId.ADAPTIVE_GA,
                            rotationAllowed, order, 20261012L,
                            com.assessment.packing.experiment.ExperimentProfile.QUICK,
                            com.assessment.packing.solver.SearchLimit.unlimited())
                    : CommandLineSolverFactory.create(algorithm, rotationAllowed, order, args);
        }
        List<Path> files;
        files = com.assessment.packing.io.InstanceFiles.listGcutFiles(dataDirectory);
        if (files.isEmpty()) throw new IllegalArgumentException("目录中未找到gcut*.txt：" + dataDirectory);

        InstanceReader reader = new InstanceReader();
        SolutionValidator validator = new SolutionValidator();

        System.out.printf("算法：%s%n", solver.name());
        System.out.println("实例     箱子        零件  已放入  装入面积    利用率       耗时(ms)  验证");

        for (Path file : files) {
            Instance instance = reader.read(file);
            Solution solution = solver.solve(instance);
            ValidationResult result = validator.validate(instance, solution, rotationAllowed);
            System.out.printf("%-8s %-11s %4d %6d %10d %10.4f%% %10.3f  %s%n",
                    instance.name(), instance.binWidth() + "x" + instance.binHeight(), instance.items().size(),
                    solution.placements().size(), solution.packedArea(), solution.utilization(instance) * 100,
                    solution.elapsedNanos() / 1_000_000.0, result.valid() ? "通过" : "失败：" + result.errors());
            if (solver instanceof MaxRectsBestSolver bestSolver) {
                var statistics = bestSolver.lastStatistics();
                System.out.printf("         BEST统计：采用=%s，并列最优=%s%n",
                        statistics.selectedHeuristic(),
                        statistics.tiedBestHeuristics());
                for (var entry : statistics.entries()) {
                    System.out.printf("           %-4s 面积=%d，零件=%d，耗时=%.3f ms%n",
                            entry.heuristic(), entry.packedArea(), entry.placedItems(),
                            entry.elapsedMilliseconds());
                }
            }
            if (solver instanceof TabuSearchSolver tabuSolver) {
                TabuSearchStatistics statistics = tabuSolver.lastStatistics();
                System.out.printf("         搜索统计：迭代=%d，评价邻居=%d，命中禁忌=%d，历史最好改进=%d，接受劣解=%d，特赦=%d，扩大邻域=%d，停滞=%d%n",
                        statistics.completedIterations(), statistics.evaluatedNeighbors(),
                        statistics.tabuHits(),
                        statistics.bestImprovements(), statistics.acceptedWorseMoves(),
                        statistics.aspirationAcceptances(), statistics.expandedNeighborhoods(),
                        statistics.stalledIterations());
            }
            if (solver instanceof SimulatedAnnealingSolver annealingSolver) {
                SimulatedAnnealingStatistics statistics = annealingSolver.lastStatistics();
                System.out.printf("         退火统计：温度层=%d，初温采样=%d，搜索评价=%d，接受改进=%d，接受等值=%d，接受劣解=%d，拒绝=%d，历史最好改进=%d，接受率=%.2f%%，温度=%.6g→%.6g%n",
                        statistics.completedTemperatureLevels(),
                        statistics.temperatureSampleEvaluations(),
                        statistics.searchNeighborEvaluations(),
                        statistics.acceptedImprovingMoves(),
                        statistics.acceptedEqualMoves(),
                        statistics.acceptedWorseMoves(),
                        statistics.rejectedMoves(),
                        statistics.bestImprovements(),
                        statistics.acceptanceRate() * 100.0,
                        statistics.initialTemperature(),
                        statistics.finalTemperature());
            }
            if (solver instanceof GeneticAlgorithmSolver geneticSolver) {
                GeneticAlgorithmStatistics statistics = geneticSolver.lastStatistics();
                System.out.printf("         遗传统计：代数=%d，有效种群=%d，适应度评价=%d，缓存命中=%d，交叉=%d，变异=%d，拒绝重复=%d，历史最好改进=%d，缓存命中率=%.2f%%%n",
                        statistics.completedGenerations(),
                        statistics.effectivePopulationSize(),
                        statistics.fitnessEvaluations(),
                        statistics.fitnessCacheHits(),
                        statistics.crossoverEvents(),
                        statistics.mutationEvents(),
                        statistics.duplicateRejections(),
                        statistics.bestImprovements(),
                        statistics.cacheHitRate() * 100.0);
                if (geneticSolver.hybridEnabled()) {
                    HybridGeneticAlgorithmStatistics hybrid = geneticSolver.lastHybridStatistics();
                    System.out.printf("         局部统计：调用=%d，候选评价=%d，成功改进=%d，累计面积增益=%d，接受劣解=%d，特赦=%d，重复结果=%d，跳过已优化=%d，改进率=%.2f%%%n",
                            hybrid.localSearchCalls(),
                            hybrid.localCandidateEvaluations(),
                            hybrid.localImprovements(),
                            hybrid.totalAreaGain(),
                            hybrid.acceptedWorseMoves(),
                            hybrid.aspirationAcceptances(),
                            hybrid.duplicateLocalResults(),
                            hybrid.skippedPreviouslyOptimized(),
                            hybrid.improvementRate() * 100.0);
                }
                if (geneticSolver.adaptiveEnabled()) {
                    AdaptiveGeneticAlgorithmStatistics adaptive =
                            geneticSolver.lastAdaptiveStatistics();
                    System.out.printf("         自适应统计：交叉率均值=%.4f（%.4f~%.4f），变异率均值=%.4f（%.4f~%.4f）%n",
                            adaptive.averageCrossoverRate(),
                            adaptive.minimumObservedCrossoverRate(),
                            adaptive.maximumObservedCrossoverRate(),
                            adaptive.averageMutationRate(),
                            adaptive.minimumObservedMutationRate(),
                            adaptive.maximumObservedMutationRate());
                }
            }
            if (solver instanceof AntColonySolver antColonySolver) {
                AntColonyStatistics statistics = antColonySolver.lastStatistics();
                System.out.printf("         蚁群统计：迭代=%d，蚂蚁=%d，构造排列=%d，适应度评价=%d，缓存命中=%d，信息素增强=%d，历史最好改进=%d，停滞=%d%n",
                        statistics.completedIterations(),
                        statistics.antCount(),
                        statistics.constructedPermutations(),
                        statistics.fitnessEvaluations(),
                        statistics.fitnessCacheHits(),
                        statistics.pheromoneDeposits(),
                        statistics.bestImprovements(),
                        statistics.noImprovementIterations());
            }
            if (solver instanceof IteratedLocalSearchSolver ilsSolver) {
                IteratedLocalSearchStatistics statistics = ilsSolver.lastStatistics();
                System.out.printf("         ILS统计：迭代=%d，适应度评价=%d，缓存命中=%d，扰动交换=%d，局部改进=%d，历史最好改进=%d，进入较差盆地=%d%n",
                        statistics.completedIterations(), statistics.fitnessEvaluations(),
                        statistics.fitnessCacheHits(), statistics.perturbations(),
                        statistics.localImprovements(), statistics.bestImprovements(),
                        statistics.acceptedWorseBasins());
            }
        }
    }

    static boolean parseBoolean(String value, String argumentName) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(argumentName + "必须是 true 或 false，实际为：" + value);
    }

    private static void printHelp() {
        System.out.println("""
                用法：java -jar rectangle-packing-1.0.0.jar [数据目录] [是否旋转] [输入排序] [算法] [算法参数...]

                默认值：gcut01 true AREA_DESC ADAPTIVE_GA（QUICK配置、Skyline解码）
                示例：java -jar rectangle-packing-1.0.0.jar gcut01 true AREA_DESC ADAPTIVE_GA
                配置：java -jar rectangle-packing-1.0.0.jar --config config/example.properties

                批量实验请运行：
                java -cp rectangle-packing-1.0.0.jar com.assessment.packing.experiment.ExperimentMain --help
                """);
    }
}
