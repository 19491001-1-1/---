package com.assessment.packing.experiment;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 算法能力目录：将默认工程入口与已实现的研究/历史变体分层。
 *
 * <p>高级入口仍保留完整求解器、命令行调用、专项实验和测试；
 * 它们只是不再出现于默认 benchmark/all 主展示中。
 */
public final class AlgorithmCatalog {
    public enum Exposure {
        PRIMARY,
        ADVANCED
    }

    public enum Role {
        OPTIMIZER,
        PACKING_ENGINE,
        VARIANT
    }

    public record Entry(
            AlgorithmId id,
            Exposure exposure,
            Role role,
            int groupWeightPercent,
            String family,
            AlgorithmId representative,
            String rationale) {}

    private static final List<AlgorithmId> OPTIMIZERS = List.of(
            AlgorithmId.TABU_MOVE,
            AlgorithmId.SA,
            AlgorithmId.ACO,
            AlgorithmId.ILS,
            AlgorithmId.ADAPTIVE_GA,
            AlgorithmId.GA_TABU);

    private static final List<AlgorithmId> PACKING_ENGINES = List.of(
            AlgorithmId.SHELF_BAF_GLOBAL,
            AlgorithmId.SHELF_FC,
            AlgorithmId.BOTTOM_LEFT,
            AlgorithmId.CONTINUOUS_BOTTOM_LEFT,
            AlgorithmId.SKYLINE,
            AlgorithmId.SKYLINE_BL_WM,
            AlgorithmId.GUILLOTINE,
            AlgorithmId.MAXRECTS_BEST);

    private static final List<AlgorithmId> PRIMARY_ORDER;
    private static final EnumSet<AlgorithmId> PRIMARY;

    static {
        List<AlgorithmId> ordered = new ArrayList<>(OPTIMIZERS);
        ordered.addAll(PACKING_ENGINES);
        PRIMARY_ORDER = List.copyOf(ordered);
        PRIMARY = EnumSet.copyOf(PRIMARY_ORDER);
    }

    private AlgorithmCatalog() {}

    public static List<Entry> entries() {
        return displayAlgorithms().stream()
                .map(AlgorithmCatalog::entry)
                .toList();
    }

    public static List<AlgorithmId> primaryAlgorithms() {
        return PRIMARY_ORDER;
    }

    public static List<AlgorithmId> advancedAlgorithms() {
        return entries().stream()
                .filter(entry -> entry.exposure() == Exposure.ADVANCED)
                .map(Entry::id)
                .toList();
    }

    public static List<AlgorithmId> optimizerAlgorithms() {
        return OPTIMIZERS;
    }

    public static List<AlgorithmId> packingEngineAlgorithms() {
        return PACKING_ENGINES;
    }

    public static List<AlgorithmId> displayAlgorithms() {
        List<AlgorithmId> result = new ArrayList<>(PRIMARY_ORDER);
        for (AlgorithmId id : AlgorithmId.values()) {
            if (!PRIMARY.contains(id)) result.add(id);
        }
        return List.copyOf(result);
    }

    public static int displayOrder(AlgorithmId id) {
        return displayAlgorithms().indexOf(id);
    }

    public static Entry entry(AlgorithmId id) {
        return new Entry(
                id,
                PRIMARY.contains(id) ? Exposure.PRIMARY : Exposure.ADVANCED,
                role(id),
                groupWeight(id),
                family(id),
                representative(id),
                rationale(id));
    }

    private static Role role(AlgorithmId id) {
        if (OPTIMIZERS.contains(id)) return Role.OPTIMIZER;
        if (PACKING_ENGINES.contains(id)) return Role.PACKING_ENGINE;
        return Role.VARIANT;
    }

    private static int groupWeight(AlgorithmId id) {
        return switch (role(id)) {
            case OPTIMIZER -> 60;
            case PACKING_ENGINE -> 40;
            case VARIANT -> 0;
        };
    }

    private static String family(AlgorithmId id) {
        return switch (id) {
            case SHELF, SHELF_FF, SHELF_BWF, SHELF_BHF, SHELF_BAF, SHELF_WWF,
                    SHELF_NF_GLOBAL, SHELF_FF_GLOBAL, SHELF_BWF_GLOBAL,
                    SHELF_BHF_GLOBAL, SHELF_BAF_GLOBAL, SHELF_WWF_GLOBAL,
                    SHELF_NF_WM, SHELF_FF_WM, SHELF_BWF_WM,
                    SHELF_BHF_WM, SHELF_BAF_WM, SHELF_WWF_WM -> "Shelf";
            case SHELF_FC -> "Floor-Ceiling";
            case BOTTOM_LEFT, CONTINUOUS_BOTTOM_LEFT -> "Bottom-Left";
            case SKYLINE, SKYLINE_BL, SKYLINE_BF, SKYLINE_BL_WM, SKYLINE_BF_WM -> "Skyline";
            case GUILLOTINE -> "Guillotine";
            case MAXRECTS_BSSF, MAXRECTS_BLSF, MAXRECTS_BAF,
                    MAXRECTS_BL, MAXRECTS_CP, MAXRECTS_BEST -> "MaxRects";
            case TABU_FULL, TABU_MOVE, TABU_POSITION, TABU_ORDER,
                    TABU_ADJACENCY, TABU_PLACEMENT -> "Tabu Search";
            case SA -> "Simulated Annealing";
            case ACO -> "Ant Colony Optimization";
            case ILS -> "Iterated Local Search";
            case GA, ADAPTIVE_GA -> "Genetic Algorithm";
            case GA_SA, GA_TABU -> "Memetic GA";
        };
    }

