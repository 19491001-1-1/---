package com.assessment.packing.experiment;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Placement;
import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.MaxRectsBestStatistics;
import com.assessment.packing.solver.MaxRectsHeuristic;
import com.assessment.packing.solver.PackingDecoderType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class ExperimentArtifacts {
    private static final String[] COLORS = {
            "#2563eb", "#dc2626", "#059669", "#7c3aed", "#ea580c",
            "#0891b2", "#be185d", "#4d7c0f", "#9333ea", "#0f766e",
            "#b45309", "#475569", "#1d4ed8", "#c026d3"
    };

    private ExperimentArtifacts() {}

    public static List<SummaryObservation> writeAll(
            Path outputDirectory,
            List<RunObservation> runs,
            String title,
            PackingDecoderType decoderType) throws IOException {
        return writeAll(outputDirectory, runs, title, decoderType, List.of());
    }

    public record ConfigurationRun(String instance, String algorithm, String decoder,
                                   boolean valid, double utilizationPercent) {}
    public record ConfigurationWinner(String instance, String algorithm, String decoder,
                                      double mean, double best, int runs, int validRuns, boolean tied) {}

    public static List<ConfigurationWinner> configurationWinners(List<ConfigurationRun> runs) {
        Map<List<String>, List<ConfigurationRun>> groups = runs.stream().collect(Collectors.groupingBy(
                run -> List.of(run.instance(), run.algorithm(), run.decoder()), LinkedHashMap::new, Collectors.toList()));
        List<ConfigurationWinner> candidates = new ArrayList<>();
        groups.forEach((key, values) -> {
            double[] valid = values.stream().filter(ConfigurationRun::valid).mapToDouble(ConfigurationRun::utilizationPercent).toArray();
            if (valid.length > 0) candidates.add(new ConfigurationWinner(key.get(0), key.get(1), key.get(2),
                    mean(valid), java.util.Arrays.stream(valid).max().orElse(0), values.size(), valid.length, false));
        });
        Map<String, Double> maxima = new LinkedHashMap<>();
        candidates.forEach(item -> maxima.merge(item.instance(), item.mean(), Math::max));
        List<ConfigurationWinner> best = candidates.stream()
                .filter(item -> Math.abs(item.mean() - maxima.get(item.instance())) <= 1e-9).toList();
        return best.stream().map(item -> new ConfigurationWinner(item.instance(), item.algorithm(), item.decoder(),
                item.mean(), item.best(), item.runs(), item.validRuns(),
                best.stream().filter(other -> other.instance().equals(item.instance())).count() > 1))
                .sorted(Comparator.comparing(ConfigurationWinner::instance, ExperimentArtifacts::naturalNameCompare)
                        .thenComparing(ConfigurationWinner::algorithm).thenComparing(ConfigurationWinner::decoder)).toList();
    }

    public static List<SummaryObservation> writeAll(Path outputDirectory, List<RunObservation> runs,
            String title, PackingDecoderType decoderType, List<ConfigurationRun> configurations) throws IOException {
        Files.createDirectories(outputDirectory);
        List<SummaryObservation> summaries = summarize(runs);
        writeRunsCsv(outputDirectory.resolve("runs.csv"), runs);
        writeSummaryCsv(outputDirectory.resolve("summary.csv"), summaries);
        writeOverallCsv(outputDirectory.resolve("overall.csv"), summaries);
        writeInstanceWinnersCsv(outputDirectory.resolve("instance-winners.csv"), instanceWinners(summaries));
        if (!configurations.isEmpty()) {
            StringBuilder csv = new StringBuilder("instance,algorithm,decoder,mean_percent,best_percent,runs,valid_runs,tied\n");
            for (ConfigurationWinner item : configurationWinners(configurations)) {
                row(csv, item.instance(), item.algorithm(), item.decoder(), f(item.mean()), f(item.best()),
                        item.runs(), item.validRuns(), item.tied());
            }
            write(outputDirectory.resolve("instance-winners.csv"), csv);
        }
        writeTracesCsv(outputDirectory.resolve("traces.csv"), runs);
        writeAlgorithmCatalogCsv(outputDirectory.resolve("algorithm-catalog.csv"));
        writeBestLayouts(outputDirectory.resolve("layouts"), runs);
        writeConvergenceCharts(outputDirectory.resolve("convergence"), runs);
        writeReport(outputDirectory.resolve("report.html"), title, runs, summaries, decoderType, configurations);
        return summaries;
    }

    public static List<SummaryObservation> summarize(List<RunObservation> runs) {
        Map<String, List<RunObservation>> groups = runs.stream().collect(Collectors.groupingBy(
                run -> run.algorithm().name() + "\u0000" + run.instance().name(),
                LinkedHashMap::new,
                Collectors.toList()));
        List<SummaryObservation> result = new ArrayList<>();
        for (List<RunObservation> group : groups.values()) {
            group.sort(Comparator.comparingInt(RunObservation::repetition));
            List<RunObservation> valid = group.stream().filter(RunObservation::valid).toList();
            double[] values = valid.stream().mapToDouble(RunObservation::utilizationPercent).sorted().toArray();
            double mean = mean(values);
            double variance = 0.0;
            for (double value : values) variance += (value - mean) * (value - mean);
            double median = values.length == 0 ? 0.0 : values.length % 2 == 1
                    ? values[values.length / 2]
                    : (values[values.length / 2 - 1] + values[values.length / 2]) / 2.0;
            RunObservation first = group.get(0);
            result.add(new SummaryObservation(
                    first.algorithm(),
                    first.instance().name(),
                    group.size(),
                    (int) group.stream().filter(RunObservation::valid).count(),
                    values.length == 0 ? 0.0 : values[values.length - 1],
                    values.length == 0 ? 0.0 : values[0],
                    mean,
                    median,
                    values.length == 0 ? 0.0 : Math.sqrt(variance / values.length),
                    group.stream().mapToDouble(RunObservation::elapsedMilliseconds).average().orElse(0.0),
                    Math.round(group.stream().mapToLong(RunObservation::evaluations).average().orElse(0.0)),
                    valid.stream().mapToLong(run -> run.solution().packedArea()).max().orElse(0)));
        }
        result.sort(Comparator.comparing(SummaryObservation::instance, ExperimentArtifacts::naturalNameCompare)
                .thenComparingInt(summary -> AlgorithmCatalog.displayOrder(summary.algorithm())));
        return List.copyOf(result);
    }

    private static void writeRunsCsv(Path file, List<RunObservation> runs) throws IOException {
        StringBuilder text = new StringBuilder("experiment,budget_kind,budget_value,algorithm,instance,repetition,seed,packed_area,utilization_percent,placed_items,total_items,elapsed_ms,evaluations,iterations,valid,validation_errors,maxrects_selected_heuristic,maxrects_tied_best,bssf_area,bssf_ms,blsf_area,blsf_ms,baf_area,baf_ms,bl_area,bl_ms,cp_area,cp_ms\n");
        for (RunObservation run : runs) {
            row(text,
                    run.experiment(), run.budgetKind(), run.budgetValue(), run.algorithm().name(),
                    run.instance().name(), run.repetition(), run.seed(), run.solution().packedArea(),
                    f(run.utilizationPercent()), run.solution().placements().size(),
                    run.instance().items().size(), f(run.elapsedMilliseconds()), run.evaluations(),
                    run.iterations(), run.valid(), run.validationErrors(),
                    maxRectsSelected(run), maxRectsTiedBest(run),
                    maxRectsArea(run, MaxRectsHeuristic.BSSF),
                    maxRectsMilliseconds(run, MaxRectsHeuristic.BSSF),
                    maxRectsArea(run, MaxRectsHeuristic.BLSF),
                    maxRectsMilliseconds(run, MaxRectsHeuristic.BLSF),
                    maxRectsArea(run, MaxRectsHeuristic.BAF),
                    maxRectsMilliseconds(run, MaxRectsHeuristic.BAF),
                    maxRectsArea(run, MaxRectsHeuristic.BL),
                    maxRectsMilliseconds(run, MaxRectsHeuristic.BL),
                    maxRectsArea(run, MaxRectsHeuristic.CP),
                    maxRectsMilliseconds(run, MaxRectsHeuristic.CP));
        }
        write(file, text);
    }

    private static void writeSummaryCsv(Path file, List<SummaryObservation> summaries) throws IOException {
        StringBuilder text = new StringBuilder("algorithm,instance,runs,valid_runs,best_percent,worst_percent,mean_percent,median_percent,stddev_percent,mean_ms,mean_evaluations,best_packed_area\n");
        for (SummaryObservation summary : summaries) {
            row(text, summary.algorithm().name(), summary.instance(), summary.runs(), summary.validRuns(),
                    f(summary.bestUtilization()), f(summary.worstUtilization()),
                    f(summary.meanUtilization()), f(summary.medianUtilization()),
                    f(summary.standardDeviation()), f(summary.meanMilliseconds()),
                    summary.meanEvaluations(), summary.bestPackedArea());
        }
        write(file, text);
    }

    private static void writeOverallCsv(Path file, List<SummaryObservation> summaries) throws IOException {
        Map<AlgorithmId, List<SummaryObservation>> groups = summaries.stream().collect(Collectors.groupingBy(
                SummaryObservation::algorithm, LinkedHashMap::new, Collectors.toList()));
        StringBuilder text = new StringBuilder("algorithm,instances,mean_of_instance_means,mean_best_percent,average_rank,mean_ms,total_valid_runs,total_runs\n");
        for (AlgorithmId algorithm : AlgorithmCatalog.displayAlgorithms()) {
            List<SummaryObservation> values = groups.get(algorithm);
            if (values == null) continue;
            row(text, algorithm.name(), values.size(),
                    f(values.stream().mapToDouble(SummaryObservation::meanUtilization).average().orElse(0)),
                    f(values.stream().mapToDouble(SummaryObservation::bestUtilization).average().orElse(0)),
                    f(averageRank(algorithm, summaries)),
                    f(values.stream().mapToDouble(SummaryObservation::meanMilliseconds).average().orElse(0)),
                    values.stream().mapToInt(SummaryObservation::validRuns).sum(),
                    values.stream().mapToInt(SummaryObservation::runs).sum());
        }
        write(file, text);
    }

    /** 同一实例下按多次合法运行的平均利用率选冠军；浮点误差范围内并列时全部保留。 */
    public static List<InstanceWinner> instanceWinners(List<SummaryObservation> summaries) {
        Map<String, List<SummaryObservation>> byInstance = summaries.stream()
                .filter(summary -> summary.validRuns() > 0)
                .collect(Collectors.groupingBy(
                        SummaryObservation::instance, LinkedHashMap::new, Collectors.toList()));
        List<InstanceWinner> winners = new ArrayList<>();
        for (Map.Entry<String, List<SummaryObservation>> entry : byInstance.entrySet()) {
            double bestMean = entry.getValue().stream()
                    .mapToDouble(SummaryObservation::meanUtilization).max().orElse(0.0);
            List<SummaryObservation> tied = entry.getValue().stream()
                    .filter(summary -> Math.abs(summary.meanUtilization() - bestMean) <= 1.0e-9)
                    .sorted(Comparator.comparingInt(summary -> AlgorithmCatalog.displayOrder(summary.algorithm())))
                    .toList();
            for (SummaryObservation summary : tied) {
                winners.add(new InstanceWinner(
                        entry.getKey(), summary.algorithm(), summary.meanUtilization(),
                        summary.bestUtilization(), summary.standardDeviation(), summary.runs(),
                        summary.validRuns(), summary.bestPackedArea(), tied.size() > 1));
            }
        }
        winners.sort(Comparator.comparing(InstanceWinner::instance, ExperimentArtifacts::naturalNameCompare)
                .thenComparingInt(winner -> AlgorithmCatalog.displayOrder(winner.algorithm())));
        return List.copyOf(winners);
    }

    public record InstanceWinner(
            String instance,
            AlgorithmId algorithm,
            double meanUtilization,
            double bestUtilization,
            double standardDeviation,
            int runs,
            int validRuns,
            long bestPackedArea,
            boolean tied) {}

    private static void writeInstanceWinnersCsv(Path file, List<InstanceWinner> winners) throws IOException {
        StringBuilder text = new StringBuilder(
                "instance,algorithm,display_name,mean_percent,best_percent,stddev_percent,runs,valid_runs,best_packed_area,tied\n");
        for (InstanceWinner winner : winners) {
            row(text, winner.instance(), winner.algorithm().name(), winner.algorithm().displayName(),
                    f(winner.meanUtilization()), f(winner.bestUtilization()),
                    f(winner.standardDeviation()), winner.runs(), winner.validRuns(),
                    winner.bestPackedArea(), winner.tied());
        }
        write(file, text);
    }

    private static void writeTracesCsv(Path file, List<RunObservation> runs) throws IOException {
        StringBuilder text = new StringBuilder("experiment,algorithm,instance,repetition,seed,step,evaluations,best_packed_area,best_utilization_percent\n");
        for (RunObservation run : runs) {
            for (ConvergencePoint point : run.trace()) {
                row(text, run.experiment(), run.algorithm().name(), run.instance().name(),
                        run.repetition(), run.seed(), point.step(), point.evaluations(),
                        point.bestPackedArea(),
                        f(100.0 * point.bestPackedArea() / run.instance().binArea()));
            }
        }
        write(file, text);
    }

    private static void writeAlgorithmCatalogCsv(Path file) throws IOException {
        StringBuilder text = new StringBuilder(
                "algorithm,display_name,exposure,role,group_weight_percent,family,representative,stochastic,rationale,settings,compatibility_only\n");
        for (AlgorithmCatalog.Entry entry : AlgorithmCatalog.entries()) {
            row(text,
                    entry.id().name(),
                    entry.id().displayName(),
                    entry.exposure(),
                    entry.role(),
                    entry.groupWeightPercent(),
                    entry.family(),
                    entry.representative().name(),
                    entry.id().stochastic(),
                    entry.rationale(), AlgorithmCatalog.settings(entry.id()), AlgorithmCatalog.compatibilityOnly(entry.id()));
        }
        write(file, text);
    }

    private static void writeBestLayouts(Path directory, List<RunObservation> runs) throws IOException {
        Files.createDirectories(directory);
        Map<String, RunObservation> best = new LinkedHashMap<>();
        for (RunObservation run : runs) {
            String key = run.instance().name() + "\u0000" + run.algorithm().name();
            RunObservation previous = best.get(key);
            if (previous == null
                    || run.solution().packedArea() > previous.solution().packedArea()
                    || run.solution().packedArea() == previous.solution().packedArea()
                            && run.solution().elapsedNanos() < previous.solution().elapsedNanos()) {
                best.put(key, run);
            }
        }
        for (RunObservation run : best.values()) {
            Path instanceDirectory = directory.resolve(run.instance().name());
            Files.createDirectories(instanceDirectory);
            write(instanceDirectory.resolve(run.algorithm().name().toLowerCase(Locale.ROOT) + ".svg"),
                    layoutSvg(run));
        }
    }

    private static StringBuilder layoutSvg(RunObservation run) {
        Instance instance = run.instance();
        int canvasWidth = 1000;
        int canvasHeight = 760;
        int left = 55;
        int top = 100;
        int plotWidth = 890;
        int plotHeight = 600;
        double scale = Math.min(
                (double) plotWidth / instance.binWidth(),
                (double) plotHeight / instance.binHeight());
        double width = instance.binWidth() * scale;
        double height = instance.binHeight() * scale;
        StringBuilder svg = svgStart(canvasWidth, canvasHeight);
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#f8fafc\"/>");
        svg.append("<text x=\"55\" y=\"38\" font-size=\"24\" font-weight=\"700\">")
                .append(xml(instance.name())).append(" · ").append(xml(run.algorithm().displayName()))
                .append("</text>");
        svg.append("<text x=\"55\" y=\"70\" font-size=\"16\" fill=\"#475569\">")
                .append(String.format(Locale.ROOT,
                        "%dx%d · 已放入 %d/%d · 面积 %d · 利用率 %.4f%% · seed %d",
                        instance.binWidth(), instance.binHeight(), run.solution().placements().size(),
                        instance.items().size(), run.solution().packedArea(), run.utilizationPercent(), run.seed()))
                .append("</text>");
        svg.append("<rect x=\"").append(left).append("\" y=\"").append(top)
                .append("\" width=\"").append(f(width)).append("\" height=\"")
                .append(f(height)).append("\" fill=\"white\" stroke=\"#0f172a\" stroke-width=\"3\"/>");
        for (Placement placement : run.solution().placements()) {
            double x = left + placement.x() * scale;
            double y = top + height - (placement.y() + placement.height()) * scale;
            double w = placement.width() * scale;
            double h = placement.height() * scale;
            String color = itemColor(placement.item().id());
            svg.append("<g><title>零件 ").append(placement.item().id())
                    .append(" | 原尺寸 ").append(placement.item().width()).append('x')
                    .append(placement.item().height()).append(" | 坐标 (")
                    .append(placement.x()).append(',').append(placement.y()).append(") | 旋转 ")
                    .append(placement.rotated()).append("</title>");
            svg.append("<rect x=\"").append(f(x)).append("\" y=\"").append(f(y))
                    .append("\" width=\"").append(f(w)).append("\" height=\"").append(f(h))
                    .append("\" fill=\"").append(color)
                    .append("\" fill-opacity=\"0.78\" stroke=\"#0f172a\" stroke-width=\"1\"/>");
            if (w >= 24 && h >= 18) {
                svg.append("<text x=\"").append(f(x + w / 2)).append("\" y=\"")
                        .append(f(y + h / 2 + 5)).append("\" text-anchor=\"middle\" font-size=\"")
                        .append(w >= 45 && h >= 30 ? 13 : 10)
                        .append("\" font-weight=\"700\" fill=\"#0f172a\">")
                        .append(placement.item().id()).append(placement.rotated() ? "↻" : "")
                        .append("</text>");
            }
            svg.append("</g>");
        }
        svg.append("<text x=\"").append(left).append("\" y=\"").append(top + height + 30)
                .append("\" font-size=\"14\" fill=\"#475569\">原点位于板材左下角；↻ 表示旋转90°。鼠标悬停可查看零件详情。</text>");
        return svg.append("</svg>");
    }

    private static void writeConvergenceCharts(Path directory, List<RunObservation> runs) throws IOException {
        Files.createDirectories(directory);
        Map<String, List<RunObservation>> byInstance = runs.stream().collect(Collectors.groupingBy(
                run -> run.instance().name(), LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<RunObservation>> entry : byInstance.entrySet()) {
            List<RunObservation> selected = bestTracePerAlgorithm(entry.getValue());
            if (selected.stream().noneMatch(run -> run.trace().size() > 1)) continue;
            write(directory.resolve(entry.getKey() + ".svg"), convergenceSvg(entry.getKey(), selected));
        }
    }

    private static List<RunObservation> bestTracePerAlgorithm(List<RunObservation> runs) {
        Map<AlgorithmId, RunObservation> selected = new LinkedHashMap<>();
        for (RunObservation run : runs) {
            if (run.trace().size() <= 1) continue;
            RunObservation previous = selected.get(run.algorithm());
            if (previous == null || run.solution().packedArea() > previous.solution().packedArea()) {
                selected.put(run.algorithm(), run);
            }
        }
        return new ArrayList<>(selected.values());
    }

    private static StringBuilder convergenceSvg(String instanceName, List<RunObservation> runs) {
        int width = 1100;
        int height = 650;
        int left = 85;
        int top = 70;
        int plotWidth = 770;
        int plotHeight = 500;
        long maxEvaluations = runs.stream().flatMap(run -> run.trace().stream())
                .mapToLong(ConvergencePoint::evaluations).max().orElse(1);
        double minimum = runs.stream().flatMap(run -> run.trace().stream())
                .mapToDouble(point -> 100.0 * point.bestPackedArea() / runs.get(0).instance().binArea())
                .min().orElse(0.0);
        double yMin = Math.max(0.0, Math.floor(minimum - 2.0));
        double yMax = 100.0;
        StringBuilder svg = svgStart(width, height);
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>");
        svg.append("<text x=\"55\" y=\"36\" font-size=\"24\" font-weight=\"700\">")
                .append(xml(instanceName)).append(" 收敛曲线（各算法最好一次运行）</text>");
        for (int i = 0; i <= 5; i++) {
            double yValue = yMin + (yMax - yMin) * i / 5.0;
            double y = top + plotHeight - plotHeight * i / 5.0;
            svg.append("<line x1=\"").append(left).append("\" y1=\"").append(f(y))
                    .append("\" x2=\"").append(left + plotWidth).append("\" y2=\"")
                    .append(f(y)).append("\" stroke=\"#e2e8f0\"/>");
            svg.append("<text x=\"").append(left - 12).append("\" y=\"").append(f(y + 5))
                    .append("\" text-anchor=\"end\" font-size=\"12\">")
                    .append(String.format(Locale.ROOT, "%.1f%%", yValue)).append("</text>");
        }
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(top + plotHeight)
                .append("\" x2=\"").append(left + plotWidth).append("\" y2=\"")
                .append(top + plotHeight).append("\" stroke=\"#0f172a\" stroke-width=\"2\"/>");
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(top)
                .append("\" x2=\"").append(left).append("\" y2=\"")
                .append(top + plotHeight).append("\" stroke=\"#0f172a\" stroke-width=\"2\"/>");
        for (int i = 0; i <= 5; i++) {
            long value = Math.round(maxEvaluations * i / 5.0);
            double x = left + plotWidth * i / 5.0;
            svg.append("<text x=\"").append(f(x)).append("\" y=\"").append(top + plotHeight + 24)
                    .append("\" text-anchor=\"middle\" font-size=\"12\">")
                    .append(value).append("</text>");
        }
        svg.append("<text x=\"").append(left + plotWidth / 2).append("\" y=\"")
                .append(height - 25).append("\" text-anchor=\"middle\" font-size=\"15\">候选解评价次数</text>");
        svg.append("<text transform=\"translate(22,").append(top + plotHeight / 2)
                .append(") rotate(-90)\" text-anchor=\"middle\" font-size=\"15\">历史最好利用率</text>");

        for (int index = 0; index < runs.size(); index++) {
            RunObservation run = runs.get(index);
            String color = COLORS[index % COLORS.length];
            StringBuilder points = new StringBuilder();
            for (ConvergencePoint point : run.trace()) {
                double x = left + plotWidth * point.evaluations() / Math.max(1.0, maxEvaluations);
                double utilization = 100.0 * point.bestPackedArea() / run.instance().binArea();
                double y = top + plotHeight * (yMax - utilization) / Math.max(0.0001, yMax - yMin);
                points.append(f(x)).append(',').append(f(y)).append(' ');
            }
            svg.append("<polyline points=\"").append(points)
                    .append("\" fill=\"none\" stroke=\"").append(color)
                    .append("\" stroke-width=\"2.5\"/>");
            double legendY = 90 + index * 30;
            svg.append("<line x1=\"885\" y1=\"").append(f(legendY))
                    .append("\" x2=\"915\" y2=\"").append(f(legendY))
                    .append("\" stroke=\"").append(color).append("\" stroke-width=\"4\"/>");
            svg.append("<text x=\"925\" y=\"").append(f(legendY + 5))
                    .append("\" font-size=\"13\">").append(xml(run.algorithm().displayName()))
                    .append("</text>");
        }
        return svg.append("</svg>");
    }

    private static void writeReport(
            Path file,
            String title,
            List<RunObservation> runs,
            List<SummaryObservation> summaries,
            PackingDecoderType decoderType, List<ConfigurationRun> configurations) throws IOException {
        Map<AlgorithmId, List<SummaryObservation>> groups = summaries.stream().collect(Collectors.groupingBy(
                SummaryObservation::algorithm, LinkedHashMap::new, Collectors.toList()));
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(xml(title)).append("</title><style>")
                .append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:0;background:#f1f5f9;color:#0f172a}")
                .append("main{max-width:1320px;margin:auto;padding:28px}.card{background:white;border-radius:14px;padding:22px;margin:18px 0;box-shadow:0 4px 18px #0f172a12}")
                .append("table{border-collapse:collapse;width:100%;font-size:13px}th,td{padding:9px 10px;border-bottom:1px solid #e2e8f0;text-align:right}th:first-child,td:first-child{text-align:left}th{background:#f8fafc;position:sticky;top:0}")
                .append("a{color:#1d4ed8}.ok{color:#047857;font-weight:700}.bad{color:#b91c1c;font-weight:700}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(330px,1fr));gap:14px}.grid img{width:100%;border:1px solid #e2e8f0;border-radius:10px}")
                .append("code{background:#e2e8f0;padding:2px 5px;border-radius:4px}.table-scroll{overflow-x:auto}</style></head><body><main>")
                .append("<h1>").append(xml(title)).append("</h1><p>生成时间：")
                .append(java.time.ZonedDateTime.now()).append("</p>")
                .append("<div class=\"card\"><h2>实验说明</h2><p>随机算法按多个随机种子独立运行；表中标准差反映稳定性。所有结果均经过越界、重叠、零件身份和旋转合法性验证。评价次数是比迭代次数更公平的主要计算量指标。</p>")
                .append("<p><a href=\"runs.csv\">逐次结果CSV</a> · <a href=\"summary.csv\">分实例汇总CSV</a> · <a href=\"overall.csv\">总览CSV</a> · <a href=\"instance-winners.csv\">逐实例冠军CSV</a> · <a href=\"traces.csv\">收敛轨迹CSV</a> · <a href=\"algorithm-catalog.csv\">完整算法能力目录</a></p></div>")
                .append("<div class=\"card\"><h2>算法总览</h2><table><thead><tr><th>算法</th><th>实例数</th><th>实例平均利用率</th><th>平均最好利用率</th><th>平均名次</th><th>平均耗时(ms)</th><th>合法运行</th></tr></thead><tbody>");
        for (AlgorithmId algorithm : AlgorithmCatalog.displayAlgorithms()) {
            List<SummaryObservation> values = groups.get(algorithm);
            if (values == null) continue;
            html.append("<tr><td>").append(xml(algorithm.displayName())).append("</td><td>")
                    .append(values.size()).append("</td><td>")
                    .append(f(values.stream().mapToDouble(SummaryObservation::meanUtilization).average().orElse(0))).append("%</td><td>")
                    .append(f(values.stream().mapToDouble(SummaryObservation::bestUtilization).average().orElse(0))).append("%</td><td>")
                    .append(f(averageRank(algorithm, summaries))).append("</td><td>")
                    .append(f(values.stream().mapToDouble(SummaryObservation::meanMilliseconds).average().orElse(0))).append("</td><td class=\"ok\">")
                    .append(values.stream().mapToInt(SummaryObservation::validRuns).sum()).append('/')
                    .append(values.stream().mapToInt(SummaryObservation::runs).sum()).append("</td></tr>");
        }
        html.append("</tbody></table></div>");
        if (!configurations.isEmpty()) {
            html.append("<div class=\"card\"><h2>逐实例最优配置</h2><p>固定搜索器与解码器，按合法运行平均利用率判定；并列配置分别列出。算法总览与分实例汇总按搜索器跨解码器聚合。</p>")
                    .append("<table><thead><tr><th>实例</th><th>搜索器</th><th>解码器</th><th>平均利用率</th><th>最好</th><th>合法运行</th><th>并列</th></tr></thead><tbody>");
            for (ConfigurationWinner item : configurationWinners(configurations)) {
                html.append("<tr><td>").append(xml(item.instance())).append("</td><td>")
                        .append(xml(item.algorithm())).append("</td><td>").append(xml(item.decoder()))
                        .append("</td><td>").append(f(item.mean())).append("%</td><td>").append(f(item.best()))
                        .append("%</td><td>").append(item.validRuns()).append('/').append(item.runs())
                        .append("</td><td>").append(item.tied() ? "是" : "否").append("</td></tr>");
            }
            html.append("</tbody></table></div>");
        } else {
            html.append("<div class=\"card\"><h2>逐实例最优算法</h2>")
                    .append("<p>在本次实验包含的全部家族策略配置中，按同一实例的多次合法运行平均利用率选冠军；并列冠军全部列出。最好值用于核对，不参与冠军判定。</p>")
                    .append("<table><thead><tr><th>实例</th><th>最优配置</th><th>平均利用率</th><th>最好利用率</th><th>标准差</th><th>合法运行</th><th>并列</th></tr></thead><tbody>");
            for (InstanceWinner winner : instanceWinners(summaries)) {
                html.append("<tr><td>").append(xml(winner.instance())).append("</td><td>")
                        .append(xml(winner.algorithm().displayName())).append("</td><td>")
                        .append(f(winner.meanUtilization())).append("%</td><td>")
                        .append(f(winner.bestUtilization())).append("%</td><td>")
                        .append(f(winner.standardDeviation())).append("</td><td class=\"ok\">")
                        .append(winner.validRuns()).append('/').append(winner.runs()).append("</td><td>")
                        .append(winner.tied() ? "是" : "否").append("</td></tr>");
            }
            html.append("</tbody></table></div>");
        }
        String budgetKind = runs.get(0).budgetKind();
        html.append("<div class=\"card\"><h2>比较口径</h2><p>");
        if (budgetKind.equals("EVALUATIONS")) {
            html.append("搜索算法使用相同候选解评价次数；构造式引擎为单次解码结果。该口径比较搜索效率，不代表墙钟成本完全相同。");
        } else if (budgetKind.equals("TIME_MS")) {
            html.append("搜索算法使用相同墙钟预算并经过预热；耗时仍可能受到JIT、垃圾回收和系统负载影响。");
        } else {
            html.append("结果使用各算法默认参数。构造式引擎与迭代搜索的计算预算不同，利用率排名应结合耗时和评价次数解释。");
        }
        html.append("</p>");
        if (decoderType == PackingDecoderType.SKYLINE) {
            appendSearchGainOverBaseline(html, summaries);
        } else if (summaries.stream().anyMatch(summary -> summary.algorithm() == AlgorithmId.SKYLINE)
                && summaries.stream().anyMatch(summary -> summary.algorithm().stochastic())) {
            html.append("<p><strong>未计算相对Skyline的搜索提升：</strong>本实验的搜索算法使用")
                    .append(xml(decoderType.name()))
                    .append("解码器，与构造式Skyline基线的解码口径不同。请使用decoder-matrix报告分离搜索与解码器影响。</p>");
        }
        html.append("<p>实验环境和解码器记录见 <a href=\"experiment-metadata.properties\">experiment-metadata.properties</a>。</p></div>");
        appendAlgorithmCatalog(html);
        appendMaxRectsBestDetails(html, runs);
        html.append("<div class=\"card\"><h2>分实例统计</h2><table><thead><tr><th>实例 / 算法</th><th>次数</th><th>最好</th><th>平均</th><th>最差</th><th>标准差</th><th>平均ms</th><th>平均评价</th></tr></thead><tbody>");
        for (SummaryObservation summary : summaries) {
            html.append("<tr><td>").append(xml(summary.instance())).append(" / ")
                    .append(xml(summary.algorithm().displayName())).append("</td><td>")
                    .append(summary.runs()).append("</td><td>").append(f(summary.bestUtilization()))
                    .append("%</td><td>").append(f(summary.meanUtilization())).append("%</td><td>")
                    .append(f(summary.worstUtilization())).append("%</td><td>")
                    .append(f(summary.standardDeviation())).append("</td><td>")
                    .append(f(summary.meanMilliseconds())).append("</td><td>")
                    .append(summary.meanEvaluations()).append("</td></tr>");
        }
        html.append("</tbody></table></div><div class=\"card\"><h2>收敛曲线</h2><div class=\"grid\">");
        runs.stream().map(run -> run.instance().name()).distinct()
                .sorted(ExperimentArtifacts::naturalNameCompare).forEach(instance -> {
                    Path chart = Path.of("convergence", instance + ".svg");
                    if (Files.exists(file.getParent().resolve(chart))) {
                        html.append("<a href=\"").append(chart).append("\"><img loading=\"lazy\" src=\"")
                                .append(chart).append("\" alt=\"").append(xml(instance)).append(" 收敛曲线\"></a>");
                    }
                });
        html.append("</div></div><div class=\"card\"><h2>最好排样图</h2><p>按实例和算法浏览 <a href=\"layouts/\">layouts目录</a>；SVG中悬停矩形可查看坐标、尺寸和旋转状态。</p></div>")
                .append("</main></body></html>");
        write(file, html);
    }

    /**
     * 当实验同时包含 Skyline 基线与搜索算法时，报告各搜索算法相对基线的
     * 平均利用率提升（百分点）。收益必须相对同一实验内的基线计算，
     * 避免不同实验目录之间直接比较。
     */
    private static void appendSearchGainOverBaseline(
            StringBuilder html, List<SummaryObservation> summaries) {
        Map<String, Double> baselineByInstance = new LinkedHashMap<>();
        for (SummaryObservation summary : summaries) {
            if (summary.algorithm() == AlgorithmId.SKYLINE) {
                baselineByInstance.put(summary.instance(), summary.meanUtilization());
            }
        }
        if (baselineByInstance.size() < 2) return;

        Map<AlgorithmId, List<Double>> gains = new LinkedHashMap<>();
        for (SummaryObservation summary : summaries) {
            Double baseline = baselineByInstance.get(summary.instance());
            if (baseline == null || summary.algorithm() == AlgorithmId.SKYLINE) continue;
            if (!summary.algorithm().stochastic()) continue;
            gains.computeIfAbsent(summary.algorithm(), ignored -> new ArrayList<>())
                    .add(summary.meanUtilization() - baseline);
        }
        if (gains.isEmpty()) return;

        html.append("<div class=\"table-scroll\"><table><thead><tr><th>搜索算法</th>")
                .append("<th>对比实例数</th><th>相对Skyline基线平均提升（百分点）</th>")
                .append("<th>提升&gt;0的实例数</th></tr></thead><tbody>");
        for (Map.Entry<AlgorithmId, List<Double>> entry : gains.entrySet()) {
            List<Double> values = entry.getValue();
            double meanGain = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            long positive = values.stream().filter(value -> value > 0).count();
            html.append("<tr><td>").append(xml(entry.getKey().displayName()))
                    .append("</td><td>").append(values.size())
                    .append("</td><td>").append(f(meanGain))
                    .append("</td><td>").append(positive).append('/').append(values.size())
                    .append("</td></tr>");
        }
        html.append("</tbody></table></div>");
    }

    private static void appendAlgorithmCatalog(StringBuilder html) {
        long primary = AlgorithmCatalog.entries().stream()
                .filter(entry -> entry.exposure() == AlgorithmCatalog.Exposure.PRIMARY)
                .count();
        long advanced = AlgorithmCatalog.entries().size() - primary;
        html.append("<div class=\"card\"><h2>算法能力结构</h2><p>目录按算法家族与策略配置组织；配置入口数不代表独立算法数。MaxRects-BEST 为五规则择优组合，非单次评分。Skyline GLOBAL 与顺序 WM 入口不可直接用于 WM 单变量归因。默认主界面展示 ")
                .append(primary).append(" 个工程主入口；另有 ")
                .append(advanced)
                .append(" 个已实现、可调用、可测试的高级策略不参与默认主榜。主展示按搜索优化60%、排样引擎40%组织，搜索层在前、排样层随后。完整数据见 <a href=\"algorithm-catalog.csv\">algorithm-catalog.csv</a>。</p>")
                .append("<div class=\"table-scroll\"><table><thead><tr><th>入口</th><th>状态</th><th>角色</th><th>家族</th><th>主入口</th><th>分层原因</th></tr></thead><tbody>");
        for (AlgorithmCatalog.Entry entry : AlgorithmCatalog.entries()) {
            html.append("<tr><td>").append(xml(entry.id().displayName()))
                    .append("</td><td>")
                    .append(entry.exposure() == AlgorithmCatalog.Exposure.PRIMARY
                            ? "工程主入口" : "高级/历史策略")
                    .append("</td><td>").append(switch (entry.role()) {
                        case OPTIMIZER -> "搜索优化";
                        case PACKING_ENGINE -> "排样引擎";
                        case VARIANT -> "策略变体";
                    })
                    .append("</td><td>").append(xml(entry.family()))
                    .append("</td><td>").append(xml(entry.representative().displayName()))
                    .append("</td><td style=\"text-align:left\">")
                    .append(xml(entry.rationale())).append("；")
                    .append(xml(AlgorithmCatalog.settings(entry.id())))
                    .append(AlgorithmCatalog.compatibilityOnly(entry.id()) ? "；兼容配置（默认收起）" : "")
                    .append("</td></tr>");
        }
        html.append("</tbody></table></div></div>");
    }

    private static void appendMaxRectsBestDetails(
            StringBuilder html, List<RunObservation> runs) {
        List<RunObservation> bestRuns = runs.stream()
                .filter(run -> run.maxRectsBestStatistics().available())
                .toList();
        if (bestRuns.isEmpty()) return;

        Map<String, List<RunObservation>> byInstance = bestRuns.stream().collect(Collectors.groupingBy(
                run -> run.instance().name(), LinkedHashMap::new, Collectors.toList()));
        List<List<RunObservation>> instanceGroups = new ArrayList<>(byInstance.values());
        instanceGroups.sort(Comparator.comparing(
                group -> group.get(0).instance().name(), ExperimentArtifacts::naturalNameCompare));
        List<RunObservation> representatives = instanceGroups.stream()
                .map(group -> group.get(0))
                .toList();

        long improvedOverBssf = representatives.stream()
                .filter(run -> run.solution().packedArea()
                        > run.maxRectsBestStatistics().entry(MaxRectsHeuristic.BSSF).packedArea())
                .count();
        double meanGainPoints = representatives.stream().mapToDouble(run -> {
            long bssf = run.maxRectsBestStatistics().entry(MaxRectsHeuristic.BSSF).packedArea();
            return 100.0 * (run.solution().packedArea() - bssf) / run.instance().binArea();
        }).average().orElse(0.0);
        double meanBestNanos = bestRuns.stream()
                .mapToLong(run -> run.solution().elapsedNanos()).average().orElse(0.0);
        double meanStandaloneBssfNanos = runs.stream()
                .filter(run -> run.algorithm() == AlgorithmId.MAXRECTS_BSSF)
                .mapToLong(run -> run.solution().elapsedNanos()).average().orElse(0.0);
        double meanTimeFactor = meanStandaloneBssfNanos == 0.0
                ? 0.0
                : meanBestNanos / meanStandaloneBssfNanos;

        String selectedCounts = java.util.Arrays.stream(MaxRectsHeuristic.values())
                .map(heuristic -> heuristic + "=" + representatives.stream()
                        .filter(run -> run.maxRectsBestStatistics().selectedHeuristic() == heuristic)
                        .count())
                .collect(Collectors.joining("，"));

        html.append("<div class=\"card\"><h2>MaxRects-BEST 组成明细</h2><p>")
                .append("稳定择优次数：").append(xml(selectedCounts))
                .append("。相对BSSF严格改善 ").append(improvedOverBssf).append('/')
                .append(representatives.size()).append(" 个实例，平均提高 ")
                .append(f(meanGainPoints)).append(" 个利用率百分点；相对独立BSSF的实测平均耗时因子 ")
                .append(f(meanTimeFactor)).append("×。成员运行时间很短时会受JIT和计时噪声影响，")
                .append("本表按实例聚合预热后的重复运行。</p>")
                .append("<div class=\"table-scroll\"><table><thead><tr><th>实例</th><th>采用</th><th>并列最优</th><th>BEST面积</th><th>较BSSF增益</th><th>总耗时ms</th>");
        for (MaxRectsHeuristic heuristic : MaxRectsHeuristic.values()) {
            html.append("<th>").append(heuristic).append(" 面积 / ms</th>");
        }
        html.append("</tr></thead><tbody>");
        for (List<RunObservation> group : instanceGroups) {
            RunObservation run = group.get(0);
            MaxRectsBestStatistics statistics = run.maxRectsBestStatistics();
            long bssf = statistics.entry(MaxRectsHeuristic.BSSF).packedArea();
            html.append("<tr><td>").append(xml(run.instance().name())).append("</td><td>")
                    .append(statistics.selectedHeuristic()).append("</td><td>")
                    .append(xml(maxRectsTiedBest(run))).append("</td><td>")
                    .append(run.solution().packedArea()).append("</td><td>")
                    .append(run.solution().packedArea() - bssf).append(" / ")
                    .append(f(100.0 * (run.solution().packedArea() - bssf)
                            / run.instance().binArea())).append("pp</td><td>")
                    .append(f(group.stream().mapToDouble(RunObservation::elapsedMilliseconds)
                            .average().orElse(0.0))).append("</td>");
            for (MaxRectsHeuristic heuristic : MaxRectsHeuristic.values()) {
                MaxRectsBestStatistics.Entry entry = statistics.entry(heuristic);
                html.append("<td>").append(entry.packedArea()).append(" / ")
                        .append(f(group.stream().mapToDouble(member -> member.maxRectsBestStatistics()
                                .entry(heuristic).elapsedMilliseconds()).average().orElse(0.0)))
                        .append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div></div>");
    }

    private static String maxRectsSelected(RunObservation run) {
        MaxRectsBestStatistics statistics = run.maxRectsBestStatistics();
        return statistics.available() ? statistics.selectedHeuristic().name() : "";
    }

    private static String maxRectsTiedBest(RunObservation run) {
        MaxRectsBestStatistics statistics = run.maxRectsBestStatistics();
        if (!statistics.available()) return "";
        return statistics.tiedBestHeuristics().stream()
                .map(Enum::name)
                .collect(Collectors.joining("|"));
    }

    private static String maxRectsArea(
            RunObservation run, MaxRectsHeuristic heuristic) {
        MaxRectsBestStatistics statistics = run.maxRectsBestStatistics();
        return statistics.available() ? Long.toString(statistics.entry(heuristic).packedArea()) : "";
    }

    private static String maxRectsMilliseconds(
            RunObservation run, MaxRectsHeuristic heuristic) {
        MaxRectsBestStatistics statistics = run.maxRectsBestStatistics();
        return statistics.available() ? f(statistics.entry(heuristic).elapsedMilliseconds()) : "";
    }

    private static double mean(double[] values) {
        double total = 0.0;
        for (double value : values) total += value;
        return values.length == 0 ? 0.0 : total / values.length;
    }

    private static double averageRank(AlgorithmId algorithm, List<SummaryObservation> summaries) {
        List<SummaryObservation> own = summaries.stream()
                .filter(summary -> summary.algorithm() == algorithm).toList();
        if (own.isEmpty()) return 0.0;
        double total = 0.0;
        for (SummaryObservation value : own) {
            long strictlyBetter = summaries.stream()
                    .filter(other -> other.instance().equals(value.instance()))
                    .filter(other -> other.meanUtilization() > value.meanUtilization() + 1.0e-9)
                    .count();
            total += 1 + strictlyBetter;
        }
        return total / own.size();
    }

    private static void row(StringBuilder text, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) text.append(',');
            text.append(csv(String.valueOf(values[i])));
        }
        text.append('\n');
    }

    private static String csv(String value) {
        return CsvEscaper.escape(value);
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String xml(Object value) {
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String itemColor(int id) {
        return COLORS[Math.floorMod(id * 7, COLORS.length)];
    }

    private static StringBuilder svgStart(int width, int height) {
        return new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height)
                .append("\" role=\"img\" font-family=\"-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif\">");
    }

    private static void write(Path file, CharSequence content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static int naturalNameCompare(String first, String second) {
        return Integer.compare(numberIn(first), numberIn(second));
    }

    private static int numberIn(String text) {
        String digits = text.replaceAll("\\D+", "");
        return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }
}
