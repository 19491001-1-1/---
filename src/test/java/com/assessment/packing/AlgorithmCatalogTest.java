package com.assessment.packing;

import com.assessment.packing.experiment.AlgorithmCatalog;
import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Item;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.SearchLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlgorithmCatalogTest {
    @Test
    void everyConfigurationIncludesACompleteBeginnerGuide() {
        for (var family : AlgorithmCatalog.families()) {
            for (var configuration : family.configurations()) {
                var guide = configuration.guide();
                for (String section : List.of(guide.overview(), guide.workflow(), guide.example(), guide.suitable(), guide.limitations())) {
                    assertTrue(section.length() >= 30, configuration.id() + "的介绍章节缺失或过于简略");
                }
            }
        }
    }

    @Test
    void familiesDescribeEveryConfigurationAndIsolateCompatibilityOptions() {
        var configurations = AlgorithmCatalog.families().stream()
                .flatMap(family -> family.configurations().stream()).toList();
        assertEquals(46, configurations.size());
        assertEquals(46, configurations.stream().map(AlgorithmCatalog.Configuration::id).distinct().count());
        assertEquals(5, configurations.stream().filter(AlgorithmCatalog.Configuration::compatibilityOnly).count());
        assertTrue(configurations.stream().noneMatch(item -> item.compatibilityOnly() && item.recommended()));
        assertTrue(AlgorithmCatalog.settings(AlgorithmId.SKYLINE_BL).contains("GLOBAL"));
        assertTrue(AlgorithmCatalog.settings(AlgorithmId.SKYLINE_BL_WM).contains("SEQUENTIAL"));
        assertTrue(AlgorithmCatalog.settings(AlgorithmId.MAXRECTS_BEST).contains("五次解码"));
    }

    @Test
    void catalogKeepsAllImplementedEntriesTraceable() {
        assertEquals(46, AlgorithmId.values().length, "已实现入口总数应继续为46");
        assertEquals(46, AlgorithmCatalog.entries().size(), "能力目录必须覆盖全部46个入口");
        assertEquals(14, AlgorithmCatalog.primaryAlgorithms().size(), "默认工程主入口应为14个");
        assertEquals(6, AlgorithmCatalog.optimizerAlgorithms().size(), "核心搜索优化入口应为6个");
        assertEquals(8, AlgorithmCatalog.packingEngineAlgorithms().size(), "排样引擎入口应为8个");
        assertEquals(32, AlgorithmCatalog.advancedAlgorithms().size(), "高级/历史策略应为32个");
        assertEquals(AlgorithmCatalog.optimizerAlgorithms(),
                AlgorithmCatalog.primaryAlgorithms().subList(0, 6),
                "默认展示必须优先排列搜索优化算法");
        assertTrue(AlgorithmCatalog.optimizerAlgorithms().stream()
                        .allMatch(id -> AlgorithmCatalog.entry(id).role()
                                == AlgorithmCatalog.Role.OPTIMIZER
                                && AlgorithmCatalog.entry(id).groupWeightPercent() == 60),
                "搜索优化层权重应为60%");
        assertTrue(AlgorithmCatalog.packingEngineAlgorithms().stream()
                        .allMatch(id -> AlgorithmCatalog.entry(id).role()
                                == AlgorithmCatalog.Role.PACKING_ENGINE
                                && AlgorithmCatalog.entry(id).groupWeightPercent() == 40),
                "排样引擎层权重应为40%");

        EnumSet<AlgorithmId> union = EnumSet.noneOf(AlgorithmId.class);
        union.addAll(AlgorithmCatalog.primaryAlgorithms());
        union.addAll(AlgorithmCatalog.advancedAlgorithms());
        assertEquals(EnumSet.allOf(AlgorithmId.class), union, "分层不得丢失任何已实现入口");
        assertTrue(AlgorithmCatalog.primaryAlgorithms().stream()
                        .noneMatch(AlgorithmCatalog.advancedAlgorithms()::contains),
                "主入口与高级入口不得重叠");
        assertEquals(46, SolverFactory.allAlgorithms().size(),
                "完整实验入口仍应包含46个算法");
        assertEquals(AlgorithmCatalog.primaryAlgorithms(), SolverFactory.primaryAlgorithms(),
                "默认工程列表应由能力目录统一提供");
    }

    @ParameterizedTest
    @EnumSource(value = AlgorithmId.class, names = {"SHELF", "MAXRECTS_BSSF", "TABU_FULL", "GA_SA"})
    void hiddenEntriesRemainCallable(AlgorithmId id) {
        assertSame(AlgorithmCatalog.Exposure.ADVANCED, AlgorithmCatalog.entry(id).exposure(),
                id + "应被标记为高级/历史策略");
        Instance instance = new Instance("catalog", 4, 4, List.of(new Item(1, 4, 4)));
        long packedArea = SolverFactory.create(
                        id, false, ItemOrder.INPUT, 1L,
                        ExperimentProfile.QUICK, SearchLimit.evaluations(50))
                .solve(instance)
                .packedArea();
        assertEquals(16L, packedArea, id + "隐藏后仍必须可调用");
    }
}