    private static AlgorithmId representative(AlgorithmId id) {
        return switch (id) {
            case SHELF, SHELF_FF, SHELF_BWF, SHELF_BHF, SHELF_BAF, SHELF_WWF,
                    SHELF_NF_GLOBAL, SHELF_FF_GLOBAL, SHELF_BWF_GLOBAL,
                    SHELF_BHF_GLOBAL, SHELF_BAF_GLOBAL, SHELF_WWF_GLOBAL ->
                    AlgorithmId.SHELF_BAF_GLOBAL;
            case SHELF_NF_WM, SHELF_FF_WM, SHELF_BWF_WM,
                    SHELF_BHF_WM, SHELF_BAF_WM, SHELF_WWF_WM ->
                    AlgorithmId.SHELF_BAF_GLOBAL;
            case SKYLINE_BL, SKYLINE_BF, SKYLINE_BL_WM, SKYLINE_BF_WM ->
                    AlgorithmId.SKYLINE_BL_WM;
            case MAXRECTS_BSSF, MAXRECTS_BLSF, MAXRECTS_BAF,
                    MAXRECTS_BL, MAXRECTS_CP, MAXRECTS_BEST -> AlgorithmId.MAXRECTS_BEST;
            case TABU_FULL, TABU_MOVE, TABU_POSITION, TABU_ORDER,
                    TABU_ADJACENCY, TABU_PLACEMENT -> AlgorithmId.TABU_MOVE;
            case GA, ADAPTIVE_GA -> AlgorithmId.ADAPTIVE_GA;
            case GA_SA, GA_TABU -> AlgorithmId.GA_TABU;
            default -> id;
        };
    }

    private static String rationale(AlgorithmId id) {
        if (PRIMARY.contains(id)) {
            return role(id) == Role.OPTIMIZER
                    ? "核心优化层：搜索排列、旋转或邻域状态，主展示权重60%"
                    : "排样引擎层：生成可行布局并提供空间管理基线，主展示权重40%";
        }
        return switch (id) {
            case SHELF, SHELF_FF, SHELF_BWF, SHELF_BHF, SHELF_BAF, SHELF_WWF ->
                    "已实现的原始顺序 Shelf 基线；默认展示改用 GLOBAL 代表，仍可独立调用和验证";
            case SHELF_NF_GLOBAL, SHELF_FF_GLOBAL, SHELF_BWF_GLOBAL,
                    SHELF_BHF_GLOBAL, SHELF_WWF_GLOBAL ->
                    "与主入口共享 Shelf-GLOBAL 核心，仅替换选层评分规则";
            case SHELF_NF_WM, SHELF_FF_WM, SHELF_BWF_WM,
                    SHELF_BHF_WM, SHELF_BAF_WM, SHELF_WWF_WM ->
                    "已实现的 Shelf 废物地图变体；作为专项/消融策略保留，默认展示用 Skyline-WM 代表碎片回收机制";
            case SKYLINE_BL, SKYLINE_BF, SKYLINE_BF_WM ->
                    "Skyline-GLOBAL 的评分或 WM 变体；保留专项对比，不占用默认主榜入口";
            case MAXRECTS_BSSF, MAXRECTS_BLSF, MAXRECTS_BAF,
                    MAXRECTS_BL, MAXRECTS_CP ->
                    "MaxRects-BEST 的已实现成员启发式；BEST内部全部运行并在报告中留下成员结果";
            case TABU_FULL, TABU_POSITION, TABU_ORDER, TABU_ADJACENCY, TABU_PLACEMENT ->
                    "与 Tabu-Move 共享搜索框架，仅替换禁忌属性定义；保留研究和消融证据";
            case GA ->
                    "普通 GA 基线；默认工程展示使用同核心的自适应 GA";
            case GA_SA ->
                    "已实现的 GA+SA 模因变体；默认只展示 GA+Tabu 作为混合搜索代表";
            default -> "保留为可调用的高级策略";
        };
    }
}
