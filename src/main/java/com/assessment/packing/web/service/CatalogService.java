package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmCatalog;
import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.AlgorithmDto;
import com.assessment.packing.web.dto.AdvancedAlgorithmDto;
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
                        "以层架为基本单元，按剩余面积选择层",
                        "工作方式：先把板材横向切分成一层层货架。每个矩形到来时，算法尝试已有层，并计算放进去以后该层还会剩下多少面积；剩余面积最小的位置优先。如果所有层都放不下，就在上方创建新层。\n\n为什么有效：层架结构简单、速度快，面积评分又能同时考虑宽度和高度，比只看剩余宽度更全面。它适合快速生成稳定基线，也便于解释每一步决策。\n\n局限：层高一旦由较高矩形确定，矮矩形上方的空间通常难以利用；不规则碎片也可能被层架边界锁住。", "层架列表"),
                decoder(PackingDecoderType.FLOOR_CEILING, "地板-天花板", "Floor-Ceiling",
                        "在层内从底部和顶部双向填充",
                        "工作方式：每一层不再只从左下方向右摆放，而是同时维护“地板”和“天花板”两条放置边。部分矩形贴着层底向右排列，另一些矩形从层顶反向填入，让同一层内部形成上下互补。\n\n为什么有效：当矩形高度差异较大时，单向层架容易在矮件上方留下整条空白；双向填充可以让较小矩形利用这些区域。\n\n局限：上下两组矩形必须避免碰撞，空间管理比普通 Shelf 更复杂；若尺寸缺乏互补性，双向结构未必带来明显收益。", "双向层架"),
                decoder(PackingDecoderType.BOTTOM_LEFT, "左下角", "Bottom-Left",
                        "枚举离散候选点并优先放置在左下方",
                        "工作方式：算法收集板材边界和已放矩形边缘形成的候选点，逐一检查当前矩形能否放置。所有合法位置中，先选纵坐标最低的位置，再选横坐标最小的位置，相当于不断把矩形推向左下角。\n\n为什么有效：左下方被优先填满后，上方和右侧通常保留较连续的剩余空间；规则确定、结果可复现，也不要求把空间切成固定层。\n\n局限：当前最靠左下的位置可能阻挡后续大件；候选点数量会随已放矩形增加，计算量高于简单层架法。", "候选点集"),
                decoder(PackingDecoderType.CONTINUOUS_BOTTOM_LEFT, "连续左下角", "Continuous Bottom-Left",
                        "通过连续移动将矩形推向左下边界",
                        "工作方式：矩形先出现在一个可行位置，然后像受重力和向左推力影响一样持续移动，直到再向下或向左都会碰到边界或其他矩形。算法依据已放置矩形的轮廓寻找这种稳定位置。\n\n为什么有效：它不局限于预先列出的少量离散点，可以更自然地贴住复杂边界，在某些布局中找到普通 Bottom-Left 漏掉的位置。\n\n局限：连续移动和碰撞判断更复杂；强烈偏向左下方仍属于局部贪心，无法保证最终全局最优。", "已放置矩形集"),
                decoder(PackingDecoderType.SKYLINE, "天际线", "Skyline",
                        "使用堆优化的天际线快速构造布局",
                        "工作方式：把所有已放矩形的上边缘压缩成一条由水平线段组成的“城市天际线”。新矩形只需尝试放在这些线段上，放置后再合并相邻且高度相同的线段。\n\n为什么有效：算法不必保存所有空闲区域，只维护顶部轮廓，因此候选位置少、更新速度快。它适合大规模快速构造，也常作为搜索算法的高吞吐量解码器。\n\n局限：天际线只看最上方轮廓，轮廓下方形成的封闭空洞通常无法再次使用。", "天际线段"),
                decoder(PackingDecoderType.SKYLINE_BL_WM, "天际线 BL-WM", "Skyline BL-WM",
                        "左下评分结合碎片回收区域",
                        "工作方式：主体仍沿天际线寻找最低、最靠左的候选位置，同时维护 Waste Map（废料图）。当天际线更新产生封闭空洞时，算法把空洞记录成可复用区域；后续矩形先尝试回填，再继续堆叠。\n\n为什么有效：普通 Skyline 的主要损失正是无法利用轮廓下方空间，废料图补上了这一缺口，尤其适合大小件混合的数据。\n\n局限：碎片区域需要不断分割、删除和去重，计算与实现复杂度明显增加；若几乎没有小件，回收收益可能有限。", "天际线 + 废料图"),
                decoder(PackingDecoderType.MAXRECTS, "最大空闲矩形", "MaxRects",
                        "综合五种启发式选择最佳布局",
                        "工作方式：算法用一组“最大空闲矩形”描述所有仍能放东西的区域。每次放入矩形后，与其相交的空闲区被切分，并删除被其他空闲区完全包含的冗余区域。本实现综合短边贴合、长边贴合、面积贴合、左下位置和接触周长五种评分。\n\n为什么有效：候选空间描述细致，能适应不规则布局；多种评分互补，可避免单一启发式在特定数据上失灵。\n\n局限：空闲矩形允许互相重叠，列表可能快速增长，切分和去重成本较高。", "重叠空闲矩形集"),
                decoder(PackingDecoderType.GUILLOTINE, "一刀切", "Guillotine",
                        "维护可一刀切分的不相交空闲矩形",
                        "工作方式：每次把矩形放进一个空闲矩形后，只用贯穿剩余区域的一条水平或垂直直线进行切分，得到两个新的空闲矩形。这种切法像工业裁刀从板材一边直切到另一边，因此称为 Guillotine。\n\n为什么有效：所有空闲区互不重叠，结构清晰，并且生成的布局更容易对应真实的一刀切加工流程。选择规则、切割方向和相邻空闲区合并方式可以组合调优。\n\n局限：一刀切约束限制了可形成的几何形状，理论利用率可能低于更自由的 MaxRects。", "不相交空闲矩形集"));
    }

    public List<AdvancedAlgorithmDto> advancedAlgorithms() {
        return AlgorithmCatalog.advancedAlgorithms().stream().map(id -> {
            AlgorithmCatalog.Entry entry = AlgorithmCatalog.entry(id);
            AlgorithmId representative = entry.representative();
            return new AdvancedAlgorithmDto(
                    id.name(), id.displayName(), entry.family(), representative.name(),
                    representative.displayName(), detailedAlgorithmIdea(id), id.stochastic());
        }).toList();
    }

    private String detailedAlgorithmIdea(AlgorithmId id) {
        var guide = com.assessment.packing.experiment.AlgorithmGuides.guide(id);
        return guide.overview() + "\n\n工作方式：" + guide.workflow()
                + "\n\n直观理解：" + guide.example()
                + "\n\n适用场景：" + guide.suitable()
                + "\n\n局限与取舍：" + guide.limitations();
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
        return new AlgorithmDto(id.name(), chinese, english, description,
                searchAlgorithmGuide(id) + "\n\n" + searchAlgorithmDeepDive(id), id.stochastic(),
                List.of("EVALUATIONS", "TIME_MS"));
    }

    private String searchAlgorithmGuide(AlgorithmId id) {
        return switch (id) {
            case TABU_MOVE -> "工作方式：从一个可行排列出发，每轮枚举移动、交换等邻域操作，选择当前最有希望的下一步。刚执行过的操作会进入禁忌表，在若干轮内不能立即反向撤销；若某个禁忌动作能产生历史最好解，则允许通过“特赦规则”破例执行。\n\n为什么有效：普通局部搜索容易在几个相似方案之间来回循环，禁忌记忆会强迫搜索离开熟悉区域。移动属性比记忆完整方案更轻量，适合中等到较大规模。\n\n关键取舍：禁忌期限太短容易回头，太长又会封锁有价值的方向；它仍依赖解码器把排列转换成二维布局。";
            case SA -> "工作方式：模拟金属退火过程。高温阶段即使新方案稍差，也会按一定概率接受，让搜索有机会越过局部最优的“山坡”；温度逐渐降低后，算法越来越偏向只接受改进。\n\n为什么有效：完全贪心一旦陷入局部最优就无法离开，而模拟退火用受控的随机退步换取更大的探索范围。固定随机种子时仍可复现实验。\n\n关键取舍：初始温度、降温速度和每个温度的尝试次数决定探索深度；降温过快会退化成局部搜索，过慢则耗时很长。";
            case ACO -> "工作方式：把构造矩形顺序看成蚂蚁逐步选择路径。每只蚂蚁结合“信息素”和即时启发式评分选择下一个矩形；表现好的完整方案会在对应选择上留下更多信息素，后续蚂蚁更可能沿用。信息素同时会蒸发，避免早期错误永久占主导。\n\n为什么有效：许多个体并行构造不同顺序，既能共享好经验，又保留随机探索。\n\n关键取舍：信息素权重过高会过早收敛，启发式权重过高则接近普通贪心；蚂蚁数量越多，搜索更稳定但评价成本更高。";
            case ILS -> "工作方式：先对当前方案执行局部改进，直到附近再也找不到更优解；随后进行一次幅度较大的扰动，把排列推到新的区域，再次局部优化。算法不断重复“深挖—跳出—再深挖”。\n\n为什么有效：局部搜索擅长快速找到附近好解，扰动则负责跨越局部最优之间的障碍。与维护大种群的算法相比，ILS 结构简单、内存开销小。\n\n关键取舍：扰动太弱会回到原来的谷底，太强则丢失已有好结构；接受规则决定新局部最优是否值得保留。";
            case ADAPTIVE_GA -> "工作方式：维护零件优先级排列种群，通过锦标赛选择父代、OX顺序交叉和排列变异产生子代。交叉概率参考两个父代中较大的装入面积；变异概率参考变异前子代自身的装入面积，两者都沿用父代种群的平均面积和最大面积。\n\n调整规则：参考面积不高于父代平均值时取概率上界，高于平均值时随质量提升线性降低，达到或超过父代最大值时取下界；父代全体同分时取上界。\n\n关键取舍：子代预评分的缓存未命中计入评价预算，变异后排列改变可能需要再次解码；固定预算下可能完成更少代，效果须通过实验验证。";
            case GA_TABU -> "工作方式：遗传算法先在多个候选方案之间做全局探索，通过交叉产生新的结构；随后对优秀个体运行禁忌搜索，利用短期记忆进行精细的局部改进。改进后的个体再回到种群参与下一代繁殖，因此属于模因算法。\n\n为什么有效：GA 擅长发现相距很远的不同区域，Tabu 擅长在一个区域内继续深挖，两者分别承担广度和深度。\n\n关键取舍：局部搜索投入过多会让每代很慢，投入过少又体现不出混合优势；还要防止所有个体被改进得过于相似。";
            default -> throw new IllegalArgumentException("非产品级搜索器：" + id);
        };
    }

    private String searchAlgorithmDeepDive(AlgorithmId id) {
        return switch (id) {
            case TABU_MOVE -> "状态与邻域：一个状态通常由矩形排列和旋转选择组成，解码器把它变成真实布局并返回装入面积。邻域可以由单点移动、两点交换或局部重排产生；每个候选都要重新解码评价。禁忌属性记录的是最近执行的移动，而不是把整个方案存下来，因此内存增长可控。\n\n单轮流程：生成邻居，过滤仍处于禁忌期限内且不满足特赦条件的动作，比较剩余候选的适应度，执行其中最好的一步，再更新全局最好解和禁忌表。即使所有邻居都比当前解差，也通常选择“最不差”的一步继续走，这正是它跳出局部最优的关键。\n\n参数与终止：禁忌期限控制记忆长度，邻域采样量控制每轮视野；评价次数预算限制解码总量，时间预算则直接限制墙钟时间。连续多轮没有刷新最好解时，可视为进入平台区。\n\n搭配与解读：与快速 Skyline 搭配可扩大每秒搜索步数，与 MaxRects 搭配则用较少步数换取更精细评价。结果出现短期下降并不代表失败，应观察历史最好值和收敛曲线是否继续上升。";
            case SA -> "状态与邻域：状态同样是排列与旋转决策，邻域通过交换、移动、反转片段或切换旋转位产生。新旧方案的面积差决定接受概率；改进解始终接受，劣解以 exp(差值/温度) 一类概率接受。\n\n单轮流程：从当前解随机产生邻居，调用解码器评价，依据温度决定接受或拒绝，再按冷却计划降低温度。高温时搜索近似随机漫游，中温时能跨过小障碍，低温时接近贪心爬山。全程另存历史最好解，避免最终状态偶然变差。\n\n参数与终止：初始温度决定早期容忍度，冷却率决定探索持续多久，每温度迭代数决定采样充分程度。温度低于阈值、评价预算耗尽或时间到达都会停止。\n\n搭配与解读：快速解码器允许更慢降温和更多采样；复杂解码器需要压缩迭代数。曲线可能长时间横盘后突然跃升，这是越过能量障碍后的正常表现，不应只看最后一次接受的状态。";
            case ACO -> "状态与信息：每只蚂蚁从空序列开始，逐步选择尚未使用的矩形。信息素矩阵记录某种选择或相邻关系在历史好解中的价值，启发式项则反映当前尺寸、面积或解码增益。选择通常采用带权随机抽样，因此相同参数下仍需要固定种子才能复现。\n\n一代流程：多只蚂蚁独立构造完整方案，解码并评价每个方案；随后所有信息素按比例蒸发，再由本代最好或全局最好方案沿其决策路径追加信息素。必要时设置上下界，防止某条路径垄断全部概率。\n\n参数与终止：α 控制信息素影响，β 控制启发式影响，蒸发率控制记忆新旧，蚂蚁数控制每代覆盖面。评价或时间预算耗尽时停止；长期无改进可触发信息素重启。\n\n搭配与解读：ACO 特别适合研究“哪些矩形应相邻或先放”的规律。若曲线过早变平，通常意味着信息素集中太快；若一直剧烈波动，则可能蒸发过快或启发式引导不足。";
            case ILS -> "状态与局部搜索：算法只维护一个主解和一个历史最好解。局部搜索会反复应用能改善结果的移动、交换或片段操作，直到所有已检查邻居都无法继续提升，此时到达局部最优。\n\n循环流程：对局部最优执行扰动，例如连续交换多个位置或打乱一个片段，得到与原解有一定距离的新起点；再做完整局部搜索。接受准则可以只接收更优局部最优，也可以允许部分变差结果以增加多样性。\n\n参数与终止：局部邻域大小决定“深挖”强度，扰动幅度决定“跳远”距离，接受阈值决定是否保留多样性。评价次数通常主要消耗在局部搜索阶段；预算耗尽或长期无改进时终止。\n\n搭配与解读：ILS 对解码器速度较敏感，因为一次循环会评价很多邻居。曲线往往呈阶梯状：局部搜索快速上升，随后停滞，扰动成功后出现下一次跃升。";
            case ADAPTIVE_GA -> "编码与种群：个体仅编码零件ID优先级排列，适应度是指定解码器返回的装入面积；旋转权限由解码器配置，不包含旋转基因或合法性惩罚项。\n\n一代流程：统计父代种群平均面积和最大面积，复制精英，锦标赛选父代，按较优父代面积计算交叉概率，进行OX交叉或克隆。先评价每个子代，再用子代自身面积计算变异概率，执行交换、插入或区间反转。重复排列会触发额外变异；最终排列重新评价并加入下一代。\n\n预算与历史最好：预评分和最终评分都通过同一个缓存与预算检查，缓存命中不增加真实解码评价数。预评分发现的历史最好排列会独立复制保存，避免变异覆盖；最终返回历史最好排列的完整布局。达到代数、评价次数、时间预算、连续无改进代数或满板条件时停止，也响应取消。\n\n实现边界：平均值和最大值始终取本代父种群，不随子代生成更新。父代全体同分时取概率上界；否则子代达到或超过父代最大值时取下界。变异概率不依赖相似度、改进速度或历史最好值，额外评分的收益需在同等预算下验证。";
            case GA_TABU -> "分工结构：外层 GA 用种群、交叉和变异探索相距很远的方案区域；内层 Tabu 从选中的个体出发，通过移动与短期禁忌记忆进行局部强化。被强化的个体再进入下一代，优秀局部结构因此可以通过遗传传播。\n\n一代流程：评价种群并选择需要局部改进的精英或部分子代，为每个目标分配有限禁忌步数；局部结果替换原个体后，再进行选择和繁殖。系统必须控制局部搜索比例，否则全部预算会被少数个体消耗。\n\n参数与终止：除 GA 的种群、交叉、变异参数外，还包括禁忌期限、局部步数和接受局部改进的个体比例。终止通常由统一评价预算控制，便于与其他算法公平比较。\n\n搭配与解读：这是高预算下追求质量的方案。曲线前期来自种群探索，后期的细小提升多由禁忌强化贡献；若种群多样性快速归零，应减少局部强化比例或提高变异。";
            default -> throw new IllegalArgumentException("非产品级搜索器：" + id);
        };
    }

    private String decoderDeepDive(PackingDecoderType type) {
        return switch (type) {
            case SHELF_BAF -> "输入与状态：输入是搜索器给出的矩形顺序和旋转选择；状态是一组已有层，每层记录起点、高度、已用宽度和剩余区域。矩形只能整体放入某一层，不会跨层。\n\n决策细节：对每个高度可容纳且宽度足够的层计算剩余面积，选择最小者；若允许旋转，会比较两个方向的可行性和评分。新建层时必须保证累计高度不超过板材。\n\n复杂度与适用性：朴素实现每个矩形扫描已有层，速度通常很快且内存小。适合需要大量重复评价的 GA、ACO 或 Tabu，也适合作为教学和基线。结果对输入顺序非常敏感，因此搜索优化器有明确用武之地。";
            case FLOOR_CEILING -> "输入与状态：每层同时记录从底部增长的占用区和从顶部反向增长的占用区，中间保持一条安全间隔。矩形的归属不仅包含层，还包含贴地板或贴天花板的方向。\n\n决策细节：算法分别评估底部放置和顶部放置后的剩余宽度、高度互补与碰撞情况，选择更合适的一侧；必要时新建双向层。旋转可能让高件变宽或宽件变高，从而改变上下互补关系。\n\n复杂度与适用性：比普通 Shelf 多一倍候选和碰撞判断，但仍保持层架级速度。适合高度分布呈大小搭配的数据；若所有矩形高度接近，顶部通道很少产生额外价值。";
            case BOTTOM_LEFT -> "输入与状态：状态保存全部已放矩形和由其右边、上边及板材边界产生的离散候选点。每个候选必须通过边界与两两不重叠检查。\n\n决策细节：对当前矩形的原方向和旋转方向分别枚举候选，删除不可行位置，再按 y 坐标、x 坐标及稳定并列规则排序。放置后更新候选点并清理被覆盖或永远不可用的点。\n\n复杂度与适用性：候选数与已放矩形数共同增长，朴素碰撞检查较贵，但布局自由度高于层架法。适合中小规模或需要可解释确定性布局的实验；结果仍高度依赖输入顺序。";
            case CONTINUOUS_BOTTOM_LEFT -> "输入与状态：不依赖固定网格，状态由已放矩形的精确坐标和边界组成。候选位置可由矩形边缘接触事件产生，算法模拟向下、向左的连续滑动直到受到阻挡。\n\n决策细节：每次移动都要计算最近碰撞距离，矩形可能先下滑、再左移、再次下滑，最终停在局部稳定点。旋转方向分别执行同样过程并比较最终位置。\n\n复杂度与适用性：它能找到离散候选集未显式列出的贴合位置，但几何判断和数值边界处理更复杂。适合研究连续几何放置质量；规模很大或搜索器评价次数极高时，速度可能成为瓶颈。";
            case SKYLINE -> "输入与状态：状态是一列按 x 坐标排序的水平线段，每段包含起点、宽度和高度。线段共同描述当前布局的最高轮廓，而不保存轮廓下方的空洞。\n\n决策细节：尝试把矩形左边缘对齐到各线段起点，计算它跨越范围内的最高高度作为落脚高度；合法候选按最低顶部、最小 x 或指定评分选择。放置后切开受影响线段、插入新高度并合并相邻同高段。\n\n复杂度与适用性：候选数通常远小于自由空间法，配合堆或有序结构可获得很高吞吐量。它特别适合 GA、ACO 等需要成千上万次解码的搜索；主要盲点是无法回填被轮廓封住的洞。";
            case SKYLINE_BL_WM -> "输入与状态：同时维护天际线段和一组互不越界的废料矩形。天际线负责继续向上构造，废料图负责保存轮廓下方、层间或切分后仍可利用的局部区域。\n\n决策细节：每个矩形先在废料区尝试最合适的位置；失败后再按最低、最左规则放到天际线上。无论放在哪，相关废料区都要切分和去重，天际线也要同步更新。\n\n复杂度与适用性：比纯 Skyline 慢，但通常仍比完整 MaxRects 轻量，是速度与碎片利用之间的折中。小件丰富、尺寸跨度大时收益明显；废料区长期无法匹配时会增加无效检查。";
            case MAXRECTS -> "输入与状态：状态是所有最大空闲矩形的集合。它们可以互相重叠，但每一个都表示当前布局中一个不可再向外扩张的空白区域；真实可用空间由这些区域共同覆盖。\n\n决策细节：对每个空闲区、每个允许方向计算五类评分，选择候选放置；随后将所有与新矩形相交的空闲区按四个方向切分，删除零面积区域和被其他区域完全包含的冗余项。BEST 模式可比较多种启发式产生的完整结果。\n\n复杂度与适用性：候选和切分数可能呈平方增长，质量通常较高但评价昂贵。适合单次高质量构造、较小搜索预算或作为强解码器验证；与大种群搜索搭配时应控制评价次数和缓存。";
            case GUILLOTINE -> "输入与状态：状态是一组互不重叠的空闲矩形，每次放置都必须通过一条贯穿剩余区域的直线完成切分。矩形选择规则决定放进哪个空闲区，切割规则决定先水平还是先垂直，合并规则决定相邻空闲区何时重新组合。\n\n决策细节：选定位置后通常产生右侧与上方两个余料区；算法根据短轴、长轴、面积或固定方向决定切法，并可合并边长完全对齐的相邻区域。旋转会同时影响适配评分和余料形状。\n\n复杂度与适用性：空闲区互不重叠使结构和验证相对简单，也能直接对应玻璃、木板、金属板的一刀切加工约束。若生产并不要求一刀切，它的几何自由度限制可能牺牲利用率。";
        };
    }

    private DecoderDto decoder(
            PackingDecoderType type, String chinese, String english, String description,
            String guide, String structure) {
        return new DecoderDto(type.name(), chinese, english, description,
                guide + "\n\n" + decoderDeepDive(type), true, structure);
    }
}
