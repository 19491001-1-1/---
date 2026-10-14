package com.assessment.packing.experiment;

import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实验级并行的等价性：并行只改变「谁来跑」，绝不能改变「跑出什么、以什么顺序列出」。
 * 因此这里逐位比对并行与串行的观测列表，并守住并行 + 时间预算这一不公平组合。
 */
final class ExperimentParallelismTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parallelRunsMatchSerialOrderAndValues() throws IOException {
        Path data = seededData();
        List<RunObservation> serial = new ExperimentRunner().run(options(data, 1, true));
        List<RunObservation> parallel = new ExperimentRunner().run(options(data, 4, false));

        assertEquals(serial.size(), parallel.size(), "并行不得增减运行次数");
        for (int index = 0; index < serial.size(); index++) {
            RunObservation expected = serial.get(index);
            RunObservation actual = parallel.get(index);
            assertEquals(expected.instance().name(), actual.instance().name(), "第" + index + "项实例顺序");
            assertEquals(expected.algorithm(), actual.algorithm(), "第" + index + "项算法顺序");
            assertEquals(expected.repetition(), actual.repetition(), "第" + index + "项重复序号");
            assertEquals(expected.seed(), actual.seed(), "第" + index + "项种子");
            assertEquals(expected.solution().packedArea(), actual.solution().packedArea(),
                    "第" + index + "项装载面积");
            assertEquals(expected.solution().placements(), actual.solution().placements(),
                    "第" + index + "项布局");
            assertEquals(expected.evaluations(), actual.evaluations(), "第" + index + "项评价次数");
            assertEquals(expected.valid(), actual.valid(), "第" + index + "项合法性");
        }
    }

    @Test
    void parallelRunWritesSameArtifactsAsSerial() throws IOException {
        Path data = seededData();
        Path serialOutput = temporaryDirectory.resolve("serial-artifacts");
        Path parallelOutput = temporaryDirectory.resolve("parallel-artifacts");

        new ExperimentRunner().run(options(data, serialOutput, 1, true));
        new ExperimentRunner().run(options(data, parallelOutput, 4, false));

        // elapsed_ms 是墙钟时间，天然不可复现，因此比对时剔除该列，其余列必须逐字一致。
        assertEquals(withoutTimingColumns(serialOutput.resolve("runs.csv")),
                withoutTimingColumns(parallelOutput.resolve("runs.csv")),
                "并行与串行的 runs.csv 除耗时列外必须一致");
        assertTrue(Files.exists(parallelOutput.resolve("report.html")));
    }

    @Test
    void parallelismIsRejectedForTimeBudget() {
        Path data = temporaryDirectory.resolve("data");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new ExperimentRunner.Options(
                        data, temporaryDirectory.resolve("time-output"),
                        List.of(AlgorithmId.ILS), true, ItemOrder.AREA_DESC, List.of(99L),
                        ExperimentProfile.QUICK, SearchLimit.milliseconds(2000),
                        "时间预算并行", "TIME_MS", 2000, false,
                        PackingDecoderType.SKYLINE, false, 4));
        assertTrue(error.getMessage().contains("时间预算"), "报错应说明时间预算不可并行");
    }

    /**
     * 读取 CSV 并抹掉所有耗时列（`*_ms`），用于内容比对。
     *
     * <p>耗时不只 {@code elapsed_ms} 一列：MaxRects 会把五种启发式各自的耗时也写进
     * {@code bssf_ms}/{@code blsf_ms} 等列。它们都是墙钟时间，在任何两次运行之间都不可复现，
     * 与「并行是否改变结果」无关，因此统一剔除。
     */
    private static String withoutTimingColumns(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        Set<Integer> timingColumns = new HashSet<>();
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            String[] cells = lines.get(index).split(",", -1);
            if (index == 0) {
                for (int column = 0; column < cells.length; column++) {
                    String header = cells[column];
                    if (header.equals("elapsed_ms") || header.endsWith("_ms")) {
                        timingColumns.add(column);
                    }
                }
            }
            for (int column = 0; column < cells.length; column++) {
                if (timingColumns.contains(column)) continue;
                if (column > 0) result.append(',');
                result.append(cells[column]);
            }
            result.append('\n');
        }
        return result.toString();
    }

    private Path seededData() throws IOException {
        Path data = temporaryDirectory.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("gcut1.txt"), """
                2
                10 10
                4 3 12
                3 2 6
                """);
        return data;
    }

    private ExperimentRunner.Options options(Path data, int parallelism, boolean resume) {
        return options(data, temporaryDirectory.resolve("output-p" + parallelism), parallelism, resume);
    }

    private ExperimentRunner.Options options(
            Path data, Path output, int parallelism, boolean resume) {
        return new ExperimentRunner.Options(
                data, output, List.of(AlgorithmId.ILS, AlgorithmId.TABU_MOVE),
                true, ItemOrder.AREA_DESC, List.of(99L, 100L), ExperimentProfile.QUICK,
                SearchLimit.evaluations(20), "并行测试", "EVALUATIONS", 20, false,
                PackingDecoderType.SKYLINE, resume, parallelism);
    }
}
