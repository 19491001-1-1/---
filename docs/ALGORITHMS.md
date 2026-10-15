# 算法目录与产品分层

## 1. 为什么分层

项目已实现并验证 46 个算法入口，但其中很多是共享同一核心的评分、属性或废物回收变体。工程主界面若将它们全部平铺，会造成“用参数组合凑算法数量”的观感，也不利于命令行、报告和前端展示。

因此项目采用三类能力目录：

- **OPTIMIZER（搜索优化层，60%）**：Tabu、SA、ACO、ILS、自适应 GA 和 GA+Tabu，负责优化排列、旋转或邻域状态，是项目主算法层。
- **PACKING_ENGINE（排样引擎层，40%）**：Shelf、Bottom-Left、Skyline、Guillotine 和 MaxRects 等，负责产生合法布局、管理空间并提供比较基线。
- **ADVANCED（高级/历史策略）**：不进入产品配置、默认报告或未来前端；源码、稳定标识、专项验证和能力档案保留，只用于回溯与消融实验。

产品接口只对外呈现 14 个代表性主入口，不用参数笛卡尔积扩充算法数量；高级策略继续用于兼容、消融实验与回归验证。

## 2. 默认工程主入口（14个）

默认报告先展示 6 个搜索优化算法，再展示 8 个排样引擎。60%/40% 是项目叙事与页面篇幅权重，不是简单按入口数量计算，也不改变实验统计的客观口径。

### 搜索优化层（60%）

`TABU_MOVE`、`SA`、`ACO`、`ILS`、`ADAPTIVE_GA`、`GA_TABU`。

### 排样引擎层（40%）

| 入口 | 代表意义 |
|---|---|
| `SHELF_BAF_GLOBAL` | Shelf-GLOBAL 层式构造代表 |
| `SHELF_FC` | Floor-Ceiling 地板/天花板双向填充 |
| `BOTTOM_LEFT` | 离散候选点 Bottom-Left |
| `CONTINUOUS_BOTTOM_LEFT` | 连续移动 Bottom-Left |
| `SKYLINE` | 堆优化 Skyline |
| `SKYLINE_BL_WM` | Skyline-GLOBAL 与废物地图代表 |
| `GUILLOTINE` | 可一刀切的不相交自由矩形 |
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

## 4. 可追溯保证

以下机制共同证明 46 个入口均曾完整实现，而不是文档列名：

1. `AlgorithmId` 仍保留全部 46 个稳定标识。
2. `SolverFactory.create` 仍能创建每个入口的求解器。
3. `AlgorithmCatalogTest` 校验 14+32=46、分层无重叠无遗漏，并实际运行代表性隐藏入口。
4. 各家族专项测试仍遍历 Shelf、Skyline、MaxRects、Guillotine 和搜索变体。
5. 每份实验报告都生成 `algorithm-catalog.csv`，并在 HTML 中展示完整能力表。
6. `full-benchmark` 可重新运行全部 46 个入口。

Guillotine 的 6 种空闲矩形选择、6 种切割规则和合并开关共形成 72 种可验证配置，但它们是同一空闲矩形内核的参数组合，不计为 72 个产品算法。工程只对外使用 `BSSF + SAS + 合并` 代表，其余组合仅用于自动化验证与策略评估。

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

## 6. 产品展示口径

推荐表述：

> 项目累计实现并通过合法性验证的算法入口共46个。工程展示以搜索优化为核心，占60%权重；排样引擎负责可行解构造、空间管理和基线对照，占40%权重。默认展示14个代表性入口，其余32个变体仍可调用、可测试、可生成完整对比报告。

前端只按 `role=OPTIMIZER`、`role=PACKING_ENGINE` 渲染 14 个主入口，不提供把 `VARIANT` 重新平铺成产品选项的开关。高级策略只在“算法档案”页面中展示；`group_weight_percent` 提供 60/40 的展示权重元数据。
