package com.assessment.packing.experiment;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.AntColonyConfig;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticAlgorithmConfig;
import com.assessment.packing.solver.GeneticMutationMode;
import com.assessment.packing.solver.HybridGeneticAlgorithmConfig;
import com.assessment.packing.solver.HybridLocalSearchMode;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.IteratedLocalSearchConfig;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.SimulatedAnnealingConfig;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.AdaptiveGeneticAlgorithmSolver;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuSearchSolver;
import com.assessment.packing.solver.TabuMode;
import com.assessment.packing.solver.TabuSearchConfig;
import com.assessment.packing.validation.SolutionValidator;
import com.assessment.packing.validation.ValidationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

/** SA、Tabu、ACO、ILS、GA、自适应GA和两种混合算法的可复现单因素参数扫描。 */
public final class ParameterSweepRunner {
    private ParameterSweepRunner() {}

    public static void runFromCommandLine(String[] args, Path data, Path output) throws Exception {
        String instance = ExperimentMain.arg(args, 3, "gcut3");
        int repetitions = ExperimentMain.integerArg(args, 4, 3);
        long seed = ExperimentMain.longArg(args, 5, 20261012L);
        ExperimentProfile profile = ExperimentProfile.parse(ExperimentMain.arg(args, 6, "STANDARD"));
        run(data, output, instance, repetitions, seed, profile);
    }

    public static void run(
            Path dataDirectory,
            Path outputDirectory,
            String instanceSelection,
            int repetitions,
            long baseSeed,
            ExperimentProfile profile) throws IOException {
        if (repetitions <= 0) throw new IllegalArgumentException("重复次数必须为正数");
        List<Instance> instances = ExperimentRunner.readInstances(dataDirectory).stream()
                .filter(instance -> instanceSelection.equalsIgnoreCase("all")
                        || instance.name().equalsIgnoreCase(instanceSelection))
                .toList();
        if (instances.isEmpty()) throw new IllegalArgumentException("找不到实例：" + instanceSelection);
        List<SweepCase> cases = cases(profile);
        List<SweepRun> runs = new ArrayList<>();
        SolutionValidator validator = new SolutionValidator();
        int total = instances.size() * cases.size() * repetitions;
        int completed = 0;

        for (Instance instance : instances) {
            for (SweepCase sweepCase : cases) {
                for (int repetition = 0; repetition < repetitions; repetition++) {
                    long seed = baseSeed + repetition * 104_729L;
                    PackingAlgorithm solver = sweepCase.factory().apply(seed);
                    Solution solution = solver.solve(instance);
                    ValidationResult validation = validator.validate(instance, solution, true);
                    runs.add(new SweepRun(
                            sweepCase.group(), sweepCase.algorithm(), sweepCase.parameter(),
                            sweepCase.value(), sweepCase.label(), instance, repetition + 1, seed,
                            solution.packedArea(), solution.utilization(instance) * 100.0,
                            solution.elapsedNanos() / 1_000_000.0,
                            SolverIntrospection.evaluations(solver), validation.valid()));
                    completed++;
                    System.out.printf("[参数 %d/%d] %-8s %-10s %-24s %.4f%% %s%n",
                            completed, total, instance.name(), sweepCase.algorithm(), sweepCase.label(),
                            solution.utilization(instance) * 100.0,
                            validation.valid() ? "通过" : "失败");
                }
            }
        }

        List<SweepSummary> summaries = summarize(runs);
        Files.createDirectories(outputDirectory);
        writeRuns(outputDirectory.resolve("runs.csv"), runs);
        writeSummaries(outputDirectory.resolve("summary.csv"), summaries);
        writeCharts(outputDirectory.resolve("charts"), summaries);
        writeReport(outputDirectory.resolve("report.html"), summaries, runs.size());
        System.out.println("参数扫描完成：" + outputDirectory.resolve("report.html").toAbsolutePath());
    }

