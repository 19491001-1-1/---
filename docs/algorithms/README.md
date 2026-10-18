# 算法说明与实现差异

## 1. 排列搜索与几何构造

项目把搜索优化与几何放置分开实现。搜索器维护零件ID优先级排列，解码器据此生成布局并反馈面积。评分、取件方式、禁忌属性和WM属于具体配置，46个入口不能解释为46种独立算法。

`AlgorithmId`标识配置，`SolverFactory`负责创建求解器。阅读时应把算法名称与实际类、参数、评分和选件分支对应起来。`AlgorithmCatalog`与`AlgorithmGuides`供界面检索和展示使用，界面分类不改变求解语义。

五个非 NF 的 Shelf-WM 组合（FF、BWF、BHF、BAF、WWF）归入兼容配置，默认收起。当前十二组面积降序实验未观察到它们相对基础规则的利用率提升，这不构成所有输入下等价的证明。完整遍历仍覆盖这五项。

MaxRects 的五种评分属于同一家族；BEST 为独立运行五种评分后择优的组合策略，成本按五次解码计算。Tabu 的六个入口对应六种禁忌记忆配置。GA 的自适应和局部强化具有实际机制差异，保留明确名称。

### Skyline WM 单变量对照

既有 `Skyline-BL/BF-Global` 使用 GLOBAL 取件，`Skyline-BL/BF-WM` 使用 SEQUENTIAL 取件，两者同时改变取件与 WM，不能据此归因 WM 收益。已有标识和求解语义保持兼容。使用下列专项命令，对 BL/BF 各自固定 GLOBAL/SEQUENTIAL 取件、旋转与排序，只切换 WM：

```bash
./scripts/run-experiments.sh skyline-wm-ablation gcut01 results/skyline-wm-ablation
```

Windows 使用 `scripts/run-experiments.ps1` 并传入相同参数。输出 `skyline-wm-ablation.csv`，每行是一对相同条件下的对照，包含双方合法性和利用率差（百分点）。十二组共产生 48 对、96 次求解。

因此项目采用三类能力目录：

- **OPTIMIZER（搜索优化层）**：Tabu、SA、ACO、ILS、自适应GA和GA+Tabu，优化零件ID的优先级排列。旋转权限由配置与解码器处理，不是搜索器直接连续优化零件角度。
- **PACKING_ENGINE（排样引擎层）**：Shelf、Bottom-Left、Skyline、Guillotine和MaxRects等，负责候选位置、空间管理和布局构造。
- **ADVANCED（专项配置）**：不进入默认求解选择器；在家族目录中说明差异，支持完整遍历、消融实验和回归验证。其中五个非 NF 的 Shelf-WM 配置标记为兼容用途，默认收起。

默认实验使用14个代表入口，高级变体用于专项对照与组合调参。界面只提供其中的常用选择，不代表其他变体未实现。

## 2. 代表算法（14个）

默认报告包含6个搜索优化算法与8个排样引擎，分别比较搜索决策与空间构造的差异。

### 搜索优化层

`TABU_MOVE`、`SA`、`ACO`、`ILS`、`ADAPTIVE_GA`、`GA_TABU`。

### 排样引擎层

| 入口 | 代表意义 |
|---|---|
| `SHELF_BAF_GLOBAL` | Shelf-GLOBAL 层式构造代表 |
| `SHELF_FC` | Floor-Ceiling 地板/天花板双向填充 |
| `BOTTOM_LEFT` | 离散候选点 Bottom-Left |
| `CONTINUOUS_BOTTOM_LEFT` | 连续移动 Bottom-Left |
| `SKYLINE` | 堆优化 Skyline |
| `SKYLINE_BL_WM` | SkylineGlobalSolver的顺序底左分支与废物地图 |
| `GUILLOTINE` | 不相交自由矩形的选区、二分与合并，未输出切割树 |
| `MAXRECTS_BEST` | 五种 MaxRects 启发式组合器 |

`SHELF_BAF_GLOBAL` 是 Shelf-GLOBAL 的展示代表，不表示已证明 BAF 在所有实例上最优。其他选层规则仍保留于专项实验中。

## 3. 高级/历史策略（32个）

### Shelf（17个）

- 原始顺序基线：`SHELF`、`SHELF_FF`、`SHELF_BWF`、`SHELF_BHF`、`SHELF_BAF`、`SHELF_WWF`。
- GLOBAL 评分变体：`SHELF_NF_GLOBAL`、`SHELF_FF_GLOBAL`、`SHELF_BWF_GLOBAL`、`SHELF_BHF_GLOBAL`、`SHELF_WWF_GLOBAL`。
- WM 变体：`SHELF_NF_WM`、`SHELF_FF_WM`、`SHELF_BWF_WM`、`SHELF_BHF_WM`、`SHELF_BAF_WM`、`SHELF_WWF_WM`。

原始 Shelf 保留为顺序处理基线与 GLOBAL 消融对照；WM 保留为碎片回收专项证据。

