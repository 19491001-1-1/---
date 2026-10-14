package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackingRunConfigurationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsDefaultsFromEmptyConfiguration() throws IOException {
        PackingRunConfiguration configuration = read("");

        assertEquals(Path.of("gcut01"), configuration.dataDirectory());
        assertTrue(configuration.rotationAllowed());
        assertEquals(ItemOrder.AREA_DESC, configuration.order());
        assertEquals(AlgorithmId.ADAPTIVE_GA, configuration.algorithm());
        assertEquals(PackingDecoderType.SKYLINE, configuration.decoder());
        assertEquals(ExperimentProfile.QUICK, configuration.profile());
        assertEquals(20261012L, configuration.seed());
        assertEquals(Long.MAX_VALUE, configuration.searchLimit().maxEvaluations());
        assertEquals(Long.MAX_VALUE, configuration.searchLimit().maxNanos());
    }

    @Test
    void readsExplicitEvaluationConfiguration() throws IOException {
        PackingRunConfiguration configuration = read("""
                dataDirectory=data/custom
                rotationAllowed=false
                order=INPUT
                algorithm=ILS
                decoder=MAXRECTS
                profile=STANDARD
                seed=17
                maxEvaluations=321
                """);

        assertEquals(Path.of("data/custom"), configuration.dataDirectory());
        assertFalse(configuration.rotationAllowed());
        assertEquals(ItemOrder.INPUT, configuration.order());
        assertEquals(AlgorithmId.ILS, configuration.algorithm());
        assertEquals(PackingDecoderType.MAXRECTS, configuration.decoder());
        assertEquals(ExperimentProfile.STANDARD, configuration.profile());
        assertEquals(17L, configuration.seed());
        assertEquals(321L, configuration.searchLimit().maxEvaluations());
    }

    @Test
    void readsTimeBudgetInMilliseconds() throws IOException {
        PackingRunConfiguration configuration = read("maxMilliseconds=7\n");
        assertEquals(7_000_000L, configuration.searchLimit().maxNanos());
        assertEquals(Long.MAX_VALUE, configuration.searchLimit().maxEvaluations());
    }

    @Test
    void rejectsUnknownKeysAndAmbiguousBudgets() {
        assertThrows(IllegalArgumentException.class, () -> read("profil=QUICK\n"));
        assertThrows(IllegalArgumentException.class,
                () -> read("maxEvaluations=10\nmaxMilliseconds=10\n"));
        assertThrows(IllegalArgumentException.class,
                () -> read("rotationAllowed=yes\n"));
    }

    private PackingRunConfiguration read(String text) throws IOException {
        Path file = temporaryDirectory.resolve("run.properties");
        Files.writeString(file, text);
        return PackingRunConfiguration.read(file);
    }
}