    private static List<SweepCase> cases(ExperimentProfile profile) {
        int saLevels = profile == ExperimentProfile.QUICK ? 50 : 500;
        int saAttempts = profile == ExperimentProfile.QUICK ? 30 : 0;
        int tabuIterations = profile == ExperimentProfile.QUICK ? 60 : 1_000;
        int tabuNoImprove = profile == ExperimentProfile.QUICK ? 25 : 300;
        int gaPopulation = profile == ExperimentProfile.QUICK ? 30 : 100;
        int gaGenerations = profile == ExperimentProfile.QUICK ? 40 : 500;
        int gaNoImprove = profile == ExperimentProfile.QUICK ? 15 : 100;
        int acoAnts = profile == ExperimentProfile.QUICK ? 20 : 50;
        int acoIterations = profile == ExperimentProfile.QUICK ? 40 : 300;
        int acoNoImprove = profile == ExperimentProfile.QUICK ? 15 : 80;
        int localBudget = profile == ExperimentProfile.QUICK ? 60 : 300;
        int ilsIterations = profile == ExperimentProfile.QUICK ? 40 : 500;
        int ilsNoImprove = profile == ExperimentProfile.QUICK ? 15 : 100;
        List<SweepCase> cases = new ArrayList<>();

        for (double cooling : new double[] {0.90, 0.95, 0.98, 0.995}) {
            cases.add(new SweepCase("SA_COOLING", "SA", "coolingRate", Double.toString(cooling),
                    "降温率=" + cooling, seed -> new SimulatedAnnealingSolver(
                            true, ItemOrder.AREA_DESC,
                            new SimulatedAnnealingConfig(saLevels, saAttempts, cooling, 1e-5,
                                    profile == ExperimentProfile.QUICK ? 15 : 100, 0.8,
                                    profile == ExperimentProfile.QUICK ? 50 : 200, seed))));
        }
        for (double acceptance : new double[] {0.30, 0.50, 0.70, 0.90}) {
            cases.add(new SweepCase("SA_ACCEPTANCE", "SA", "initialAcceptance",
                    Double.toString(acceptance), "初始接受率=" + acceptance,
                    seed -> new SimulatedAnnealingSolver(
                            true, ItemOrder.AREA_DESC,
                            new SimulatedAnnealingConfig(saLevels, saAttempts, 0.98, 1e-5,
                                    profile == ExperimentProfile.QUICK ? 15 : 100, acceptance,
                                    profile == ExperimentProfile.QUICK ? 50 : 200, seed))));
        }
        for (int tenure : new int[] {3, 7, 15, 30}) {
            cases.add(new SweepCase("TABU_TENURE", "TABU_MOVE", "tabuTenure",
                    Integer.toString(tenure), "禁忌期限=" + tenure,
                    seed -> new TabuSearchSolver(true, ItemOrder.AREA_DESC, TabuMode.MOVE,
                            new TabuSearchConfig(tabuIterations, 50, tenure, tabuNoImprove, seed))));
        }
        for (int neighborhood : new int[] {10, 25, 50, 100}) {
            cases.add(new SweepCase("TABU_NEIGHBORHOOD", "TABU_MOVE", "neighborhoodSize",
                    Integer.toString(neighborhood), "邻域抽样=" + neighborhood,
                    seed -> new TabuSearchSolver(true, ItemOrder.AREA_DESC, TabuMode.MOVE,
                            new TabuSearchConfig(tabuIterations, neighborhood, 10, tabuNoImprove, seed))));
        }
        for (double evaporation : new double[] {0.05, 0.15, 0.30, 0.60}) {
            cases.add(new SweepCase("ACO_EVAPORATION", "ACO", "evaporationRate",
                    Double.toString(evaporation), "蒸发率=" + evaporation,
                    seed -> new AntColonySolver(
                            true, ItemOrder.AREA_DESC,
                            aco(acoAnts, acoIterations, 1.0, 2.0, evaporation,
                                    acoNoImprove, seed))));
        }
        for (double alpha : new double[] {0.0, 0.5, 1.0, 2.0}) {
            cases.add(new SweepCase("ACO_ALPHA", "ACO", "pheromoneAlpha",
                    Double.toString(alpha), "信息素指数=" + alpha,
                    seed -> new AntColonySolver(
                            true, ItemOrder.AREA_DESC,
                            aco(acoAnts, acoIterations, alpha, 2.0, 0.20,
                                    acoNoImprove, seed))));
        }
        for (double beta : new double[] {0.0, 1.0, 2.0, 4.0}) {
            cases.add(new SweepCase("ACO_BETA", "ACO", "heuristicBeta",
                    Double.toString(beta), "启发式指数=" + beta,
                    seed -> new AntColonySolver(
                            true, ItemOrder.AREA_DESC,
                            aco(acoAnts, acoIterations, 1.0, beta, 0.20,
                                    acoNoImprove, seed))));
        }
        for (int ants : new int[] {10, 20, 50, 100}) {
            cases.add(new SweepCase("ACO_ANTS", "ACO", "antCount",
                    Integer.toString(ants), "蚂蚁数=" + ants,
                    seed -> new AntColonySolver(
                            true, ItemOrder.AREA_DESC,
                            aco(ants, acoIterations, 1.0, 2.0, 0.20,
                                    acoNoImprove, seed))));
        }
        for (int swaps : new int[] {1, 2, 3, 5}) {
            cases.add(new SweepCase("ILS_PERTURBATION", "ILS", "perturbationSwaps",
                    Integer.toString(swaps), "扰动交换=" + swaps,
                    seed -> new IteratedLocalSearchSolver(
                            true, ItemOrder.AREA_DESC,
                            new IteratedLocalSearchConfig(
                                    ilsIterations, 100, swaps, ilsNoImprove, seed))));
        }
        for (int budget : new int[] {20, 50, 100, 200}) {
            cases.add(new SweepCase("ILS_LOCAL_BUDGET", "ILS", "localSearchEvaluations",
                    Integer.toString(budget), "局部评价=" + budget,
                    seed -> new IteratedLocalSearchSolver(
                            true, ItemOrder.AREA_DESC,
                            new IteratedLocalSearchConfig(
                                    ilsIterations, budget, 3, ilsNoImprove, seed))));
        }
        for (double mutationRate : new double[] {0.05, 0.15, 0.30, 0.50}) {
            cases.add(new SweepCase("GA_MUTATION_RATE", "GA", "mutationRate",
                    Double.toString(mutationRate), "变异率=" + mutationRate,
                    seed -> new GeneticAlgorithmSolver(true, ItemOrder.AREA_DESC,
                            ga(gaPopulation, gaGenerations, mutationRate, GeneticMutationMode.MIXED,
                                    gaNoImprove, seed))));
        }
        for (int population : new int[] {30, 60, 100, 150}) {
            cases.add(new SweepCase("GA_POPULATION", "GA", "populationSize",
                    Integer.toString(population), "种群=" + population,
                    seed -> new GeneticAlgorithmSolver(true, ItemOrder.AREA_DESC,
                            ga(population, gaGenerations, 0.2, GeneticMutationMode.MIXED,
                                    gaNoImprove, seed))));
        }
        for (GeneticMutationMode mode : GeneticMutationMode.values()) {
            cases.add(new SweepCase("GA_MUTATION_MODE", "GA", "mutationMode", mode.name(),
                    "变异方式=" + mode, seed -> new GeneticAlgorithmSolver(
                            true, ItemOrder.AREA_DESC,
                            ga(gaPopulation, gaGenerations, 0.2, mode, gaNoImprove, seed))));
        }
        for (double maximumMutation : new double[] {0.20, 0.35, 0.50, 0.70}) {
            cases.add(new SweepCase("ADAPTIVE_GA_MUTATION", "ADAPTIVE_GA",
                    "maximumMutationRate", Double.toString(maximumMutation),
                    "最大变异率=" + maximumMutation,
                    seed -> new AdaptiveGeneticAlgorithmSolver(
                            true, ItemOrder.AREA_DESC,
                            ga(gaPopulation, gaGenerations, 0.2,
                                    GeneticMutationMode.MIXED, gaNoImprove, seed),
                            new AdaptiveGeneticAlgorithmConfig(
                                    0.60, 0.95, 0.05, maximumMutation))));
        }
        for (int interval : new int[] {2, 5, 10}) {
            addHybridPair(cases, "HYBRID_INTERVAL", "localInterval", Integer.toString(interval),
                    "局部间隔=" + interval, gaPopulation, gaGenerations, gaNoImprove, localBudget,
                    interval, 0.10);
        }
        for (double fraction : new double[] {0.05, 0.10, 0.20}) {
            addHybridPair(cases, "HYBRID_FRACTION", "localFraction", Double.toString(fraction),
                    "局部比例=" + fraction, gaPopulation, gaGenerations, gaNoImprove, localBudget,
                    5, fraction);
        }
        for (int budget : new int[] {
                Math.max(20, localBudget / 3), localBudget, localBudget * 2}) {
            addHybridPair(cases, "HYBRID_LOCAL_BUDGET", "localBudget", Integer.toString(budget),
                    "局部预算=" + budget, gaPopulation, gaGenerations, gaNoImprove, budget,
                    5, 0.10);
        }
        return List.copyOf(cases);
    }

