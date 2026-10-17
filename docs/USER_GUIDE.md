# 用户与实验指南

## 1. 一键运行

脚本会先重新编译生产源码，再启动实验入口。实验前的完整测试和覆盖率验证请单独运行 `./mvnw clean verify`：

```bash
chmod +x scripts/run-experiments.sh

# 不带参数：14个工程主入口、12个实例的快速验证
./scripts/run-experiments.sh

# 正式默认参数实验：随机算法各运行5个种子
./scripts/run-experiments.sh benchmark gcut01 results/benchmark 5 20261012 STANDARD

# 全部46个入口的完整档案实验
./scripts/run-experiments.sh full-benchmark gcut01 results/full-catalog 1 20261012 QUICK

# 十九个Shelf入口（六基础、Floor-Ceiling、六GLOBAL、六WM）的专项实验
./scripts/run-experiments.sh shelf gcut01 results/shelf 20 20261013

# 堆优化Skyline与GLOBAL Skyline-BL/BF及两个WM版本的专项实验
./scripts/run-experiments.sh skyline gcut01 results/skyline 20 20261013

# 五种MaxRects与BEST专项实验：确定性算法预热后各重复20次
./scripts/run-experiments.sh maxrects gcut01 results/maxrects 20 20261012

# 蚁群算法多种子专项实验
./scripts/run-experiments.sh aco gcut01 results/aco 20 20261013 STANDARD

# 每次搜索最多评价20000个候选解
./scripts/run-experiments.sh fair-evaluations gcut01 results/fair-evaluations 20000 5 20261012 STANDARD

# 每次搜索分配2000毫秒；程序先执行不计入结果的JIT预热
./scripts/run-experiments.sh fair-time gcut01 results/fair-time 2000 5 20261012 STANDARD

# 六种搜索算法 × 八种遵守优先级顺序的解码器
./scripts/run-experiments.sh decoder-matrix gcut01 results/decoder-matrix 20000 5 20261012 STANDARD

# 在gcut3上运行全部单因素参数实验
./scripts/run-experiments.sh sweep gcut01 results/parameter-sweep gcut3 3 20261012 STANDARD

# 依次完成上面四类实验，并生成总入口results/full/index.html
./scripts/run-experiments.sh all gcut01 results/full 5 20261012 STANDARD 20000 2000
```

`QUICK` 用于检查流程和现场快速演示，搜索规模较小；`STANDARD` 使用项目正式默认参数。

Windows PowerShell 使用同名脚本，参数顺序完全一致：

```powershell
.\scripts\run-experiments.ps1 benchmark gcut01 results/benchmark 5 20261012 STANDARD
```

## 2. 生成的文件

每个普通实验目录包含：

- `runs.csv`：每一次独立运行的种子、面积、利用率、耗时、评价次数、迭代数和验证结果；
  MaxRects-BEST行还记录采用规则、并列最优规则及五个成员的面积与耗时。
- `summary.csv`：按“算法—实例”统计最好、最差、平均、中位数、标准差和平均耗时。
- `overall.csv`：跨实例的平均利用率、平均最好利用率、平均名次和合法运行数。
- `instance-winners.csv`：逐实例遍历本次实验包含的全部算法，按多种子平均利用率列出冠军；并列冠军全部保留。
- Web 实验的冠军按“搜索器 × 解码器”配置独立比较，CSV 包含 `decoder` 列；普通命令行实验使用固定解码器，冠军行标明稳定算法配置 ID。统计仅使用合法运行，任务尚未完成时前端结果标为暂定。
- `traces.csv`：每个算法随候选解评价次数变化的历史最好面积，可用于重新作图。
- `algorithm-catalog.csv`：全部46个已实现入口的展示层级、家族、代表入口和分层原因。
- 算法页按家族展开配置；兼容配置默认收起，可勾选显示。`algorithm-catalog.csv` 同时记录 `settings` 与 `compatibility_only`，46 个配置入口不等于 46 种独立算法。
- `experiment-metadata.properties`：项目版本、Java/OS、解码器、随机种子和预算口径。
- `layouts/<实例>/<算法>.svg`：每个算法的最好排样图。原点在左下角，`↻`表示旋转；悬停可查看坐标。
- `convergence/<实例>.svg`：各元启发式算法最好一次运行的收敛曲线。
- `report.html`：可直接用浏览器打开的实验报告。

参数实验输出 `runs.csv`、`summary.csv`、`charts/*.svg` 和 `report.html`。

