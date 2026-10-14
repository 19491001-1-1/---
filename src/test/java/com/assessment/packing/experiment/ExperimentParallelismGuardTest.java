package com.assessment.packing.experiment;

import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并行的护栏与端到端一致性。
 *
 * <p>前一组用例已经在内存里比对过「并行产出与串行逐位相同」；这里补上两件实验级的事：
 * 并行度必须有上限（否则一次手误就把机器打满），以及<b>带断点续跑</b>时并行仍不得让
 * 检查点互相覆盖、产出错乱。
 */
final class ExperimentParallelismGuardTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parallelismIsCappedToAvoidSaturatingTheMachine() {
        Path data = temporaryDirectory.resolve("data");
        int tooMany = ExperimentRunner.Options.MAX_PARALLELISM + 1;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> options(data, tooMany, false));
        assertTrue(error.getMessage().contains(Integer.toString(ExperimentRunner.Options.MAX_PARALLELISM)),
                "报错应说明上限，实际=" + error.getMessage());
    }

    @Test
    void parallelismAcceptsItsUpperBound() throws IOException {
        Path data = seededData();
        // 上限本身必须可用：任务数不足时 ExecutorService 只会开更少线程。
        List<RunObservation> runs = new ExperimentRunner().run(
                options(data, ExperimentRunner.Options.MAX_PARALLELISM, false));
        assertEquals(4, runs.size(), "并行不得增减运行次数");
    }

    @Test
    void parallelResumeMatchesParallelWithoutResume() throws IOException {
        Path data = seededData();
        Path interrupted = temporaryDirectory.resolve("interrupted");
        Path uninterrupted = temporaryDirectory.resolve("uninterrupted");

        // 先跑一次留下检查点，再删除产出文件模拟「上次被打断」。
        ExperimentRunner.Options interruptedOptions = options(data, interrupted, 4, true);
        List<RunObservation> first = new ExperimentRunner().run(interruptedOptions);
        Files.deleteIfExists(interrupted.resolve("runs.csv"));

        // 断点文件已被正常收尾删除，这里重新写一份，模拟真正的中断现场。
        RunCheckpoint.save(interrupted.resolve(".experiment-checkpoint.bin"), interruptedOptions, first);
        List<RunObservation> resumed = new ExperimentRunner().run(interruptedOptions);

        List<RunObservation> fresh = new ExperimentRunner().run(
                options(data, uninterrupted, 4, false));

        assertEquals(fresh.size(), resumed.size(), "续跑不得改变运行次数");
        for (int index = 0; index < fresh.size(); index++) {
            RunObservation expected = fresh.get(index);
            RunObservation actual = resumed.get(index);
            assertEquals(expected.algorithm(), actual.algorithm(), "第" + index + "项算法");
            assertEquals(expected.repetition(), actual.repetition(), "第" + index + "项重复序号");
            assertEquals(expected.seed(), actual.seed(), "第" + index + "项种子");
            assertEquals(expected.solution().packedArea(), actual.solution().packedArea(),
                    "第" + index + "项装载面积");
            assertEquals(expected.solution().placements(), actual.solution().placements(),
                    "第" + index + "项布局");
            assertEquals(expected.evaluations(), actual.evaluations(), "第" + index + "项评价次数");
        }
    }

    /** 并行 + 断点续跑时，产出的 runs.csv 也必须与串行一致（耗时列除外）。 */
    @Test
    void parallelResumeWritesSameRunRowsAsSerial() throws IOException {
        Path data = seededData();
        Path parallel = temporaryDirectory.resolve("parallel-resume");
        Path serial = temporaryDirectory.resolve("serial");

        ExperimentRunner.Options parallelOptions = options(data, parallel, 4, true);
        List<RunObservation> completed = new ExperimentRunner().run(parallelOptions);
        RunCheckpoint.save(parallel.resolve(".experiment-checkpoint.bin"), parallelOptions, completed);
        new ExperimentRunner().run(parallelOptions);

        new ExperimentRunner().run(options(data, serial, 1, false));

        assertEquals(withoutTimingColumns(serial.resolve("runs.csv")),
                withoutTimingColumns(parallel.resolve("runs.csv")),
                "并行续跑的 runs.csv 除耗时列外必须与串行一致");
    }

    /**
     * 剔除墙钟耗时列后的 CSV 内容，用于跨运行的确定性比对。
     *
     * <p>耗时不只 {@code elapsed_ms} 一列：MaxRects 会把五种启发式各自的耗时也写进
     * {@code bssf_ms}/{@code blsf_ms} 等列。它们都是墙钟时间，在任何两次运行之间都不可复现，
     * 与「并行是否改变结果」无关，因此按表头定位后整列剔除。
     */
    private static String withoutTimingColumns(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        java.util.Set<Integer> timingColumns = new java.util.HashSet<>();
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
        return options(data, temporaryDirectory.resolve("output-p" + parallelism + "-" + resume),
                parallelism, resume);
    }

    private ExperimentRunner.Options options(
            Path data, Path output, int parallelism, boolean resume) {
        return new ExperimentRunner.Options(
                data, output, List.of(AlgorithmId.ILS, AlgorithmId.TABU_MOVE),
                true, ItemOrder.AREA_DESC, List.of(99L, 100L), ExperimentProfile.QUICK,
                SearchLimit.evaluations(20), "并行护栏测试", "EVALUATIONS", 20, false,
                PackingDecoderType.SKYLINE, resume, parallelism);
    }
}
