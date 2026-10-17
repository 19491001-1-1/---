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

    /** 稳定入口只是配置标识；家族才是产品目录的组织单位。 */
    public record Configuration(String id, String displayName, String settings,
                                boolean recommended, boolean compatibilityOnly, AlgorithmGuides.Guide guide) {}

    public record Family(String name, List<Configuration> configurations) {
        public Family { configurations = List.copyOf(configurations); }
    }

    public static boolean compatibilityOnly(AlgorithmId id) {
        return switch (id) {
            case SHELF_FF_WM, SHELF_BWF_WM, SHELF_BHF_WM, SHELF_BAF_WM, SHELF_WWF_WM -> true;
            default -> false;
        };
    }

    public static List<Family> families() {
        java.util.Map<String, List<Configuration>> groups = new java.util.LinkedHashMap<>();
        for (Entry entry : entries()) {
            groups.computeIfAbsent(entry.family(), ignored -> new ArrayList<>()).add(
                    new Configuration(entry.id().name(), entry.id().displayName(), settings(entry.id()),
                            entry.exposure() == Exposure.PRIMARY, compatibilityOnly(entry.id()), AlgorithmGuides.guide(entry.id())));
        }
        return groups.entrySet().stream().map(entry -> new Family(entry.getKey(), entry.getValue())).toList();
    }

    public static String settings(AlgorithmId id) {
        String name = id.name();
        return switch (family(id)) {
            case "Shelf" -> "取件=" + (name.endsWith("_GLOBAL") ? "GLOBAL" : "SEQUENTIAL")
                    + "；选层=" + (id == AlgorithmId.SHELF ? "NF" : name.split("_")[1])
                    + "；WM=" + name.endsWith("_WM");
            case "Skyline" -> id == AlgorithmId.SKYLINE ? "堆优化天际线构造"
                    : "取件=" + (name.endsWith("_WM") ? "SEQUENTIAL" : "GLOBAL")
                    + "；评分=" + name.split("_")[1] + "；WM=" + name.endsWith("_WM");
            case "Tabu Search" -> "禁忌记忆=" + name.substring("TABU_".length());
            case "MaxRects" -> id == AlgorithmId.MAXRECTS_BEST
                    ? "组合策略：独立运行五种评分后择优；评价成本为五次解码"
                    : "放置评分=" + name.substring("MAXRECTS_".length());
            case "Genetic Algorithm" -> id == AlgorithmId.GA ? "固定交叉与变异概率" : "自适应交叉与变异概率";
            case "Memetic GA" -> "局部强化=" + (id == AlgorithmId.GA_SA ? "模拟退火" : "禁忌搜索");
            default -> id.displayName();
        };
    }

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
            case SHELF_NF_WM -> "Shelf 的 NF 选层与 WM 回收配置；层尾回看与层内空洞回收是不同机制";
            case SHELF_FF_WM, SHELF_BWF_WM, SHELF_BHF_WM, SHELF_BAF_WM, SHELF_WWF_WM ->
                    "兼容配置：当前十二组面积降序实验未观察到相对基础规则的利用率收益；保留复现与消融，不单列为独立算法";
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
