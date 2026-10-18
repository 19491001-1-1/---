# 算法源码

这里存放实际参与编译的算法实现，不是展示用副本。前端、HTTP接口和实验任务管理位于根目录的其他文件夹。

## 搜索优化器

| 算法 | 实现 | 参数 |
|---|---|---|
| Tabu-Move及禁忌变体 | [TabuSearchSolver](src/main/java/com/assessment/packing/solver/TabuSearchSolver.java) | [TabuSearchConfig](src/main/java/com/assessment/packing/solver/TabuSearchConfig.java)、[TabuMode](src/main/java/com/assessment/packing/solver/TabuMode.java) |
| 模拟退火SA | [SimulatedAnnealingSolver](src/main/java/com/assessment/packing/solver/SimulatedAnnealingSolver.java) | [SimulatedAnnealingConfig](src/main/java/com/assessment/packing/solver/SimulatedAnnealingConfig.java) |
| 蚁群ACO | [AntColonySolver](src/main/java/com/assessment/packing/solver/AntColonySolver.java) | [AntColonyConfig](src/main/java/com/assessment/packing/solver/AntColonyConfig.java) |
| 迭代局部搜索ILS | [IteratedLocalSearchSolver](src/main/java/com/assessment/packing/solver/IteratedLocalSearchSolver.java) | [IteratedLocalSearchConfig](src/main/java/com/assessment/packing/solver/IteratedLocalSearchConfig.java) |
| 自适应GA | [AdaptiveGeneticAlgorithmSolver](src/main/java/com/assessment/packing/solver/AdaptiveGeneticAlgorithmSolver.java)、[共享遗传框架](src/main/java/com/assessment/packing/solver/GeneticAlgorithmSolver.java) | [AdaptiveGeneticAlgorithmConfig](src/main/java/com/assessment/packing/solver/AdaptiveGeneticAlgorithmConfig.java) |
| GA、GA+Tabu、GA+SA | [GeneticAlgorithmSolver](src/main/java/com/assessment/packing/solver/GeneticAlgorithmSolver.java) | [GeneticAlgorithmConfig](src/main/java/com/assessment/packing/solver/GeneticAlgorithmConfig.java)、[HybridGeneticAlgorithmConfig](src/main/java/com/assessment/packing/solver/HybridGeneticAlgorithmConfig.java) |

混合算法共用遗传框架，通过局部搜索模式区分，并非每个算法名称都有一个独立Solver文件。入口与STANDARD参数的组装在[SolverFactory](../src/main/java/com/assessment/packing/experiment/SolverFactory.java)。

## 排样引擎

| 引擎 | 实现 |
|---|---|
| Shelf BAF Global及层算法变体 | [ShelfSolver](src/main/java/com/assessment/packing/solver/ShelfSolver.java) |
| Shelf Floor-Ceiling | [ShelfFloorCeilingSolver](src/main/java/com/assessment/packing/solver/ShelfFloorCeilingSolver.java) |
| Bottom-Left | [BottomLeftSolver](src/main/java/com/assessment/packing/solver/BottomLeftSolver.java) |
| Continuous Bottom-Left | [ContinuousBottomLeftSolver](src/main/java/com/assessment/packing/solver/ContinuousBottomLeftSolver.java) |
| Skyline | [SkylineSolver](src/main/java/com/assessment/packing/solver/SkylineSolver.java) |
| Skyline BL-WM及轮廓变体 | [SkylineGlobalSolver](src/main/java/com/assessment/packing/solver/SkylineGlobalSolver.java) |
| Guillotine | [GuillotineSolver](src/main/java/com/assessment/packing/solver/GuillotineSolver.java) |
| MaxRects及BEST组合 | [MaxRectsSolver](src/main/java/com/assessment/packing/solver/MaxRectsSolver.java)、[MaxRectsBestSolver](src/main/java/com/assessment/packing/solver/MaxRectsBestSolver.java) |

## 共享机制与数据

- [PackingDecoder](src/main/java/com/assessment/packing/solver/PackingDecoder.java)与[PackingDecoders](src/main/java/com/assessment/packing/solver/PackingDecoders.java)：优先级排列与排样引擎之间的接口和适配。
- [DecodingCache](src/main/java/com/assessment/packing/solver/DecodingCache.java)与[SequenceKey](src/main/java/com/assessment/packing/solver/SequenceKey.java)：排列面积缓存与键副本。
- [SearchLimit](src/main/java/com/assessment/packing/solver/SearchLimit.java)：搜索预算与取消检查。
- [ItemOrder](src/main/java/com/assessment/packing/solver/ItemOrder.java)与[ItemOrdering](src/main/java/com/assessment/packing/solver/ItemOrdering.java)：优先级排序。
- [model/](src/main/java/com/assessment/packing/model/)：实例、零件、Placement与Solution。
- [SolutionValidator](src/main/java/com/assessment/packing/validation/SolutionValidator.java)：独立布局合法性验证。

建议从一个Solver及其Config开始，再看解码器、缓存和预算。流程解释见[算法文档](../docs/algorithms/README.md)，实验用法见[运行说明](../docs/experiments/RUNNING.md)。

## 编译与运行

在仓库根目录执行`./mvnw clean verify`，其余运行命令不变。这里是独立源码目录，仍由根目录的单个Maven项目统一编译，Java包名保持`com.assessment.packing`。测试保留在[src/test/](../src/test/)，不需要启动前端。
