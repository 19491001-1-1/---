package com.assessment.packing.experiment;

import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.solver.SearchLimit;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

final class ExperimentRunConfiguration {
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "dataDirectory", "outputDirectory", "algorithms", "decoder",
            "rotationAllowed", "order", "repetitions", "baseSeed", "profile",
            "maxEvaluations", "maxMilliseconds", "experimentName", "resume",
            "repeatDeterministic", "parallelism");

    private ExperimentRunConfiguration() {}

    static ExperimentRunner.Options read(Path file) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        for (String key : values.stringPropertyNames()) {
            if (!ALLOWED_KEYS.contains(key)) throw new IllegalArgumentException("未知实验配置项：" + key);
        }

        int repetitions = integer(values, "repetitions", 5);
        if (repetitions <= 0) throw new IllegalArgumentException("repetitions必须为正数");
        long baseSeed = longValue(values, "baseSeed", 20261012L);
        long evaluations = longValue(values, "maxEvaluations", Long.MAX_VALUE);
        long milliseconds = longValue(values, "maxMilliseconds", Long.MAX_VALUE);
        if (evaluations != Long.MAX_VALUE && milliseconds != Long.MAX_VALUE) {
            throw new IllegalArgumentException("maxEvaluations与maxMilliseconds不能同时设置");
        }
        SearchLimit limit = evaluations != Long.MAX_VALUE
                ? SearchLimit.evaluations(evaluations)
                : milliseconds != Long.MAX_VALUE
                ? SearchLimit.milliseconds(milliseconds)
                : SearchLimit.unlimited();
        String budgetKind = evaluations != Long.MAX_VALUE ? "EVALUATIONS"
                : milliseconds != Long.MAX_VALUE ? "TIME_MS" : "DEFAULT";
        long budgetValue = evaluations != Long.MAX_VALUE ? evaluations
                : milliseconds != Long.MAX_VALUE ? milliseconds : 0;
        int parallelism = integer(values, "parallelism", 1);
        if (parallelism < 1) throw new IllegalArgumentException("parallelism必须为正数");
        if (parallelism > 1 && milliseconds != Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "parallelism>1不能与maxMilliseconds同时设置：并行会让耗时不再公平");
        }

        return new ExperimentRunner.Options(
                Path.of(values.getProperty("dataDirectory", "gcut01")),
                Path.of(values.getProperty("outputDirectory", "results/configured")),
                algorithms(values.getProperty("algorithms", "PRIMARY")),
                bool(values, "rotationAllowed", true),
                ItemOrder.parse(values.getProperty("order", "AREA_DESC")),
                seeds(repetitions, baseSeed),
                ExperimentProfile.parse(values.getProperty("profile", "STANDARD")),
                limit,
                values.getProperty("experimentName", "结构化配置实验"),
                budgetKind,
                budgetValue,
                bool(values, "repeatDeterministic", false),
                PackingDecoderType.parse(values.getProperty("decoder", "SKYLINE")),
                bool(values, "resume", true),
                parallelism);
    }

    private static List<AlgorithmId> algorithms(String value) {
        return switch (value.trim().toUpperCase()) {
            case "PRIMARY" -> SolverFactory.primaryAlgorithms();
            case "OPTIMIZERS" -> AlgorithmCatalog.optimizerAlgorithms();
            case "ENGINES", "PACKING_ENGINES" -> AlgorithmCatalog.packingEngineAlgorithms();
            case "ALL" -> SolverFactory.allAlgorithms();
            default -> Arrays.stream(value.split(","))
                    .map(String::trim).map(AlgorithmId::parse).toList();
        };
    }

    private static List<Long> seeds(int repetitions, long baseSeed) {
        List<Long> result = new ArrayList<>(repetitions);
        for (int index = 0; index < repetitions; index++) {
            result.add(baseSeed + index * 104_729L);
        }
        return List.copyOf(result);
    }

    private static boolean bool(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        if (value == null) return fallback;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key + "必须是true或false：" + value);
    }

    private static int integer(Properties values, String key, int fallback) {
        return values.containsKey(key) ? Integer.parseInt(values.getProperty(key)) : fallback;
    }

    private static long longValue(Properties values, String key, long fallback) {
        return values.containsKey(key) ? Long.parseLong(values.getProperty(key)) : fallback;
    }
}