    /* 参数保留在方法签名中，使两种混合算法始终使用完全相同的局部参数。 */
    private static void addHybridPair(
            List<SweepCase> cases,
            String group,
            String parameter,
            String value,
            String label,
            int population,
            int generations,
            int noImprove,
            int localBudget,
            int interval,
            double fraction) {
        for (HybridLocalSearchMode mode : HybridLocalSearchMode.values()) {
            String algorithm = mode == HybridLocalSearchMode.ANNEALING ? "GA_SA" : "GA_TABU";
            cases.add(new SweepCase(group + '_' + algorithm, algorithm, parameter, value, label,
                    seed -> new GeneticAlgorithmSolver(
                            true, ItemOrder.AREA_DESC,
                            ga(population, generations, 0.2, GeneticMutationMode.MIXED, noImprove, seed),
                            mode,
                            new HybridGeneticAlgorithmConfig(
                                    interval, fraction, Math.max(2, (int) Math.ceil(population * fraction)),
                                    localBudget, 5, 20, 0.90, 0.30, 7))));
        }
    }

    private static GeneticAlgorithmConfig ga(
            int population, int generations, double mutationRate,
            GeneticMutationMode mode, int noImprove, long seed) {
        int elites = Math.max(1, Math.min(5, population - 1));
        return new GeneticAlgorithmConfig(
                population, generations, 3, 0.9, mutationRate, elites,
                noImprove, 20, mode, seed);
    }

