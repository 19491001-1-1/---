# 项目实现状态

## 当前范围

项目解决单张固定尺寸板材上的二维矩形选择与排放问题。零件可选 90° 旋转，目标是在边界、不重叠和唯一使用约束下最大化装入面积。

当前包含：

- 46 个可调用、可验证的算法入口；
- 14 个默认工程主入口与 32 个高级策略；主入口按搜索优化 60%、排样引擎 40% 组织；
- 14 种输入顺序；
- 独立解合法性验证；
- 评价次数和墙钟时间预算；
- 多随机种子、收敛轨迹和参数扫描；
- CSV、HTML 和 SVG 实验产物。
- Shelf-BAF、Floor-Ceiling、两种 Bottom-Left、两种 Skyline、MaxRects 和 Guillotine 共 8 种可替换搜索解码器；
- 结构化运行配置、实验元数据与断点恢复。

## 算法家族

| 家族 | 实现 |
|---|---|
| Shelf | NF、FF、BWF、BHF、BAF、WWF、Floor-Ceiling、GLOBAL、WM |
| Bottom-Left | 候选点式、连续移动式 |
| Skyline | 堆优化版、GLOBAL BL/BF、BL/BF-WM |
| Guillotine | 6种自由矩形选择 × 6种分割 × RM开关 |
| MaxRects | BSSF、BLSF、BAF、BL、CP、BEST组合器 |
| Tabu | Full、Move、Position、Order、Adjacency、Placement |
| 其他搜索 | SA、ACO、ILS、GA、Adaptive GA、GA+SA、GA+Tabu |

## 默认主入口

`SHELF_BAF_GLOBAL`、`SHELF_FC`、`BOTTOM_LEFT`、`CONTINUOUS_BOTTOM_LEFT`、`SKYLINE`、`SKYLINE_BL_WM`、`GUILLOTINE`、`MAXRECTS_BEST`、`TABU_MOVE`、`SA`、`ACO`、`ILS`、`ADAPTIVE_GA`、`GA_TABU`。

全部入口与分层原因见 [docs/算法能力分层与留痕.md](docs/算法能力分层与留痕.md)。

## 验证状态

- 生产源码以 Java 17 为目标版本，且不依赖第三方运行时库。
- Maven Wrapper 统一执行编译、JUnit 5 回归测试、打包和 JaCoCo 覆盖率报告。
- 当前有 26 个断言式回归套件和 6 个原生 JUnit 测试类，包含全入口边界、随机不变量、主入口质量基线、配置校验、断点恢复和解码器交叉验证。
- 构建门禁要求行覆盖率不低于 80%、分支覆盖率不低于 70%。
- `scripts/run-tests.sh` 保留为无需 Maven 的轻量测试入口。
- GitHub Actions 在 Temurin 17 环境执行完整的 `verify` 流程。
- Guillotine 测试覆盖 72 种配置和随机实例。
- MaxRects 测试覆盖评分、切割、包含剪枝和 BEST 组合。
- 全部 46 个入口都会运行空实例、单件、全部超限和混合边界实例。
- 全部入口还会运行固定种子的随机实例，交替验证允许旋转和禁止旋转场景。
- 所有批量实验结果都由 `SolutionValidator` 复核。

## 数据与文档

- `gcut01/`：12 组基准实例。
- `docs/`：架构、实验、测试和算法能力说明。
