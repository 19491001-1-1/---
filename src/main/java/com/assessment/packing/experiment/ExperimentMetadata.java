package com.assessment.packing.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

final class ExperimentMetadata {
    private ExperimentMetadata() {}

    static void write(Path output, ExperimentRunner.Options options) throws IOException {
        Files.createDirectories(output);
        String text = """
                experiment=%s
                generatedAtUtc=%s
                projectVersion=1.0.0
                dataDirectory=%s
                algorithms=%s
                decoder=%s
                rotationAllowed=%s
                order=%s
                seeds=%s
                profile=%s
                searchLimit=%s
                budgetKind=%s
                budgetValue=%d
                parallelism=%d
                javaVersion=%s
                os=%s
                """.formatted(
                options.experimentName(), Instant.now(),
                options.dataDirectory().toAbsolutePath().normalize(), options.algorithms(),
                options.decoderType(), options.rotationAllowed(), options.order(), options.seeds(),
                options.profile(), options.searchLimit(), options.budgetKind(), options.budgetValue(),
                options.parallelism(),
                System.getProperty("java.version"), System.getProperty("os.name"));
        Files.writeString(output.resolve("experiment-metadata.properties"), text,
                StandardCharsets.UTF_8);
    }
}