    private static AntColonyConfig aco(
            int ants,
            int iterations,
            double alpha,
            double beta,
            double evaporation,
            int noImprove,
            long seed) {
        return new AntColonyConfig(
                ants, iterations, alpha, beta, evaporation, 1.0,
                Math.max(1, Math.min(5, ants)), noImprove, seed);
    }

    private static List<SweepSummary> summarize(List<SweepRun> runs) {
        Map<String, List<SweepRun>> groups = runs.stream().collect(Collectors.groupingBy(
                run -> run.group() + "\u0000" + run.label() + "\u0000" + run.instance().name(),
                LinkedHashMap::new, Collectors.toList()));
        List<SweepSummary> summaries = new ArrayList<>();
        for (List<SweepRun> group : groups.values()) {
            SweepRun first = group.get(0);
            double mean = group.stream().mapToDouble(SweepRun::utilization).average().orElse(0);
            double variance = group.stream().mapToDouble(run -> {
                double difference = run.utilization() - mean;
                return difference * difference;
            }).average().orElse(0);
            summaries.add(new SweepSummary(
                    first.group(), first.algorithm(), first.parameter(), first.value(), first.label(),
                    first.instance().name(), group.size(),
                    (int) group.stream().filter(SweepRun::valid).count(),
                    group.stream().mapToDouble(SweepRun::utilization).max().orElse(0),
                    mean, Math.sqrt(variance),
                    group.stream().mapToDouble(SweepRun::milliseconds).average().orElse(0),
                    Math.round(group.stream().mapToLong(SweepRun::evaluations).average().orElse(0))));
        }
        summaries.sort(Comparator.comparing(SweepSummary::group)
                .thenComparing(SweepSummary::instance)
                .thenComparing(SweepSummary::label));
        return List.copyOf(summaries);
    }

