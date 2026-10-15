package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmCatalog;
import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.AlgorithmDto;
import com.assessment.packing.web.dto.DecoderDto;
import com.assessment.packing.web.dto.MetaDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class CatalogService {
    public MetaDto meta() {
        String version = CatalogService.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) version = "development";
        int searchCount = AlgorithmCatalog.optimizerAlgorithms().size();
        int decoderCount = PackingDecoderType.values().length;
        return new MetaDto(
                "二维矩形排样实验平台",
                version,
                System.getProperty("java.version"),
                searchCount,
                decoderCount,
                searchCount * decoderCount,
                AlgorithmId.values().length,
                60,
                40,
                List.of("搜索器与解码器自由组合", "固定种子可复现", "评价次数或时间预算", "独立合法性验证"));
    }

    public List<AlgorithmDto> searchAlgorithms() {
        return AlgorithmCatalog.optimizerAlgorithms().stream().map(this::searchAlgorithm).toList();
    }

    public List<DecoderDto> decoders() {
        return List.of(
                decoder(PackingDecoderType.SHELF_BAF, "层架最佳面积匹配", "Shelf BAF",
                        "以层架为基本单元，按剩余面积选择层", "层架列表"),
                decoder(PackingDecoderType.FLOOR_CEILING, "地板-天花板", "Floor-Ceiling",
                        "在层内从底部和顶部双向填充", "双向层架"),
                decoder(PackingDecoderType.BOTTOM_LEFT, "左下角", "Bottom-Left",
                        "枚举离散候选点并优先放置在左下方", "候选点集"),
                decoder(PackingDecoderType.CONTINUOUS_BOTTOM_LEFT, "连续左下角", "Continuous Bottom-Left",
                        "通过连续移动将矩形推向左下边界", "已放置矩形集"),
                decoder(PackingDecoderType.SKYLINE, "天际线", "Skyline",
                        "使用堆优化的天际线快速构造布局", "天际线段"),
                decoder(PackingDecoderType.SKYLINE_BL_WM, "天际线 BL-WM", "Skyline BL-WM",
                        "左下评分结合碎片回收区域", "天际线 + 废料图"),
                decoder(PackingDecoderType.MAXRECTS, "最大空闲矩形", "MaxRects",
                        "综合五种启发式选择最佳布局", "重叠空闲矩形集"),
                decoder(PackingDecoderType.GUILLOTINE, "一刀切", "Guillotine",
                        "维护可一刀切分的不相交空闲矩形", "不相交空闲矩形集"));
    }

    private AlgorithmDto searchAlgorithm(AlgorithmId id) {
        return switch (id) {
            case TABU_MOVE -> algorithm(id, "禁忌搜索", "Tabu Search", "使用禁忌属性避免短期循环并扩展局部搜索");
            case SA -> algorithm(id, "模拟退火", "Simulated Annealing", "以随温度衰减的概率接受劣解");
            case ACO -> algorithm(id, "蚁群优化", "Ant Colony Optimization", "通过信息素与启发式因子构造物品顺序");
            case ILS -> algorithm(id, "迭代局部搜索", "Iterated Local Search", "在局部优化与扰动之间反复切换");
            case ADAPTIVE_GA -> algorithm(id, "自适应遗传算法", "Adaptive Genetic Algorithm", "根据种群适应度动态调整交叉和变异率");
            case GA_TABU -> algorithm(id, "遗传算法 + 禁忌", "Genetic Algorithm + Tabu", "将种群搜索与禁忌局部改进结合");
            default -> throw new IllegalArgumentException("非产品级搜索器：" + id);
        };
    }

    private AlgorithmDto algorithm(AlgorithmId id, String chinese, String english, String description) {
        return new AlgorithmDto(id.name(), chinese, english, description, id.stochastic(),
                List.of("EVALUATIONS", "TIME_MS"));
    }

    private DecoderDto decoder(
            PackingDecoderType type, String chinese, String english, String description, String structure) {
        return new DecoderDto(type.name(), chinese, english, description, true, structure);
    }
}