`decoder-matrix` 在输出根目录生成 `index.html` 和 `decoder-matrix.csv`，并为 Shelf-BAF、Floor-Ceiling、Bottom-Left、Continuous Bottom-Left、Skyline、Skyline-BL-WM、MaxRects 和 Guillotine 生成独立子报告。该入口使用统一评价预算和同一组随机种子，用于区分搜索机制与排样解码器的影响。不同解码器的单次评价成本不同，因此该矩阵的利用率是等评价次数下的质量对比，墙钟成本应结合 CSV 中的耗时单独解释。

结构化实验可通过 `./scripts/run-experiments.sh config config/experiment.properties` 启动。当 `resume=true` 时，每次已完成运行都会原子写入输出目录下的隐藏 checkpoint；再次使用相同配置启动会跳过已完成项，整个实验成功生成产物后自动删除 checkpoint。

MaxRects专项报告还包含“MaxRects-BEST组成明细”：质量统计按12个实例各计算一次，耗时则在
预热后对每个确定性算法重复多次并取均值。报告中的耗时因子使用BEST平均总耗时除以独立
BSSF平均总耗时，而不是除以BEST内部某一次极短的子计时。

## 3. 三类比较如何解释

默认参数实验回答“各算法在推荐配置下最终能做到多好”。随机算法必须使用多个种子，单次最好值只能展示潜力，平均值和标准差才反映通常表现与稳定性。

相同评价预算实验比较 Skyline 基线与 Tabu-Move、SA、ACO、ILS、自适应 GA、GA+Tabu 六个搜索算法。Skyline 只执行一次构造解码，作为“无搜索”参照；各搜索算法相对它的平均利用率提升即搜索机制的真实收益。一次候选解评价表示把一个优先级排列交给同一 Skyline 解码器计算装入面积。算法会在内部评价点检查硬上限，种群生成也不会越过该上限。

相同时间预算实验回答“在当前运行环境中给相同搜索时间谁更好”。程序会先对每种算法预热，减少类加载和JIT编译对第一种算法的不公平影响，并关闭各算法不同的“连续无改进”提前停止；只有达到100%利用率时才允许提前完成。时间限制在安全检查点生效，最终最好排列仍需额外解码一次并接受合法性验证，因此CSV中的总耗时可能略高于搜索预算。正式报告建议使用至少1000毫秒的预算，使固定开销占比较小。

## 4. 参数实验原则

参数扫描采用单因素法：每组只改变一个参数，其他参数保持基准值；不同参数值复用同一组随机种子，形成配对比较。当前覆盖：

- SA：降温率、目标初始劣解接受率；
- Tabu：禁忌期限、邻域抽样规模；
- ACO：信息素蒸发率、信息素指数、启发式指数、蚂蚁数量；
- ILS：扰动交换数、每轮局部评价预算；
- GA：变异率、种群规模、四种变异算子；
- 自适应GA：最大变异率；
- GA+SA和GA+Tabu：局部搜索间隔、局部搜索个体比例、局部评价预算。

当前自适应 GA 在交叉或克隆后先评价子代，用变异前子代自身的装入面积决定变异概率；平均面积和最大面积仍来自父代种群。预评分未命中缓存会消耗评价预算；未发生变异及去重修改时直接复用预评分，发生修改后评价最终排列。预评分找到的历史最好排列独立保存，不会被后续变异覆盖。`results/ppt-report` 已重新实验并生成新 CSV、报告、轨迹和布局；修改前版本保存在 `results/ppt-report-parent-fitness-20261015`。

不要只根据一个实例确定最终参数。先在`gcut3`上观察趋势，再用`all`对12个实例复核。例如：

```bash
./scripts/run-experiments.sh sweep gcut01 results/parameter-all all 3 20261012 STANDARD
```

## 5. 可复现性

同一源码、数据、参数和随机种子会产生相同布局、面积和搜索统计。墙钟耗时会受系统负载、JIT和垃圾回收影响，因此时间实验应重复运行并报告均值。所有最终布局都由`SolutionValidator`独立检查越界、重叠、重复零件、非法旋转和零件身份。

### 并行运行

结构化配置支持 `parallelism` 配置项（默认 `1`，即串行）。命令行走环境变量：

```bash
# 并行度8；输出的运行列表顺序和数值与串行完全一致
PACKING_PARALLELISM=8 ./scripts/run-experiments.sh benchmark gcut01 results/benchmark 5 20261012 STANDARD
```

并行只作用于**彼此独立的运行之间**（不同实例、不同算法、不同种子），不进入单次搜索内部。任务清单
按串行顺序编号、结果按下标回填，因此 `runs.csv` 与串行逐字节一致——只有耗时列例外，那是墙钟时间，本就不可复现。

**时间预算实验（`maxMilliseconds`）不允许并行**：并行会让多个算法争抢 CPU，耗时不再公平，
配置层会直接拒绝 `parallelism>1` 与 `maxMilliseconds` 的组合并报错。