    private static void writeRuns(Path file, List<SweepRun> runs) throws IOException {
        StringBuilder csv = new StringBuilder("group,algorithm,parameter,value,label,instance,repetition,seed,packed_area,utilization_percent,elapsed_ms,evaluations,valid\n");
        for (SweepRun run : runs) csv.append(line(
                run.group(), run.algorithm(), run.parameter(), run.value(), run.label(),
                run.instance().name(), run.repetition(), run.seed(), run.packedArea(),
                format(run.utilization()), format(run.milliseconds()), run.evaluations(), run.valid()));
        Files.writeString(file, csv, StandardCharsets.UTF_8);
    }

    private static void writeSummaries(Path file, List<SweepSummary> summaries) throws IOException {
        StringBuilder csv = new StringBuilder("group,algorithm,parameter,value,label,instance,runs,valid_runs,best_percent,mean_percent,stddev_percent,mean_ms,mean_evaluations\n");
        for (SweepSummary summary : summaries) csv.append(line(
                summary.group(), summary.algorithm(), summary.parameter(), summary.value(),
                summary.label(), summary.instance(), summary.runs(), summary.validRuns(),
                format(summary.best()), format(summary.mean()), format(summary.standardDeviation()),
                format(summary.meanMilliseconds()), summary.meanEvaluations()));
        Files.writeString(file, csv, StandardCharsets.UTF_8);
    }

    private static void writeCharts(Path directory, List<SweepSummary> summaries) throws IOException {
        Files.createDirectories(directory);
        Map<String, List<SweepSummary>> groups = summaries.stream().collect(Collectors.groupingBy(
                summary -> summary.group() + "\u0000" + summary.instance(),
                LinkedHashMap::new, Collectors.toList()));
        for (List<SweepSummary> group : groups.values()) {
            SweepSummary first = group.get(0);
            Files.writeString(directory.resolve(first.group().toLowerCase(Locale.ROOT) + '-'
                            + first.instance() + ".svg"), parameterSvg(group), StandardCharsets.UTF_8);
        }
    }

