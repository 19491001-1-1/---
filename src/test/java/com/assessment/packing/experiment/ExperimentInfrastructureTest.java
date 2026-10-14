package com.assessment.packing.experiment;

import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.MaxRectsBestStatistics;
import com.assessment.packing.solver.MaxRectsHeuristic;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExperimentInfrastructureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsStructuredExperimentConfiguration() throws IOException {
        Path file = write("""
                dataDirectory=benchmarks
                outputDirectory=results/test
                algorithms=ILS,SA
                decoder=GUILLOTINE
                rotationAllowed=false
                order=INPUT
                repetitions=3
                baseSeed=100
                profile=QUICK
                maxEvaluations=250
                experimentName=configuration test
                resume=false
                repeatDeterministic=true
                """);

        ExperimentRunner.Options options = ExperimentRunConfiguration.read(file);
        assertEquals(Path.of("benchmarks"), options.dataDirectory());
        assertEquals(Path.of("results/test"), options.outputDirectory());
        assertEquals(List.of(AlgorithmId.ILS, AlgorithmId.SA), options.algorithms());
        assertEquals(PackingDecoderType.GUILLOTINE, options.decoderType());
        assertFalse(options.rotationAllowed());
        assertEquals(ItemOrder.INPUT, options.order());
        assertEquals(List.of(100L, 104_829L, 209_558L), options.seeds());
        assertEquals(250L, options.searchLimit().maxEvaluations());
        assertEquals("EVALUATIONS", options.budgetKind());
        assertEquals(250L, options.budgetValue());
        assertFalse(options.resume());
        assertTrue(options.repeatDeterministic());
        assertEquals(1, options.parallelism(), "未配置时应默认串行");
    }

    @Test
    void acceptsParallelismAndRejectsUnfairCombination() throws IOException {
        ExperimentRunner.Options parallel = ExperimentRunConfiguration.read(write("""
                algorithms=ILS
                repetitions=2
                maxEvaluations=50
                parallelism=4
                """));
        assertEquals(4, parallel.parallelism());

        assertThrows(IllegalArgumentException.class, () -> ExperimentRunConfiguration.read(write("""
                algorithms=ILS
                repetitions=1
                maxMilliseconds=12
                parallelism=2
                """)), "并行加时间预算必须被拒绝");
        assertThrows(IllegalArgumentException.class, () -> ExperimentRunConfiguration.read(write("""
                algorithms=ILS
                repetitions=1
                parallelism=0
                """)));
    }

    @Test
    void supportsAlgorithmGroupsAndTimeBudget() throws IOException {
        ExperimentRunner.Options options = ExperimentRunConfiguration.read(write("""
                algorithms=ENGINES
                repetitions=1
                maxMilliseconds=12
                """));

        assertEquals(AlgorithmCatalog.packingEngineAlgorithms(), options.algorithms());
        assertEquals(12_000_000L, options.searchLimit().maxNanos());
        assertEquals("TIME_MS", options.budgetKind());
        assertTrue(options.resume());
    }

    @Test
    void rejectsInvalidExperimentConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentRunConfiguration.read(write("unknown=value\n")));
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentRunConfiguration.read(write("repetitions=0\n")));
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentRunConfiguration.read(write(
                        "maxEvaluations=1\nmaxMilliseconds=1\n")));
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentRunConfiguration.read(write("resume=perhaps\n")));
    }

    @Test
    void checkpointRoundTripPreservesCompleteObservation() throws IOException {
        Instance instance = instance();
        ExperimentRunner.Options options = options(PackingDecoderType.MAXRECTS);
        MaxRectsBestStatistics statistics = new MaxRectsBestStatistics(
                MaxRectsHeuristic.BSSF,
                List.of(new MaxRectsBestStatistics.Entry(
                        MaxRectsHeuristic.BSSF, 12, 1, 900)));
        RunObservation expected = new RunObservation(
                "checkpoint", "EVALUATIONS", 100, AlgorithmId.ILS, instance,
                1, 99, new Solution("test solver",
                List.of(new Placement(instance.items().get(0), 2, 3, false)), 1_200),
                true, "", 44, 8, statistics,
                List.of(new ConvergencePoint(1, 2, 12)));
        Path checkpoint = temporaryDirectory.resolve("checkpoint.bin");

        RunCheckpoint.save(checkpoint, options, List.of(expected));
        List<RunObservation> restored = RunCheckpoint.load(
                checkpoint, options, List.of(instance));

        assertEquals(List.of(expected), restored);
    }

    @Test
    void checkpointRejectsDifferentConfigurationAndVersion() throws IOException {
        Instance instance = instance();
        Path checkpoint = temporaryDirectory.resolve("checkpoint.bin");
        RunCheckpoint.save(checkpoint, options(PackingDecoderType.SKYLINE), List.of());

        assertThrows(IllegalStateException.class, () -> RunCheckpoint.load(
                checkpoint, options(PackingDecoderType.GUILLOTINE), List.of(instance)));

        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(checkpoint))) {
            output.writeInt(999);
        }
        assertThrows(IllegalStateException.class, () -> RunCheckpoint.load(
                checkpoint, options(PackingDecoderType.SKYLINE), List.of(instance)));
        assertTrue(RunCheckpoint.load(temporaryDirectory.resolve("missing.bin"),
                options(PackingDecoderType.SKYLINE), List.of(instance)).isEmpty());
    }

    @Test
    void runnerResumesWithoutRepeatingCompletedRun() throws IOException {
        Path data = temporaryDirectory.resolve("data");
        Path output = temporaryDirectory.resolve("resumed-output");
        Files.createDirectories(data);
        Files.writeString(data.resolve("gcut1.txt"), """
                2
                10 10
                4 3 12
                3 2 6
                """);
        Instance instance = ExperimentRunner.readInstances(data).get(0);
        ExperimentRunner.Options options = new ExperimentRunner.Options(
                data, output, List.of(AlgorithmId.ILS), true, ItemOrder.AREA_DESC,
                List.of(99L, 100L), ExperimentProfile.QUICK,
                SearchLimit.evaluations(20), "resume integration", "EVALUATIONS",
                20, false, PackingDecoderType.SKYLINE, true);
        RunObservation completed = new RunObservation(
                "resume integration", "EVALUATIONS", 20, AlgorithmId.ILS,
                instance, 1, 99L,
                new Solution("existing", List.of(
                        new Placement(instance.items().get(0), 0, 0, false)), 1),
                true, "", 20, 1, MaxRectsBestStatistics.empty(), List.of());
        Path checkpoint = output.resolve(".experiment-checkpoint.bin");
        RunCheckpoint.save(checkpoint, options, List.of(completed));

        List<RunObservation> result = new ExperimentRunner().run(options);

        assertEquals(2, result.size());
        assertEquals(List.of(1, 2), result.stream().map(RunObservation::repetition).toList());
        assertEquals(List.of(99L, 100L), result.stream().map(RunObservation::seed).toList());
        assertFalse(Files.exists(checkpoint), "实验成功后应删除checkpoint");
        assertTrue(Files.exists(output.resolve("report.html")));
        assertTrue(Files.exists(output.resolve("experiment-metadata.properties")));
    }

    @Test
    void reportDoesNotMixSkylineBaselineWithAnotherDecoder() throws IOException {
        Path data = temporaryDirectory.resolve("comparison-data");
        Path output = temporaryDirectory.resolve("comparison-output");
        Files.createDirectories(data);
        Files.writeString(data.resolve("gcut1.txt"), """
                2
                10 10
                4 3 12
                3 2 6
                """);
        ExperimentRunner.Options options = new ExperimentRunner.Options(
                data, output, List.of(AlgorithmId.SKYLINE, AlgorithmId.ILS),
                true, ItemOrder.AREA_DESC, List.of(99L), ExperimentProfile.QUICK,
                SearchLimit.evaluations(20), "decoder comparison", "EVALUATIONS",
                20, false, PackingDecoderType.MAXRECTS, false);

        new ExperimentRunner().run(options);

        String report = Files.readString(output.resolve("report.html"));
        assertTrue(report.contains("未计算相对Skyline的搜索提升"));
        assertTrue(report.contains("decoder-matrix"));
    }

    private Path write(String text) throws IOException {
        Path file = temporaryDirectory.resolve("experiment.properties");
        Files.writeString(file, text);
        return file;
    }

    private ExperimentRunner.Options options(PackingDecoderType decoder) {
        return new ExperimentRunner.Options(
                temporaryDirectory, temporaryDirectory.resolve("output"),
                List.of(AlgorithmId.ILS), true, ItemOrder.AREA_DESC, List.of(99L),
                ExperimentProfile.QUICK, SearchLimit.evaluations(100), "checkpoint",
                "EVALUATIONS", 100, false, decoder, true);
    }

    private static Instance instance() {
        return new Instance("checkpoint-instance", 10, 10,
                List.of(new Item(1, 4, 3)));
    }
}
