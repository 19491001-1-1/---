package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

public record PackingRunConfiguration(
        Path dataDirectory,
        boolean rotationAllowed,
        ItemOrder order,
        AlgorithmId algorithm,
        PackingDecoderType decoder,
        ExperimentProfile profile,
        long seed,
        SearchLimit searchLimit) {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "dataDirectory", "rotationAllowed", "order", "algorithm", "decoder",
            "profile", "seed", "maxEvaluations", "maxMilliseconds");

    public static PackingRunConfiguration read(Path file) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        for (String key : values.stringPropertyNames()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("未知配置项：" + key);
            }
        }

        long maxEvaluations = longValue(values, "maxEvaluations", Long.MAX_VALUE);
        long maxMilliseconds = longValue(values, "maxMilliseconds", Long.MAX_VALUE);
        if (maxEvaluations != Long.MAX_VALUE && maxMilliseconds != Long.MAX_VALUE) {
            throw new IllegalArgumentException("maxEvaluations 与 maxMilliseconds 不能同时设置");
        }
        SearchLimit limit = maxEvaluations != Long.MAX_VALUE
                ? SearchLimit.evaluations(maxEvaluations)
                : maxMilliseconds != Long.MAX_VALUE
                ? SearchLimit.milliseconds(maxMilliseconds)
                : SearchLimit.unlimited();

        return new PackingRunConfiguration(
                Path.of(values.getProperty("dataDirectory", "gcut01")),
                booleanValue(values, "rotationAllowed", true),
                ItemOrder.parse(values.getProperty("order", "AREA_DESC")),
                AlgorithmId.parse(values.getProperty("algorithm", "ADAPTIVE_GA")),
                PackingDecoderType.parse(values.getProperty("decoder", "SKYLINE")),
                ExperimentProfile.parse(values.getProperty("profile", "QUICK")),
                longValue(values, "seed", 20261012L),
                limit);
    }

    private static boolean booleanValue(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        if (value == null) return fallback;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key + " 必须是 true 或 false：" + value);
    }

    private static long longValue(Properties values, String key, long fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : Long.parseLong(value);
    }
}