    private static String parameterSvg(List<SweepSummary> values) {
        int width = 900;
        int height = 520;
        int left = 75;
        int top = 65;
        int plotWidth = 770;
        int plotHeight = 350;
        double min = values.stream().mapToDouble(SweepSummary::mean).min().orElse(0);
        double yMin = Math.max(0, Math.floor(min - 2));
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 900 520\" font-family=\"sans-serif\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/><text x=\"45\" y=\"35\" font-size=\"22\" font-weight=\"700\">")
                .append(xml(values.get(0).group())).append(" · ").append(xml(values.get(0).instance()))
                .append("</text>");
        for (int i = 0; i <= 5; i++) {
            double value = yMin + (100 - yMin) * i / 5.0;
            double y = top + plotHeight - plotHeight * i / 5.0;
            svg.append("<line x1=\"").append(left).append("\" y1=\"").append(format(y))
                    .append("\" x2=\"").append(left + plotWidth).append("\" y2=\"")
                    .append(format(y)).append("\" stroke=\"#e2e8f0\"/><text x=\"")
                    .append(left - 10).append("\" y=\"").append(format(y + 4))
                    .append("\" text-anchor=\"end\" font-size=\"12\">")
                    .append(format(value)).append("%</text>");
        }
        double slot = (double) plotWidth / values.size();
        for (int i = 0; i < values.size(); i++) {
            SweepSummary value = values.get(i);
            double barHeight = plotHeight * (value.mean() - yMin) / Math.max(0.001, 100 - yMin);
            double x = left + slot * i + slot * 0.15;
            double y = top + plotHeight - barHeight;
            svg.append("<rect x=\"").append(format(x)).append("\" y=\"").append(format(y))
                    .append("\" width=\"").append(format(slot * 0.7)).append("\" height=\"")
                    .append(format(barHeight)).append("\" fill=\"#2563eb\" opacity=\"0.82\"/>")
                    .append("<text x=\"").append(format(x + slot * 0.35)).append("\" y=\"")
                    .append(format(y - 7)).append("\" text-anchor=\"middle\" font-size=\"11\">")
                    .append(format(value.mean())).append("%</text>")
                    .append("<text transform=\"translate(").append(format(x + slot * 0.35)).append(',')
                    .append(top + plotHeight + 18).append(") rotate(25)\" text-anchor=\"start\" font-size=\"11\">")
                    .append(xml(value.label())).append("</text>");
        }
        return svg.append("</svg>").toString();
    }

    private static void writeReport(Path file, List<SweepSummary> summaries, int runCount) throws IOException {
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>参数敏感性实验</title><style>")
                .append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f1f5f9;color:#0f172a;margin:0}main{max-width:1250px;margin:auto;padding:28px}.card{background:white;padding:22px;border-radius:14px;margin:18px 0}table{border-collapse:collapse;width:100%;font-size:13px}th,td{padding:8px;border-bottom:1px solid #e2e8f0;text-align:right}th:first-child,td:first-child{text-align:left}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(390px,1fr));gap:12px}.grid img{width:100%;border:1px solid #e2e8f0;border-radius:8px}a{color:#1d4ed8}</style></head><body><main><h1>参数敏感性实验</h1><div class=\"card\"><p>共运行 ")
                .append(runCount).append(" 次。每组只改变一个参数，其余参数保持基准值；每个参数值使用相同的一组随机种子，形成配对比较。</p><p><a href=\"runs.csv\">逐次CSV</a> · <a href=\"summary.csv\">汇总CSV</a></p></div><div class=\"card\"><h2>结果</h2><table><thead><tr><th>组 / 参数</th><th>实例</th><th>次数</th><th>最好</th><th>平均</th><th>标准差</th><th>平均ms</th><th>评价数</th><th>合法</th></tr></thead><tbody>");
        for (SweepSummary summary : summaries) {
            html.append("<tr><td>").append(xml(summary.group())).append(" / ")
                    .append(xml(summary.label())).append("</td><td>").append(xml(summary.instance()))
                    .append("</td><td>").append(summary.runs()).append("</td><td>")
                    .append(format(summary.best())).append("%</td><td>").append(format(summary.mean()))
                    .append("%</td><td>").append(format(summary.standardDeviation())).append("</td><td>")
                    .append(format(summary.meanMilliseconds())).append("</td><td>")
                    .append(summary.meanEvaluations()).append("</td><td>")
                    .append(summary.validRuns()).append('/').append(summary.runs()).append("</td></tr>");
        }
        html.append("</tbody></table></div><div class=\"card\"><h2>参数图</h2><div class=\"grid\">");
        Map<String, SweepSummary> unique = new LinkedHashMap<>();
        for (SweepSummary summary : summaries) unique.put(summary.group() + '\0' + summary.instance(), summary);
        for (SweepSummary summary : unique.values()) {
            String path = "charts/" + summary.group().toLowerCase(Locale.ROOT) + '-'
                    + summary.instance() + ".svg";
            html.append("<a href=\"").append(path).append("\"><img loading=\"lazy\" src=\"")
                    .append(path).append("\" alt=\"").append(xml(summary.group())).append("\"></a>");
        }
        html.append("</div></div></main></body></html>");
        Files.writeString(file, html, StandardCharsets.UTF_8);
    }

    private static String line(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) line.append(',');
            String value = String.valueOf(values[i]);
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                line.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else line.append(value);
        }
        return line.append('\n').toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record SweepCase(
            String group,
            String algorithm,
            String parameter,
            String value,
            String label,
            LongFunction<PackingAlgorithm> factory) {}

    private record SweepRun(
            String group,
            String algorithm,
            String parameter,
            String value,
            String label,
            Instance instance,
            int repetition,
            long seed,
            long packedArea,
            double utilization,
            double milliseconds,
            long evaluations,
            boolean valid) {}

    private record SweepSummary(
            String group,
            String algorithm,
            String parameter,
            String value,
            String label,
            String instance,
            int runs,
            int validRuns,
            double best,
            double mean,
            double standardDeviation,
            double meanMilliseconds,
            long meanEvaluations) {}
}
