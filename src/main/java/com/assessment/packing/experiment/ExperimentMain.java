package com.assessment.packing.experiment;

import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 统一实验、图表和报告入口。 */
public final class ExperimentMain {
    private ExperimentMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equals("--help")) {
            printHelp();
            return;
        }
        String command = args[0].toLowerCase();
        Path data = Path.of(arg(args, 1, "gcut01"));
        Path output = Path.of(arg(args, 2, "results/" + command));
        switch (command) {
            case "config" -> runConfigured(args);
            case "benchmark" -> runBenchmark(args, data, output);
            case "full-benchmark" -> runFullBenchmark(args, data, output);
            case "shelf" -> runShelf(args, data, output);
            case "skyline" -> runSkyline(args, data, output);
            case "maxrects" -> runMaxRects(args, data, output);
            case "aco" -> runAntColony(args, data, output);
            case "fair-evaluations" -> runFairEvaluations(args, data, output);
            case "fair-time" -> runFairTime(args, data, output);
            case "decoder-matrix" -> runDecoderMatrix(args, data, output);
            case "sweep" -> ParameterSweepRunner.runFromCommandLine(args, data, output);
            case "all" -> runAll(args, data, output);
            default -> throw new IllegalArgumentException("未知实验命令：" + command);
        }
    }

    private static void runConfigured(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("config命令后必须提供一个properties文件");
        }
        ExperimentRunner.Options options = ExperimentRunConfiguration.read(Path.of(args[1]));
        new ExperimentRunner().run(options);
    }

    private static void runBenchmark(String[] args, Path data, Path output) throws Exception {
        int repetitions = integerArg(args, 3, 5);
        long baseSeed = longArg(args, 4, 20261012L);
        ExperimentProfile profile = ExperimentProfile.parse(arg(args, 5, "STANDARD"));
        new ExperimentRunner().run(options(
                data, output, SolverFactory.primaryAlgorithms(), repetitions, baseSeed, profile,
                SearchLimit.unlimited(), "工程主入口默认参数总览", "DEFAULT", 0));
    }

    private static void runFullBenchmark(String[] args, Path data, Path output) throws Exception {
        int repetitions = integerArg(args, 3, 5);
        long baseSeed = longArg(args, 4, 20261012L);
        ExperimentProfile profile = ExperimentProfile.parse(arg(args, 5, "STANDARD"));
        new ExperimentRunner().run(options(
                data, output, SolverFactory.allAlgorithms(), repetitions, baseSeed, profile,
                SearchLimit.unlimited(), "完整46入口实现档案", "FULL_CATALOG", 0));
    }

    private static void runFairEvaluations(String[] args, Path data, Path output) throws Exception {
        long budget = longArg(args, 3, 20_000L);
        int repetitions = integerArg(args, 4, 5);
        long baseSeed = longArg(args, 5, 20261012L);
        ExperimentProfile profile = ExperimentProfile.parse(arg(args, 6, "STANDARD"));
        new ExperimentRunner().run(options(
                data, output, SolverFactory.fairAlgorithms(), repetitions, baseSeed, profile,
                SearchLimit.evaluations(budget), "相同候选解评价预算实验", "EVALUATIONS", budget));
    }

    private static void runMaxRects(String[] args, Path data, Path output) throws Exception {
        int repetitions = integerArg(args, 3, 20);
        long baseSeed = longArg(args, 4, 20261012L);
        new ExperimentRunner().run(new ExperimentRunner.Options(
                data, output, SolverFactory.maxRectsAlgorithms(), true, ItemOrder.AREA_DESC,
                seeds(repetitions, baseSeed), ExperimentProfile.QUICK, SearchLimit.unlimited(),
                "MaxRects五种启发式与BEST对比", "MAXRECTS", repetitions, true));
    }

    private static void runShelf(String[] args, Path data, Path output) throws Exception {
        int repetitions = integerArg(args, 3, 20);
        long baseSeed = longArg(args, 4, 20261013L);
        new ExperimentRunner().run(new ExperimentRunner.Options(
                data, output, SolverFactory.shelfAlgorithms(), true, ItemOrder.AREA_DESC,
                seeds(repetitions, baseSeed), ExperimentProfile.QUICK, SearchLimit.unlimited(),
                "Shelf六种规则、Floor-Ceiling、GLOBAL与WM对比", "SHELF", repetitions, true));
    }

    private static void runAntColony(String[] args, Path data, Path output) throws Exception {
        int repetitions = integerArg(args, 3, 20);
        long baseSeed = longArg(args, 4, 20261013L);
        ExperimentProfile profile = ExperimentProfile.parse(arg(args, 5, "STANDARD"));
        new ExperimentRunner().run(options(
                data, output, List.of(AlgorithmId.ACO), repetitions, baseSeed, profile,
                SearchLimit.unlimited(), "Skyline排列蚁群算法多种子实验", "ACO", 0));
    }

    private static void runSkyline(String[] args, Path data, Path output) throws Exception {
        int repetitions = integerArg(args, 3, 20);
        long baseSeed = longArg(args, 4, 20261013L);
        new ExperimentRunner().run(new ExperimentRunner.Options(
                data, output, SolverFactory.skylineAlgorithms(), true, ItemOrder.AREA_DESC,
                seeds(repetitions, baseSeed), ExperimentProfile.QUICK, SearchLimit.unlimited(),
                "堆优化Skyline与GLOBAL Skyline-BL/BF/WM对比", "SKYLINE", repetitions, true));
    }

    private static void runFairTime(String[] args, Path data, Path output) throws Exception {
        long milliseconds = longArg(args, 3, 2_000L);
        int repetitions = integerArg(args, 4, 5);
        long baseSeed = longArg(args, 5, 20261012L);
        ExperimentProfile profile = ExperimentProfile.parse(arg(args, 6, "STANDARD"));
        new ExperimentRunner().run(options(
                data, output, SolverFactory.fairAlgorithms(), repetitions, baseSeed, profile,
                SearchLimit.milliseconds(milliseconds), "相同墙钟时间预算实验", "TIME_MS", milliseconds));
    }

    private static void runDecoderMatrix(String[] args, Path data, Path output) throws Exception {
        long evaluations = longArg(args, 3, 20_000L);
        int repetitions = integerArg(args, 4, 5);
        long baseSeed = longArg(args, 5, 20261012L);
        ExperimentProfile profile = ExperimentProfile.parse(arg(args, 6, "STANDARD"));
        DecoderMatrixRunner.run(
                data, output, evaluations, repetitions, baseSeed, profile);
    }

    private static void runAll(String[] args, Path data, Path output) throws Exception {
        int repetitions = integerArg(args, 3, 5);
        long baseSeed = longArg(args, 4, 20261012L);
        ExperimentProfile profile = ExperimentProfile.parse(arg(args, 5, "STANDARD"));
        long evaluationBudget = longArg(args, 6, 20_000L);
        long timeBudget = longArg(args, 7, 2_000L);
        ExperimentRunner runner = new ExperimentRunner();
        runner.run(options(data, output.resolve("benchmark"), SolverFactory.primaryAlgorithms(),
                repetitions, baseSeed, profile, SearchLimit.unlimited(),
                "工程主入口默认参数总览", "DEFAULT", 0));
        runner.run(options(data, output.resolve("fair-evaluations"), SolverFactory.fairAlgorithms(),
                repetitions, baseSeed, profile, SearchLimit.evaluations(evaluationBudget),
                "相同候选解评价预算实验", "EVALUATIONS", evaluationBudget));
        runner.run(options(data, output.resolve("fair-time"), SolverFactory.fairAlgorithms(),
                repetitions, baseSeed, profile, SearchLimit.milliseconds(timeBudget),
                "相同墙钟时间预算实验", "TIME_MS", timeBudget));
        ParameterSweepRunner.run(data, output.resolve("parameter-sweep"), "gcut3",
                repetitions, baseSeed, profile);
        DashboardWriter.write(output);
        System.out.println("全部实验入口：" + output.resolve("index.html").toAbsolutePath());
    }

    private static ExperimentRunner.Options options(
            Path data,
            Path output,
            List<AlgorithmId> algorithms,
            int repetitions,
            long baseSeed,
            ExperimentProfile profile,
            SearchLimit limit,
            String name,
            String budgetKind,
            long budgetValue) {
        if (repetitions <= 0) throw new IllegalArgumentException("重复次数必须为正数");
        return new ExperimentRunner.Options(
                data, output, algorithms, true, ItemOrder.AREA_DESC,
                seeds(repetitions, baseSeed), profile, limit, name, budgetKind, budgetValue, false,
                PackingDecoderType.SKYLINE, false, parallelism());
    }

    /**
     * 并行度为显式开关：默认串行（1），与既有产出逐位一致。
     * 只有在设置环境变量 {@code PACKING_PARALLELISM} 时才并行；
     * 时间预算实验会因公平性被 {@link ExperimentRunner.Options} 拒绝。
     *
     * <p>取值受 {@link ExperimentRunner.Options#MAX_PARALLELISM} 约束：这是防止一次手误
     * 就把机器打满的护栏，而不是性能调参。越界直接报错而非静默截断，免得实验者以为生效了。
     */
    private static int parallelism() {
        String value = System.getenv("PACKING_PARALLELISM");
        if (value == null || value.isBlank()) return 1;
        int parallelism;
        try {
            parallelism = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PACKING_PARALLELISM 必须是整数：" + value, e);
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("PACKING_PARALLELISM 必须为正数：" + value);
        }
        return parallelism;
    }

    private static List<Long> seeds(int repetitions, long baseSeed) {
        List<Long> seeds = new ArrayList<>(repetitions);
        for (int i = 0; i < repetitions; i++) seeds.add(baseSeed + i * 104_729L);
        return List.copyOf(seeds);
    }

    static String arg(String[] args, int index, String defaultValue) {
        return args.length > index ? args[index] : defaultValue;
    }

    static int integerArg(String[] args, int index, int defaultValue) {
        return Integer.parseInt(arg(args, index, Integer.toString(defaultValue)));
    }

    static long longArg(String[] args, int index, long defaultValue) {
        return Long.parseLong(arg(args, index, Long.toString(defaultValue)));
    }

    private static void printHelp() {
        System.out.println("""
                用法：java -cp out com.assessment.packing.experiment.ExperimentMain <命令> [参数]

                config           [properties配置文件]
                benchmark        [数据目录] [输出目录] [重复次数=5] [基础种子=20261012] [QUICK|STANDARD]
                full-benchmark   [数据目录] [输出目录] [重复次数=5] [基础种子=20261012] [QUICK|STANDARD]
                shelf           [数据目录] [输出目录] [重复次数=20] [基础种子=20261013]
                skyline          [数据目录] [输出目录] [重复次数=20] [基础种子=20261013]
                maxrects        [数据目录] [输出目录] [重复次数=20] [基础种子=20261012]
                aco             [数据目录] [输出目录] [重复次数=20] [基础种子=20261013] [QUICK|STANDARD]
                fair-evaluations [数据目录] [输出目录] [评价预算=20000] [重复次数=5] [基础种子] [配置]
                fair-time        [数据目录] [输出目录] [毫秒预算=2000] [重复次数=5] [基础种子] [配置]
                decoder-matrix   [数据目录] [输出目录] [评价预算=20000] [重复次数=5] [基础种子] [配置]
                sweep           [数据目录] [输出目录] [实例名=gcut3|all] [重复次数=3] [基础种子] [配置]
                all             [数据目录] [输出目录] [重复次数=5] [基础种子] [配置] [评价预算] [毫秒预算]

                每个实验目录都会生成runs.csv、summary.csv、overall.csv、traces.csv、
                algorithm-catalog.csv、report.html、layouts/*.svg和convergence/*.svg。
                """);
    }
}
