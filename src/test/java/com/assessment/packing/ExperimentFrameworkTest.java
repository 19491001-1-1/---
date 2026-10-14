package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.ExperimentRunner;
import com.assessment.packing.experiment.RunObservation;
import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ExperimentFrameworkTest {
    public static void main(String[] args) throws Exception {
        generatesValidatedCsvSvgHtmlAndStatistics();
        enforcesEvaluationBudgetAndKeepsMonotoneTrace();
        System.out.println("ExperimentFrameworkTest: 全部通过");
    }

    private static void generatesValidatedCsvSvgHtmlAndStatistics() throws Exception {
        Path root = Files.createTempDirectory("packing-experiment-test-");
        Path data = root.resolve("data");
        Path output = root.resolve("result");
        Files.createDirectories(data);
        Files.copy(Path.of("gcut01/gcut1.txt"), data.resolve("gcut1.txt"));
        ExperimentRunner.Options options = new ExperimentRunner.Options(
                data,
                output,
                List.of(
                        AlgorithmId.SHELF,
                        AlgorithmId.SKYLINE_BL_WM,
                        AlgorithmId.GUILLOTINE,
                        AlgorithmId.MAXRECTS_BSSF,
                        AlgorithmId.MAXRECTS_BEST,
                        AlgorithmId.SA,
                        AlgorithmId.ACO,
                        AlgorithmId.GA),
                true,
                ItemOrder.AREA_DESC,
                List.of(11L, 22L),
                ExperimentProfile.QUICK,
                SearchLimit.evaluations(120),
                "测试实验",
                "EVALUATIONS",
                120,
                false);

        List<RunObservation> runs = new ExperimentRunner().run(options);
        check(runs.size() == 11, "五个确定性算法应各运行一次，三个随机算法应各运行两个种子");
        check(runs.stream().allMatch(RunObservation::valid), "所有实验结果必须通过独立验证");
        check(Files.exists(output.resolve("runs.csv")), "必须生成逐次CSV");
        check(Files.exists(output.resolve("summary.csv")), "必须生成汇总CSV");
        check(Files.exists(output.resolve("overall.csv")), "必须生成总览CSV");
        check(Files.exists(output.resolve("traces.csv")), "必须生成收敛CSV");
        check(Files.exists(output.resolve("algorithm-catalog.csv")), "必须生成完整算法能力目录");
        check(Files.exists(output.resolve("report.html")), "必须生成HTML报告");
        check(Files.exists(output.resolve("layouts/gcut1/sa.svg")), "必须生成排样SVG");
        check(Files.exists(output.resolve("layouts/gcut1/aco.svg")), "必须生成ACO排样SVG");
        check(Files.exists(output.resolve("layouts/gcut1/maxrects_bssf.svg")),
                "统一报告必须生成MaxRects排样SVG");
        check(Files.exists(output.resolve("layouts/gcut1/maxrects_best.svg")),
                "统一报告必须生成MaxRects-BEST排样SVG");
        check(Files.exists(output.resolve("layouts/gcut1/skyline_bl_wm.svg")),
                "统一报告必须生成Skyline-BL-WM排样SVG");
        check(Files.exists(output.resolve("layouts/gcut1/guillotine.svg")),
                "统一报告必须生成Guillotine排样SVG");
        check(Files.exists(output.resolve("convergence/gcut1.svg")), "必须生成收敛曲线SVG");
        check(Files.readString(output.resolve("report.html")).contains("测试实验"),
                "报告应包含实验标题");
        check(Files.readString(output.resolve("report.html")).contains("算法能力结构"),
                "报告应展示主入口与高级/历史策略留痕");
        check(Files.readString(output.resolve("algorithm-catalog.csv"))
                        .contains("SHELF,Shelf-NF,ADVANCED"),
                "能力目录应记录被隐藏的原始Shelf入口");
        check(Files.readString(output.resolve("runs.csv"))
                        .contains("maxrects_selected_heuristic"),
                "逐次CSV必须包含MaxRects-BEST胜者统计列");
        check(Files.readString(output.resolve("report.html"))
                        .contains("MaxRects-BEST 组成明细"),
                "HTML报告必须展示MaxRects-BEST五种成员的组成明细");
        check(Files.readString(output.resolve("report.html"))
                        .contains("相对独立BSSF的实测平均耗时因子"),
                "BEST耗时必须与独立运行的BSSF按同一实验口径比较");
        check(Files.readString(output.resolve("layouts/gcut1/sa.svg")).contains("<rect"),
                "排样SVG应包含零件矩形");
    }

    private static void enforcesEvaluationBudgetAndKeepsMonotoneTrace() throws Exception {
        Path root = Files.createTempDirectory("packing-budget-test-");
        Path data = root.resolve("data");
        Files.createDirectories(data);
        Files.copy(Path.of("gcut01/gcut3.txt"), data.resolve("gcut3.txt"));
        List<AlgorithmId> algorithms = List.of(
                AlgorithmId.TABU_MOVE, AlgorithmId.SA, AlgorithmId.ACO, AlgorithmId.GA,
                AlgorithmId.ILS, AlgorithmId.ADAPTIVE_GA,
                AlgorithmId.GA_SA, AlgorithmId.GA_TABU);
        List<RunObservation> runs = new ExperimentRunner().run(new ExperimentRunner.Options(
                data, root.resolve("result"), algorithms, true, ItemOrder.AREA_DESC,
                List.of(77L), ExperimentProfile.QUICK, SearchLimit.evaluations(100),
                "预算测试", "EVALUATIONS", 100, false));

        for (RunObservation run : runs) {
            check(run.evaluations() <= 100,
                    run.algorithm() + "不得超过候选解评价预算，实际=" + run.evaluations());
            long previous = -1;
            for (ConvergencePoint point : run.trace()) {
                check(point.bestPackedArea() >= previous, run.algorithm() + "历史最好轨迹必须单调");
                check(point.evaluations() <= 100, run.algorithm() + "轨迹评价次数不得越过预算");
                previous = point.bestPackedArea();
            }
            check(previous == run.solution().packedArea(), run.algorithm() + "轨迹终点必须对应最终解");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
