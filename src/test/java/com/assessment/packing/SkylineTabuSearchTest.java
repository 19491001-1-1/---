package com.assessment.packing;

import com.assessment.packing.io.InstanceReader;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SkylineSolver;
import com.assessment.packing.solver.TabuSearchSolver;
import com.assessment.packing.solver.TabuMode;
import com.assessment.packing.solver.TabuSearchConfig;
import com.assessment.packing.validation.SolutionValidator;

import java.nio.file.Path;
import java.util.List;

public final class SkylineTabuSearchTest {
    public static void main(String[] args) throws Exception {
        allModesReturnValidSolutionNoWorseThanInitial();
        fixedSeedIsReproducible();
        strongAttributeWaitsForExpiryAndContinues();
        handlesSingleItem();
        System.out.println("SkylineTabuSearchTest: 全部通过");
    }

    private static void allModesReturnValidSolutionNoWorseThanInitial() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut3.txt"));
        Solution baseline = new SkylineSolver(true, ItemOrder.AREA_DESC).solve(instance);
        TabuSearchConfig config = new TabuSearchConfig(80, 25, 7, 30, 12345L);

        for (TabuMode mode : TabuMode.values()) {
            TabuSearchSolver solver = new TabuSearchSolver(
                    true, ItemOrder.AREA_DESC, mode, config);
            Solution solution = solver.solve(instance);
            check(solution.packedArea() >= baseline.packedArea(), mode + "不应丢失初始最好解");
            check(new SolutionValidator().validate(instance, solution, true).valid(), mode + "结果必须合法");
            check(solver.lastStatistics().evaluatedNeighbors() > 0, mode + "应实际评价邻居");
        }
    }

    private static void fixedSeedIsReproducible() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut2.txt"));
        TabuSearchConfig config = new TabuSearchConfig(40, 15, 5, 20, 777L);
        TabuSearchSolver a = new TabuSearchSolver(
                true, ItemOrder.AREA_DESC, TabuMode.POSITION_ATTRIBUTE, config);
        TabuSearchSolver b = new TabuSearchSolver(
                true, ItemOrder.AREA_DESC, TabuMode.POSITION_ATTRIBUTE, config);
        Solution sa = a.solve(instance);
        Solution sb = b.solve(instance);
        check(sa.packedArea() == sb.packedArea(), "相同随机种子应得到相同面积");
        check(sa.placements().equals(sb.placements()), "相同随机种子应得到相同布局");
        check(a.lastStatistics().equals(b.lastStatistics()), "相同随机种子应得到相同统计");
    }

    private static void handlesSingleItem() {
        Instance instance = new Instance("one", 10, 10, List.of(new Item(1, 5, 5)));
        TabuSearchSolver solver = new TabuSearchSolver(
                false, ItemOrder.INPUT, TabuMode.MOVE,
                new TabuSearchConfig(10, 5, 3, 5, 1L));
        check(solver.solve(instance).packedArea() == 25, "单零件实例应正常返回");
    }

    private static void strongAttributeWaitsForExpiryAndContinues() throws Exception {
        Instance instance = new InstanceReader().read(Path.of("gcut01/gcut5.txt"));
        TabuSearchSolver solver = new TabuSearchSolver(
                true, ItemOrder.AREA_DESC, TabuMode.RELATIVE_ORDER_ATTRIBUTE,
                new TabuSearchConfig(100, 50, 10, 80, 20261012L));
        Solution solution = solver.solve(instance);
        check(solver.lastStatistics().stalledIterations() > 0,
                "强属性封锁全部邻居时应记录停滞");
        check(solver.lastStatistics().completedIterations()
                        > solver.lastStatistics().stalledIterations(),
                "属性到期后搜索应恢复，而不是在首次停滞时结束");
        check(new SolutionValidator().validate(instance, solution, true).valid(),
                "停滞恢复后的结果必须合法");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
