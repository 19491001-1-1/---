package com.assessment.packing.experiment;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.io.InstanceFiles;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.validation.SolutionValidator;
import com.assessment.packing.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExperimentRunner {
    public record Options(
            Path dataDirectory,
            Path outputDirectory,
            List<AlgorithmId> algorithms,
            boolean rotationAllowed,
            ItemOrder order,
            List<Long> seeds,
            ExperimentProfile profile,
            SearchLimit searchLimit,
            String experimentName,
            String budgetKind,
            long budgetValue,
            boolean repeatDeterministic,
            PackingDecoderType decoderType,
            boolean resume,
            int parallelism) {
        public Options(
                Path dataDirectory, Path outputDirectory, List<AlgorithmId> algorithms,
                boolean rotationAllowed, ItemOrder order, List<Long> seeds,
                ExperimentProfile profile, SearchLimit searchLimit, String experimentName,
                String budgetKind, long budgetValue, boolean repeatDeterministic) {
            this(dataDirectory, outputDirectory, algorithms, rotationAllowed, order, seeds,
                    profile, searchLimit, experimentName, budgetKind, budgetValue,
                    repeatDeterministic, PackingDecoderType.SKYLINE, false, 1);
        }

        /**
         * 环境变量 {@code PACKING_PARALLELISM} 的上限。并行是实验批次的加速手段，不是越多越好：
         * 把机器打满会让每个任务争抢 CPU，既拖长单次运行，也让耗时统计失去参考价值。
         */
        static final int MAX_PARALLELISM = 16;

        public Options {
            Objects.requireNonNull(dataDirectory, "数据目录不能为空");
            Objects.requireNonNull(outputDirectory, "输出目录不能为空");
            Objects.requireNonNull(algorithms, "算法列表不能为空");
            Objects.requireNonNull(order, "输入排序不能为空");
            Objects.requireNonNull(seeds, "随机种子列表不能为空");
            Objects.requireNonNull(profile, "实验配置不能为空");
            Objects.requireNonNull(searchLimit, "搜索限制不能为空");
            Objects.requireNonNull(decoderType, "解码器类型不能为空");
            if (experimentName == null || experimentName.isBlank()) {
                throw new IllegalArgumentException("实验名称不能为空");
            }
            if (budgetKind == null || budgetKind.isBlank()) {
                throw new IllegalArgumentException("预算类型不能为空");
            }
            if (budgetValue < 0) throw new IllegalArgumentException("预算值不能为负数");
            algorithms = List.copyOf(algorithms);
            seeds = List.copyOf(seeds);
            if (algorithms.isEmpty()) throw new IllegalArgumentException("算法列表不能为空");
            if (seeds.isEmpty()) throw new IllegalArgumentException("随机种子列表不能为空");
            if (parallelism < 1) throw new IllegalArgumentException("并行度必须为正数：" + parallelism);
            if (parallelism > MAX_PARALLELISM) {
                throw new IllegalArgumentException(String.format(
                        "并行度最大为 %d，收到 %d；并行度受限于机器核数，"
                                + "过高只会让任务互相争抢 CPU",
                        MAX_PARALLELISM, parallelism));
            }
            if (parallelism > 1 && "TIME_MS".equals(budgetKind)) {
                throw new IllegalArgumentException(
                        "时间预算实验不能并行：并行会让算法争抢CPU，耗时不再公平");
            }
        }

        /** 默认串行的便捷构造器，保持既有调用点不变。 */
        public Options(
                Path dataDirectory, Path outputDirectory, List<AlgorithmId> algorithms,
                boolean rotationAllowed, ItemOrder order, List<Long> seeds,
                ExperimentProfile profile, SearchLimit searchLimit, String experimentName,
                String budgetKind, long budgetValue, boolean repeatDeterministic,
                PackingDecoderType decoderType, boolean resume) {
            this(dataDirectory, outputDirectory, algorithms, rotationAllowed, order, seeds,
                    profile, searchLimit, experimentName, budgetKind, budgetValue,
                    repeatDeterministic, decoderType, resume, 1);
        }
    }

    public List<RunObservation> run(Options options) throws IOException {
        List<Instance> instances = readInstances(options.dataDirectory());
        ExperimentMetadata.write(options.outputDirectory(), options);
        SolutionValidator validator = new SolutionValidator();
        Path checkpointFile = options.outputDirectory().resolve(".experiment-checkpoint.bin");
        List<RunObservation> runs = options.resume()
                ? new ArrayList<>(RunCheckpoint.load(checkpointFile, options, instances))
                : new ArrayList<>();
        java.util.Set<String> completedKeys = new java.util.HashSet<>();
        for (RunObservation run : runs) completedKeys.add(runKey(run));
        if (!runs.isEmpty()) {
            System.out.printf("断点续跑：已恢复%d次运行%n", runs.size());
        } else if (options.budgetKind().equals("TIME_MS")) {
            warmUpTimedAlgorithms(options, instances.get(0));
        } else if (options.repeatDeterministic()) {
            warmUpRepeatedAlgorithms(options, instances.get(0));
        }
        // 任务清单按「串行执行顺序」展开并按序编号；并行执行时结果写入下标寻址的数组，
        // 因此并行产出的 runs 列表与串行逐位相同。
        List<Task> tasks = new ArrayList<>();
        for (Instance instance : instances) {
            for (AlgorithmId algorithm : options.algorithms()) {
                int repetitions = repetitions(algorithm, options);
                for (int repetition = 0; repetition < repetitions; repetition++) {
                    long seed = options.seeds().get(repetition);
                    String key = runKey(algorithm, instance.name(), repetition + 1, seed);
                    if (!completedKeys.contains(key)) {
                        tasks.add(new Task(instance, algorithm, repetition + 1, seed));
                    }
                }
            }
        }
        int total = completedKeys.size() + tasks.size();
        int completed = completedKeys.size();

        if (options.parallelism() > 1 && tasks.size() > 1) {
            completed = runParallel(
                    tasks, options, validator, checkpointFile, runs, completed, total);
        } else {
            for (int index = 0; index < tasks.size(); index++) {
                Task task = tasks.get(index);
                RunObservation observation = execute(task, options, validator);
                runs.add(observation);
                System.out.printf("[%d/%d] %-8s %-20s %.4f%%  %.2f ms  eval=%d  %s%n",
                        ++completed, total, task.instance().name(), task.algorithm().displayName(),
                        observation.utilizationPercent(), observation.elapsedMilliseconds(),
                        observation.evaluations(), observation.valid() ? "通过" : "失败");
                if (options.resume()) RunCheckpoint.save(checkpointFile, options, runs);
            }
        }

        ExperimentArtifacts.writeAll(
                options.outputDirectory(), runs, options.experimentName(), options.decoderType());
        if (options.resume()) java.nio.file.Files.deleteIfExists(checkpointFile);
        long failures = runs.stream().filter(run -> !run.valid()).count();
        System.out.printf("实验完成：%d次运行，验证失败=%d，报告=%s%n",
                runs.size(), failures, options.outputDirectory().resolve("report.html").toAbsolutePath());
        if (failures > 0) {
            throw new IllegalStateException("实验产生了" + failures + "个非法解，详见报告");
        }
        return List.copyOf(runs);
    }

    /** 一次独立运行：单实例、单算法、单重复。彼此之间无共享可变状态。 */
    private record Task(Instance instance, AlgorithmId algorithm, int repetition, long seed) {}

    /** 执行一次运行：自建求解器、校验、装箱成观测。线程安全（只依赖不可变的 options 与无状态的 validator）。 */
    private static RunObservation execute(Task task, Options options, SolutionValidator validator) {
        PackingAlgorithm solver = SolverFactory.create(
                task.algorithm(),
                options.rotationAllowed(),
                options.order(),
                task.seed(),
                options.profile(),
                task.algorithm().stochastic() ? options.searchLimit() : SearchLimit.unlimited(),
                options.decoderType());
        Solution solution = solver.solve(task.instance());
        ValidationResult validation = validator.validate(
                task.instance(), solution, options.rotationAllowed());
        return new RunObservation(
                options.experimentName(), options.budgetKind(), options.budgetValue(),
                task.algorithm(), task.instance(), task.repetition(), task.seed(), solution,
                validation.valid(), String.join(" | ", validation.errors()),
                SolverIntrospection.evaluations(solver),
                SolverIntrospection.iterations(solver),
                SolverIntrospection.maxRectsBestStatistics(solver),
                SolverIntrospection.trace(solver, solution.packedArea()));
    }

    /**
     * 并行执行独立运行。按 Future 的下标顺序收集结果，因此 {@code runs} 的顺序与串行逐位一致，
     * 进度打印、断点写入也都在调用线程上按序进行，不会交错。
     */
    private static int runParallel(
            List<Task> tasks, Options options, SolutionValidator validator,
            Path checkpointFile, List<RunObservation> runs, int completed, int total)
            throws IOException {

        int parallelism = Math.min(options.parallelism(), tasks.size());
        System.out.printf("并行执行：%d个任务，并行度=%d%n", tasks.size(), parallelism);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        int progressed = completed;
        try {
            List<java.util.concurrent.Future<RunObservation>> futures = new ArrayList<>(tasks.size());
            for (Task task : tasks) {
                futures.add(pool.submit(() -> execute(task, options, validator)));
            }
            // 按下标收集：即使线程 3 先完成也先等 0、1、2，从而得到与串行逐位一致的顺序。
            for (int index = 0; index < futures.size(); index++) {
                RunObservation observation;
                try {
                    observation = futures.get(index).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("并行实验被中断", e);
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException("并行实验任务失败", e.getCause());
                }
                runs.add(observation);
                System.out.printf("[%d/%d] %-8s %-20s %.4f%%  %.2f ms  eval=%d  %s%n",
                        ++progressed, total, observation.instance().name(),
                        observation.algorithm().displayName(),
                        observation.utilizationPercent(), observation.elapsedMilliseconds(),
                        observation.evaluations(), observation.valid() ? "通过" : "失败");
                if (options.resume()) RunCheckpoint.save(checkpointFile, options, runs);
            }
        } finally {
            pool.shutdown();
        }
        return progressed;
    }

    private static void warmUpTimedAlgorithms(Options options, Instance instance) {
        long warmupMillis = Math.max(10, Math.min(200, options.budgetValue()));
        System.out.printf("时间预算实验预热：实例=%s，每算法最多%d ms（不计入结果）%n",
                instance.name(), warmupMillis);
        for (AlgorithmId algorithm : options.algorithms()) {
            if (!algorithm.stochastic()) continue;
            PackingAlgorithm solver = SolverFactory.create(
                    algorithm, options.rotationAllowed(), options.order(),
                    options.seeds().get(0) ^ 0x5DEECE66DL,
                    options.profile(), SearchLimit.milliseconds(warmupMillis),
                    options.decoderType());
            solver.solve(instance);
        }
        System.gc();
    }

    private static void warmUpRepeatedAlgorithms(Options options, Instance instance) {
        System.out.printf("确定性算法计时预热：实例=%s，每算法运行5次（不计入结果）%n",
                instance.name());
        for (AlgorithmId algorithm : options.algorithms()) {
            for (int i = 0; i < 5; i++) {
                PackingAlgorithm solver = SolverFactory.create(
                        algorithm, options.rotationAllowed(), options.order(),
                        options.seeds().get(0), options.profile(), SearchLimit.unlimited(),
                        options.decoderType());
                solver.solve(instance);
            }
        }
        System.gc();
    }

    private static int repetitions(AlgorithmId algorithm, Options options) {
        return algorithm.stochastic() || options.repeatDeterministic()
                ? options.seeds().size()
                : 1;
    }

    private static String runKey(RunObservation run) {
        return runKey(run.algorithm(), run.instance().name(), run.repetition(), run.seed());
    }

    private static String runKey(
            AlgorithmId algorithm, String instance, int repetition, long seed) {
        return algorithm.name() + '\u0000' + instance + '\u0000' + repetition + '\u0000' + seed;
    }

    public static List<Instance> readInstances(Path directory) throws IOException {
        List<Path> files = InstanceFiles.listGcutFiles(directory);
        InstanceReader reader = new InstanceReader();
        List<Instance> instances = new ArrayList<>(files.size());
        for (Path file : files) instances.add(reader.read(file));
        return List.copyOf(instances);
    }

}
