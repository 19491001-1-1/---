# 二维矩形排样实验平台

这是一个面向单张固定尺寸板材的 Java 二维矩形排样项目。系统实现构造式启发算法、自由矩形算法和元启发式搜索；搜索层可与 8 个遵守优先级顺序的排样解码器自由组合，并支持批量实验、独立合法性验证、可复现随机种子和 HTML/SVG/CSV 报告。

## 功能概览

- 问题约束：零件最多使用一次，可选 90° 旋转，所有放置必须在板材内且两两不重叠。
- 优化目标：最大化装入总面积，利用率为装入面积与板材面积之比。
- 算法家族：Shelf、Floor-Ceiling、Bottom-Left、Skyline、Guillotine、MaxRects、Tabu、SA、ACO、ILS、GA 及混合 GA。
- 实验能力：默认参数、同评价次数、同时间预算、参数扫描和多种子统计。
- 结果验证：`SolutionValidator` 独立检查边界、重叠、零件身份、重复使用和旋转权限。

项目保留 46 个已实现入口。默认报告展示 14 个具代表性的工程入口：搜索优化层承担 60% 的主展示权重，排样引擎层承担 40%，其余评分、属性和消融变体作为高级策略保留。分层依据见 [算法能力分层与留痕](docs/算法能力分层与留痕.md)。

## 环境要求

- JDK 17 或更高版本
- macOS/Linux 可直接运行 Maven Wrapper 和项目脚本
- Windows 可使用 `mvnw.cmd`

生产代码不依赖第三方 Java 库；测试使用 JUnit 5，依赖由 Maven Wrapper 自动管理。

## 快速开始

### 运行全部测试

```bash
./mvnw clean verify
```

命令会按 Java 17 目标编译，使用 JUnit 5 运行 26 个断言式回归套件与 6 个原生 JUnit 测试类，并在 `target/site/jacoco/` 生成覆盖率报告。仓库内置 Maven Wrapper，无需预先安装 Maven。

如需完全不经过 Maven，也可运行 `./scripts/run-tests.sh`；该脚本使用 JDK 自带工具执行 26 个不依赖 JUnit 运行时的断言式回归测试。完整验证以 Maven `verify` 为准。

提交前可运行 `./scripts/check-repository.sh`，检查误入仓库的教程、演示文稿、压缩资料和系统文件。CI 会自动执行同一检查。

### 运行快速实验

```bash
chmod +x scripts/run-experiments.sh
./scripts/run-experiments.sh
```

默认读取 `gcut01/`，使用 QUICK 配置运行 14 个主入口，结果写入 `results/demo/`。

### 构建可执行包

```bash
./mvnw package
java -jar target/rectangle-packing-1.0.0.jar \
  --config config/example.properties
```

### 不使用 Maven 时手动编译

```bash
mkdir -p out
find src/main/java -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 -d out

java -cp out com.assessment.packing.Main \
  gcut01 true AREA_DESC ADAPTIVE_GA
```

此方式只编译生产代码并运行命令行入口。需要完整 JUnit 测试、覆盖率门禁和打包时，仍以 `./mvnw clean verify` 为准。

`Main` 参数依次为：

```text
数据目录  是否允许旋转  输入排序  算法  [算法参数...]
```

未指定参数时使用允许旋转、面积降序和 `ADAPTIVE_GA`（QUICK 配置、Skyline 解码器），以搜索优化算法作为默认演示入口。

也可以使用结构化配置文件运行，避免较长的位置参数：

```bash
java -jar target/rectangle-packing-1.0.0.jar --config config/example.properties
```

`config/example.properties` 演示了单次运行的完整配置项：`dataDirectory`、`rotationAllowed`、`order`、`algorithm`、`decoder`、`profile`、`seed` 以及 `maxEvaluations` / `maxMilliseconds` 预算（两者最多设置一个）。解码器可选 `SHELF_BAF`、`FLOOR_CEILING`、`BOTTOM_LEFT`、`CONTINUOUS_BOTTOM_LEFT`、`SKYLINE`、`SKYLINE_BL_WM`、`MAXRECTS` 或 `GUILLOTINE`。未知配置项会直接报错，防止拼写错误被默认值掩盖。

## 实验命令

```bash
# 14个工程主入口（搜索优化在前，排样引擎随后）
./scripts/run-experiments.sh benchmark gcut01 results/benchmark 5 20261012 STANDARD

# 全部46个已实现入口
./scripts/run-experiments.sh full-benchmark gcut01 results/full-catalog 1 20261012 QUICK

# 专项实验
./scripts/run-experiments.sh shelf gcut01 results/shelf 20 20261013
./scripts/run-experiments.sh skyline gcut01 results/skyline 20 20261013
./scripts/run-experiments.sh maxrects gcut01 results/maxrects 20 20261012
./scripts/run-experiments.sh aco gcut01 results/aco 20 20261013 STANDARD

# 统一预算与参数扫描
./scripts/run-experiments.sh fair-evaluations gcut01 results/fair-evaluations 20000 5 20261012 STANDARD
./scripts/run-experiments.sh fair-time gcut01 results/fair-time 2000 5 20261012 STANDARD
./scripts/run-experiments.sh decoder-matrix gcut01 results/decoder-matrix 20000 5 20261012 STANDARD
./scripts/run-experiments.sh sweep gcut01 results/parameter-sweep gcut3 3 20261012 STANDARD
```

详细字段和统计口径见 [实验与图表使用说明](docs/实验与图表使用说明.md)。

## 输出结构

每个实验目录包含：

```text
runs.csv                 每次运行的原始结果
summary.csv              按算法和实例汇总
overall.csv              跨实例总览
traces.csv               收敛轨迹
algorithm-catalog.csv    完整算法能力目录
experiment-metadata.properties  运行环境、解码器与预算口径
report.html              可独立浏览的实验报告
layouts/                 最佳排样 SVG
convergence/             收敛曲线 SVG
```

## 项目结构

```text
src/main/java/com/assessment/packing/
  Main.java              单算法命令行入口
  model/                 实例、零件、放置和解
  io/                    数据读取
  solver/                算法实现与搜索统计
  validation/            独立合法性验证
  experiment/            算法目录、批量运行和报告生成
src/test/java/           JUnit 入口与断言式回归测试
gcut01/                  12组基准数据
scripts/                 测试和实验脚本
docs/                    架构、算法与实验文档
.github/workflows/       持续集成配置
```

详细设计见 [架构说明](docs/架构说明.md)，测试分层见 [测试策略](docs/测试策略.md)，当前实现状态见 [PROJECT_STATUS.md](PROJECT_STATUS.md)。

## 可复现性

相同源码、数据、参数和随机种子会产生相同布局与搜索统计。墙钟耗时受 JIT、垃圾回收和系统负载影响，正式耗时报告应先预热并重复运行。
