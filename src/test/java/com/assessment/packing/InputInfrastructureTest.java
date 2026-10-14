package com.assessment.packing;

import com.assessment.packing.io.InstanceFiles;
import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.CommandLineSolverFactory;
import com.assessment.packing.solver.ItemOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class InputInfrastructureTest {
    public static void main(String[] args) throws Exception {
        sortsLargeNumericNamesWithoutOverflow();
        validatesBooleanArguments();
        createsEveryCommandLineAlgorithm();
        System.out.println("InputInfrastructureTest: 全部通过");
    }

    private static void sortsLargeNumericNamesWithoutOverflow() throws Exception {
        Path directory = Files.createTempDirectory("packing-instance-files-");
        Files.writeString(directory.resolve("gcut10.txt"), "");
        Files.writeString(directory.resolve("gcut2.txt"), "");
        Files.writeString(directory.resolve("gcut02.txt"), "");
        Files.writeString(directory.resolve("gcut999999999999999999999999.txt"), "");
        Files.writeString(directory.resolve("notes.txt"), "");

        List<String> names = InstanceFiles.listGcutFiles(directory).stream()
                .map(path -> path.getFileName().toString())
                .toList();
        check(names.equals(List.of(
                "gcut02.txt", "gcut2.txt", "gcut10.txt", "gcut999999999999999999999999.txt")),
                "gcut文件必须按任意长度数字自然排序：" + names);
    }

    private static void validatesBooleanArguments() {
        check(Main.parseBoolean("TRUE", "测试参数"), "TRUE应解析为true");
        check(!Main.parseBoolean("false", "测试参数"), "false应解析为false");
        try {
            Main.parseBoolean("yes", "测试参数");
            throw new AssertionError("拼写错误的布尔参数不应被静默解析为false");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void createsEveryCommandLineAlgorithm() {
        for (AlgorithmId id : AlgorithmId.values()) {
            var solver = CommandLineSolverFactory.create(
                    id.name(), true, ItemOrder.AREA_DESC, new String[0]);
            check(solver != null, "命令行工厂未创建：" + id);
        }
        for (String alias : List.of(
                "NF", "FC", "BL", "CBL", "SKY", "GUIL", "BEST",
                "TS_FULL", "TS_MOVE", "TS_ATTRIBUTE", "SA", "ACO", "ILS",
                "GENETIC", "AGA", "MEMETIC_SA", "MEMETIC_TABU")) {
            check(CommandLineSolverFactory.create(
                    alias, true, ItemOrder.AREA_DESC, new String[0]) != null,
                    "命令行别名失效：" + alias);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