### Skyline-GLOBAL（3个）

`SKYLINE_BL`、`SKYLINE_BF`、`SKYLINE_BF_WM` 保留为 BL/BF 评分和 WM 消融对照，默认用 `SKYLINE_BL_WM` 代表这一支。

### MaxRects（5个）

`MAXRECTS_BSSF`、`MAXRECTS_BLSF`、`MAXRECTS_BAF`、`MAXRECTS_BL`、`MAXRECTS_CP` 仍由 `MAXRECTS_BEST` 全部运行，每个成员的面积、数量和耗时都会输出到 CSV/HTML。因此隐藏单成员不会丢失证据。

### Tabu（5个）

`TABU_FULL`、`TABU_POSITION`、`TABU_ORDER`、`TABU_ADJACENCY`、`TABU_PLACEMENT` 保留不同禁忌属性研究，默认用定义清晰、特赦有意义的 `TABU_MOVE` 代表。

### GA（2个）

`GA` 作为固定概率基线，`GA_SA` 作为另一种模因变体保留；默认分别展示 `ADAPTIVE_GA` 和 `GA_TABU`。

### ADAPTIVE_GA 的子代评分规则

交叉概率仍参考两个父代中较大的装入面积；变异概率改为参考交叉或克隆后、变异前子代自身的装入面积。两种概率都沿用当前父代种群的平均面积和最大面积，不使用历史最好值，也不随生成子代而更新参照值。父代全体同分时沿用原公式返回概率上界；否则参考面积不高于平均值时取上界，达到或超过最大值时取下界，中间线性插值。

子代预评分和最终评分共用缓存与预算，只有缓存未命中并实际解码才增加评价次数。变异及去重修改排列后必须评价最终排列；未修改时可复用缓存。预评分发现的历史最好排列会独立克隆保存，即使随后被变异覆盖或预算耗尽，也能在收尾时返回。每代只要任一已评价排列刷新历史最好，就计为该代有改进。普通固定概率 GA 和 GA+Tabu/GA+SA 不执行这项预评分。

这一改变可能增加解码成本、减少固定预算下完成的代数，并不保证改善结果。`results/ppt-report` 已用子代评分版本重新执行全部主入口实验，配置保持 STANDARD、Skyline、允许旋转、AREA_DESC 和原三个种子；修改前 CSV 备份在 `results/ppt-report-parent-fitness-20261015`。汇报 PPT 的统计与图形由新 CSV 和 SVG 更新。元数据 `adaptiveGaMutationFitness=PRE_MUTATION_CHILD`、`adaptiveGaReferencePopulation=PARENT_GENERATION` 标识新实验规则。该实验仍为 DEFAULT 预算，适合比较端到端结果，不能据此宣称严格公平的算法机制排名。

## 4. 可追溯保证

以下机制共同证明 46 个入口均曾完整实现，而不是文档列名：

1. `AlgorithmId` 仍保留全部 46 个稳定标识。
2. `SolverFactory.create` 仍能创建每个入口的求解器。
3. `AlgorithmCatalogTest` 校验 14+32=46、分层无重叠无遗漏，并实际运行代表性隐藏入口。
4. 各家族专项测试仍遍历 Shelf、Skyline、MaxRects、Guillotine 和搜索变体。
5. 每份实验报告都生成 `algorithm-catalog.csv`，并在 HTML 中展示完整能力表。
6. `full-benchmark` 可重新运行全部 46 个入口。

Guillotine的6种选区、6种分割规则和合并开关共形成72种内核配置。代表入口使用 `BSSF + SAS + 合并`，其他组合用于专项验证与调参，不计为72种独立算法。

## 5. 命令

```bash
# 默认工程主榜：14个主入口
./scripts/run-experiments.sh benchmark gcut01 results/demo 1 20261012 QUICK

# 完整实现档案：46个入口
./scripts/run-experiments.sh full-benchmark gcut01 results/full-catalog 1 20261012 QUICK

# 专项研究仍保留全变体
./scripts/run-experiments.sh shelf gcut01 results/shelf 20 20261013
./scripts/run-experiments.sh skyline gcut01 results/skyline 20 20261013
./scripts/run-experiments.sh maxrects gcut01 results/maxrects 20 20261012
```

## 6. 实验中的比较边界

比较最终结果时，要求输入、面积分母和合法性约束一致。比较算法机制时，还要控制解码器、参数、种子和计算预算。不同搜索器的评价计数、缓存计费和提前停止条件不同，不能仅凭相同名义评价次数就称严格公平。

本轮组合实验分别寻找每实例峰值配置与一套跨实例固定配置。前者用来说明已发现结果，后者用新种子考察稳定性；两者不能混成一个排名。结果与统计修正见[组合实验记录](../experiments/RESULTS.md)。

源码中的 `group_weight_percent` 仍是界面展示元数据，不属于算法参数、预算或实验权重。
