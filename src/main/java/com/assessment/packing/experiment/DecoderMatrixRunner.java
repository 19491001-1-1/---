package com.assessment.packing.experiment;

import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs the same optimizer set against every supported packing decoder. */
final class DecoderMatrixRunner {
    private DecoderMatrixRunner() {}

    static void run(
            Path data,
            Path output,
            long evaluationBudget,
            int repetitions,
            long baseSeed,
            ExperimentProfile profile) throws IOException {
        if (evaluationBudget <= 0) throw new IllegalArgumentException("评价预算必须为正数");
        if (repetitions <= 0) throw new IllegalArgumentException("重复次数必须为正数");
        Files.createDirectories(output);
        List<Long> seeds = seeds(repetitions, baseSeed);
        List<MatrixRun> matrixRuns = new ArrayList<>();
        ExperimentRunner runner = new ExperimentRunner();

        for (PackingDecoderType decoder : PackingDecoderType.values()) {
            String directoryName = decoder.name().toLowerCase(Locale.ROOT);
            ExperimentRunner.Options options = new ExperimentRunner.Options(
                    data,
                    output.resolve(directoryName),
                    AlgorithmCatalog.optimizerAlgorithms(),
                    true,
                    ItemOrder.AREA_DESC,
                    seeds,
                    profile,
                    SearchLimit.evaluations(evaluationBudget),
                    "搜索算法 × " + decoder + " 解码器",
                    "EVALUATIONS",
                    evaluationBudget,
                    false,
                    decoder,
                    true);
            for (RunObservation observation : runner.run(options)) {
                matrixRuns.add(new MatrixRun(decoder, observation));
            }
        }

        writeCsv(output.resolve("decoder-matrix.csv"), matrixRuns);
        writeIndex(output.resolve("index.html"), matrixRuns, evaluationBudget, repetitions);
        System.out.println("解码器矩阵报告：" + output.resolve("index.html").toAbsolutePath());
    }

    private static List<Long> seeds(int repetitions, long baseSeed) {
        List<Long> result = new ArrayList<>(repetitions);
        for (int index = 0; index < repetitions; index++) {
            result.add(baseSeed + index * 104_729L);
        }
        return List.copyOf(result);
    }

    private static void writeCsv(Path file, List<MatrixRun> runs) throws IOException {
        StringBuilder csv = new StringBuilder(
                "decoder,algorithm,instance,repetition,seed,packed_area,utilization_percent,"
                        + "elapsed_ms,evaluations,valid\n");
        for (MatrixRun matrixRun : runs) {
            RunObservation run = matrixRun.run();
            csv.append(matrixRun.decoder()).append(',')
                    .append(run.algorithm()).append(',')
                    .append(CsvEscaper.escape(run.instance().name())).append(',')
                    .append(run.repetition()).append(',')
                    .append(run.seed()).append(',')
                    .append(run.solution().packedArea()).append(',')
                    .append(format(run.utilizationPercent())).append(',')
                    .append(format(run.elapsedMilliseconds())).append(',')
                    .append(run.evaluations()).append(',')
                    .append(run.valid()).append('\n');
        }
        Files.writeString(file, csv, StandardCharsets.UTF_8);
    }

    private static void writeIndex(
            Path file, List<MatrixRun> runs, long evaluationBudget, int repetitions)
            throws IOException {
        Map<String, List<RunObservation>> groups = new LinkedHashMap<>();
        for (MatrixRun matrixRun : runs) {
            String key = matrixRun.decoder() + "\u0000" + matrixRun.run().algorithm();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(matrixRun.run());
        }
        StringBuilder rows = new StringBuilder();
        for (Map.Entry<String, List<RunObservation>> entry : groups.entrySet()) {
            String[] key = entry.getKey().split("\u0000", -1);
            List<RunObservation> values = entry.getValue();
            double mean = values.stream().mapToDouble(RunObservation::utilizationPercent)
                    .average().orElse(0);
            long valid = values.stream().filter(RunObservation::valid).count();
            rows.append("<tr><td>").append(key[0]).append("</td><td>")
                    .append(key[1]).append("</td><td>").append(values.size())
                    .append("</td><td>").append(format(mean)).append("%</td><td>")
                    .append(valid).append('/').append(values.size()).append("</td></tr>");
        }
        StringBuilder links = new StringBuilder(
                "<a href=\"decoder-matrix.csv\">交叉实验原始CSV</a>");
        for (PackingDecoderType decoder : PackingDecoderType.values()) {
            String directory = decoder.name().toLowerCase(Locale.ROOT);
            links.append(" · <a href=\"").append(directory).append("/report.html\">")
                    .append(decoder).append("报告</a>");
        }
        String html = """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>搜索算法与解码器交叉实验</title><style>
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f1f5f9;color:#0f172a;margin:0}
                main{max-width:1000px;margin:auto;padding:32px}.card{background:white;border-radius:14px;padding:22px;margin:18px 0;box-shadow:0 4px 18px #0f172a12}
                table{border-collapse:collapse;width:100%%}th,td{padding:9px 10px;border-bottom:1px solid #e2e8f0;text-align:right}th:first-child,td:first-child{text-align:left}
                a{color:#1d4ed8}</style></head><body><main><h1>搜索算法 × 排样解码器</h1>
                <div class="card"><p>六个搜索主入口分别与 %d 个遵守优先级顺序的排样解码器组合；
                每次搜索统一限制为 %d 次候选解评价，每个随机算法使用 %d 个种子。</p>
                <p>该口径控制搜索评价次数，不假设各解码器的单次计算成本相同；质量与耗时应结合原始CSV解释。</p>
                <p>%s</p></div>
                <div class="card"><table><thead><tr><th>解码器</th><th>搜索算法</th><th>运行数</th>
                <th>平均利用率</th><th>合法运行</th></tr></thead><tbody>%s</tbody></table></div>
                </main></body></html>
                """.formatted(PackingDecoderType.values().length, evaluationBudget,
                repetitions, links, rows);
        Files.writeString(file, html, StandardCharsets.UTF_8);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private record MatrixRun(PackingDecoderType decoder, RunObservation run) {}
}
