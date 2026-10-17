package com.assessment.packing.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SkylineWmAblationTest {
    @TempDir Path root;

    @Test
    void exportsMatchedPairsForBothSelectionModesAndBothScores() throws Exception {
        Path data = Files.createDirectory(root.resolve("data"));
        Files.copy(Path.of("gcut01/gcut1.txt"), data.resolve("gcut1.txt"));
        SkylineWmAblation.run(data, root.resolve("output"));
        var rows = Files.readAllLines(root.resolve("output/skyline-wm-ablation.csv"));
        assertEquals(5, rows.size());
        var pairs = new java.util.HashSet<String>();
        for (String row : rows.subList(1, rows.size())) {
            String[] fields = row.split(",");
            assertTrue(pairs.add(fields[1] + "/" + fields[2]));
            assertEquals("true", fields[8]);
            assertEquals("true", fields[9]);
            assertEquals(Double.parseDouble(fields[6]) - Double.parseDouble(fields[5]),
                    Double.parseDouble(fields[7]), 1e-7);
        }
        assertTrue(pairs.stream().anyMatch(value -> value.startsWith("SEQUENTIAL/")));
        assertTrue(pairs.stream().anyMatch(value -> value.startsWith("GLOBAL/")));
    }
}
