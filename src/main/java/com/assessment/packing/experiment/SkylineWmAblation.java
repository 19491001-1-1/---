package com.assessment.packing.experiment;

import com.assessment.packing.io.InstanceFiles;
import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SkylineGlobalSolver;
import com.assessment.packing.solver.SkylinePlacementHeuristic;
import com.assessment.packing.solver.SkylineSelectionMode;
import com.assessment.packing.validation.SolutionValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 固定评分与取件模式，仅切换 WM，避免把取件变化误算为碎片回收收益。 */
public final class SkylineWmAblation {
    private SkylineWmAblation() {}

    public static void run(Path data, Path output) throws IOException {
        StringBuilder csv = new StringBuilder("instance,selection,heuristic,rotation,order,without_wm_percent,with_wm_percent,delta_percentage_points,without_valid,with_valid\n");
        int pairs = 0;
        int invalid = 0;
        for (Path file : InstanceFiles.listGcutFiles(data)) {
            Instance instance = new InstanceReader().read(file);
            for (SkylineSelectionMode selection : SkylineSelectionMode.values()) {
                for (SkylinePlacementHeuristic heuristic : SkylinePlacementHeuristic.values()) {
                    Solution without = new SkylineGlobalSolver(true, ItemOrder.AREA_DESC, heuristic, false, selection).solve(instance);
                    Solution with = new SkylineGlobalSolver(true, ItemOrder.AREA_DESC, heuristic, true, selection).solve(instance);
                    boolean withoutValid = new SolutionValidator().validate(instance, without, true).valid();
                    boolean withValid = new SolutionValidator().validate(instance, with, true).valid();
                    if (!withoutValid || !withValid) invalid++;
                    double before = without.utilization(instance) * 100;
                    double after = with.utilization(instance) * 100;
                    csv.append(String.format(Locale.ROOT, "%s,%s,%s,true,AREA_DESC,%.8f,%.8f,%.8f,%s,%s%n",
                            CsvEscaper.escape(instance.name()), selection, heuristic, before, after, after - before,
                            withoutValid, withValid));
                    pairs++;
                }
            }
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve("skyline-wm-ablation.csv"), csv, StandardCharsets.UTF_8);
        System.out.printf("Skyline WM 单变量对照：%d 对，非法对照=%d，输出=%s%n", pairs, invalid, output.toAbsolutePath());
        if (invalid > 0) throw new IllegalStateException("Skyline WM 对照出现非法布局，请检查 CSV");
    }
}
