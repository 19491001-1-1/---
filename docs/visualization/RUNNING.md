# 运行与可视化说明

本文记录命令行、Web接口、前端启动和实验记录管理的具体用法。所有命令均在仓库根目录执行，另有说明的除外。问题、算法与实验主线见[首页](../../README.md)。

## 功能概览

- 问题约束：零件最多使用一次，可选 90° 旋转，所有放置必须在板材内且两两不重叠。
- 优化目标：最大化装入总面积，利用率为装入面积与板材面积之比。
- 算法家族：Shelf、Floor-Ceiling、Bottom-Left、Skyline、Guillotine、MaxRects、Tabu、SA、ACO、ILS、GA 及混合 GA。
- 实验能力：默认参数、同评价次数、同时间预算、参数扫描和多种子统计。
- 结果验证：`SolutionValidator` 独立检查边界、重叠、零件身份、重复使用和旋转权限。

代码按算法家族与策略配置组织。46个入口不代表46种独立算法，默认报告使用14个代表配置。完整目录见[算法说明](../algorithms/README.md)。

## 环境要求

- JDK 17 或更高版本
- Node.js 24.15+（前端开发与生产构建；单 jar profile 会自动下载 24.19.0）
- pnpm 10+（前端依赖使用 `pnpm-lock.yaml` 锁定）
- 可选：Playwright 的 Chromium（仅端到端冒烟需要，见「端到端冒烟」）
- macOS/Linux 可运行 Maven Wrapper 与 `.sh` 脚本
- Windows 10/11 可运行 `mvnw.cmd` 与仓库内的 PowerShell 脚本

算法核心不依赖第三方 Java 库；Web 适配层使用 Spring Boot 3.5、Bean Validation 和 Jackson，测试使用 JUnit 5 与 Spring MockMvc，依赖由 Maven Wrapper 自动管理。

## 快速开始

### 运行全部测试

```bash
./mvnw clean verify
```

命令会按 Java 17 目标编译并运行测试（含 Web API 回归、实验生命周期、事件日志、
跨进程恢复、产物生成、存储保留、分析视图与 SPA 回退等），
并强制覆盖率门禁（行 ≥ 80%、分支 ≥ 70%），报告在 `target/site/jacoco/`。仓库内置 Maven Wrapper，
无需预先安装 Maven。默认构建**不触碰 Node**，前端独立验证，见下文「单 jar 交付」。

提交前可运行 `./scripts/check-repository.sh`，检查误入仓库的教程、演示文稿、压缩资料和系统文件。CI 会自动执行同一检查。

Windows PowerShell 对应命令：

```powershell
.\mvnw.cmd clean verify
.\scripts\check-repository.ps1
```

### 运行快速实验

```bash
chmod +x scripts/run-experiments.sh
./scripts/run-experiments.sh
```

默认读取 `gcut01/`，使用 QUICK 配置运行 14 个主入口，结果写入 `results/demo/`。

Windows PowerShell：

```powershell
.\scripts\run-experiments.ps1
```

### 构建可执行包

```bash
./mvnw package
java -jar target/rectangle-packing-1.0.0.jar \
  --config config/example.properties
```

打包同时保留两个互不干扰的入口：

- `rectangle-packing-1.0.0.jar`：原有 CLI 入口，主类仍为 `com.assessment.packing.Main`；
- `rectangle-packing-1.0.0-web.jar`：包含嵌入式 Web 服务器的 Spring Boot API。

### 单 jar 交付（前端一起打包）

默认 `./mvnw clean verify` 不触碰 Node，前端独立构建、独立部署。若需要一个自带前端的
可执行包，显式启用 `frontend` profile：

```bash
./mvnw -Pfrontend package
java -jar target/rectangle-packing-1.0.0-web.jar   # 打开 http://localhost:8080 即前端
```

Windows PowerShell：

```powershell
.\mvnw.cmd -Pfrontend package
java -jar target\rectangle-packing-1.0.0-web.jar
```

该 profile 会用 `frontend-maven-plugin` 把固定版本的 Node 与 pnpm 下载到 `target/`，再复用
`frontend/package.json` 里已有的脚本执行类型检查与 `vite build`，最后把 `frontend/dist`
复制进 `target/classes/static`，由 Spring Boot 作为 classpath 静态资源托管。构建机因此
**不需要预装 Node**，本机与 CI 走同一条路径。

单 jar 形态附带一个 SPA 回退：前端路由是 history 模式，用户直接打开或刷新 `/records`
这类路径时服务端拿到的是磁盘上不存在的路径，回退统一交给 `index.html` 由前端路由接管。
回退**只作用于非 `/api` 路径**，且不会把打错的 `.js`/`.css` 地址变成 HTML；未知 API
路径与缺失静态资源统一返回结构化 404。默认构建没有 `static/index.html`，回退自动退化为空操作。

两点已知取舍：单模块结构下 `target/classes` 同时喂 CLI jar 与 web jar，因此 `-Pfrontend`
时 CLI jar 也会捎带一份 `static/`（默认构建不含）；`-Pfrontend` 不跑前端单测，前端质量由
独立的 CI job 与本地 `pnpm check` 守。

### 启动后端 API

```bash
./mvnw spring-boot:run

# 或在 package / verify 后启动附加的 Web 可执行包
java -jar target/rectangle-packing-1.0.0-web.jar
```

默认读取 `gcut01/`，可用 `--packing.instances-directory=/absolute/path`
指定另一个后端受控实例目录。第一阶段 API 包括：

```text
GET  /api/meta
GET  /api/algorithms/search
GET  /api/algorithms/decoders
GET  /api/instances
GET  /api/instances/{id}
POST /api/solve
POST /api/experiments/preview
POST /api/experiments
GET  /api/experiments
GET  /api/experiments/{id}
DELETE /api/experiments/{id}
POST /api/experiments/{id}/cancel
POST /api/experiments/{id}/retry
GET  /api/experiments/{id}/events
GET  /api/experiments/{id}/analysis
GET  /api/experiments/{id}/artifacts
POST /api/experiments/{id}/artifacts
GET  /api/experiments/{id}/artifacts/{name}
```

`POST /api/experiments/{id}/cancel` 请求取消一个在跑或排队中的实验：已算完的格子结果全部保留，
任务以 `CANCELLED` 收尾。`POST /api/experiments/{id}/retry` 只补算还没有结果的格子，
已完成的结果不会重跑，因此重试的代价与剩余工作量成正比而不是与矩阵规模成正比。
两者在语义不成立时都返回 `409` 加错误码（`EXPERIMENT_NOT_RUNNING`、`EXPERIMENT_ALREADY_RUNNING`、
`EXPERIMENT_NOTHING_TO_RETRY`），而不是静默成功。

`DELETE /api/experiments/{id}` 删除一条已结束的实验记录，任务元数据、逐格结果、
布局和已生成产物会一起删除。排队或运行中的实验必须先取消，避免在工作线程
仍在写入时拆除任务目录。记录页会在删除前再次确认，删除后不可撤销。

`GET /api/experiments/{id}/events` 的每条 SSE 事件都带 `id:` 序号与 `event:` 名
（`progress` / `complete`）。断线重连时浏览器会带上 `Last-Event-ID` 请求头，服务端据此补发
漏掉的事件；也可以显式用查询参数指定起点（`?lastEventId=7`）。任务结束后订阅仍会收到
一次进度快照与终止事件，因此「任务快到来不及订阅」不会让界面永远停在等待状态。

### 实验产物与下载边界

`POST /api/experiments/{id}/artifacts` 为一个**终态**任务生成 CSV / HTML / SVG 产物并返回清单；
`GET` 只读清单（任务不存在报 404，没生成过则返回空数组）。用 `POST` 触发写入而不是 `GET`：
浏览器、代理和预取都可能替用户发 `GET`，那会导致「看一眼清单就触发一次全量重写」。

生成是「先在暂存目录写全，再整体替换」，中途失败不会让一份半截报告覆盖掉上次可用的产物。
清单里除了 `runs.csv` / `report.html` / 各实例的 SVG，还有
`experiment-metadata.properties`——`report.html` 里有一条指向它的链接，不补写就是死链。

`GET /api/experiments/{id}/artifacts/{name}` 里，`name` **不是路径，是一把钥匙**：只有命中
服务端产物清单的名字才放行，另有两层校验（分隔符黑名单 + 规范化后必须仍在产物根目录内）。
校验不通过一律 `404 EXPERIMENT_ARTIFACT_NOT_FOUND`，与「文件不存在」不可区分，免得这个端点
变成探测存储目录结构的工具。响应一律带 `Content-Disposition: attachment`：产物里既有 HTML
也有 SVG，内联打开等于把用户实验目录里的内容当成同源页面执行。

```bash
TASK=<task-id>
curl -s -X POST "http://localhost:8080/api/experiments/$TASK/artifacts"      # 生成并取清单
curl -sO -J "http://localhost:8080/api/experiments/$TASK/artifacts/runs.csv" # 下载
```

### 结果分析

`GET /api/experiments/{id}/analysis` 一次给出前端画图所需的全部数据：标量结果 `results`、
逐格明细 `cells`（每格带一条收敛轨迹）与实例几何 `instances`（板材尺寸与零件数，用作轴标签）。
前端「结果分析」页据此画热力图（搜索器 × 解码器 × 实例任取两维）、算法排名、箱线图与
收敛曲线对比，四张图共用同一份筛选。

与产物生成不同，**这个端点不要求任务终态**：分析视图本来就是随结果陆续到达而更新的，
缺的格子保持为空而不是补 0。`POST .../artifacts` 的 409 针对的是「一次成型的快照」，
半途生成会给出误导性的报告——两者的差别是刻意的。

`cells[i]` 与 `results[i]` 逐位对齐，顺序是「实例 → 搜索器 → 解码器 → 种子」的笛卡尔积。
排名口径与核心报告一致：**同实例均值**排名，并列同名次、之后跳号。

单格收敛轨迹可能很长（每代一个点），超过 2000 点会被等距抽样并置 `curveTruncated: true`，
**末点始终保留**——曲线的终点就是这条轨迹的结论。前端在图上会明说「已抽样」。

存储目录默认最多保留 200 个任务（`packing.experiment-store-max-tasks`），超出时回收
**最旧的终态任务**。排队中与运行中的任务永不删除——它们正在被写入，而且承载着用户正在等
的结果。上限设为 0 或负数表示关闭保留策略。

### 实验任务的持久化与恢复

任务、进度、单格结果与事件序号都写在 `packing.experiment-store`（默认
`results/web-experiments`）下，每个任务一个目录，写入一律「临时文件 + 原子替换」。
服务启动时扫描该目录恢复任务：

- `RUNNING` → **转为「已取消」**，已算完的格子全部保留，可重试补齐。不自动续跑：
  重启往往正是运维在干预，此时静默拉起重活是最不该做的事。
- `QUEUED` → 自动重新排队，对用户透明。
- 终态任务原样恢复，只恢复记录、不恢复执行。

存储根目录只能由部署方通过配置指定，客户端无法提供路径。跨重启恢复的是**结果**而不是
中途状态：已算完的格子不会重算，未开始或失败的格子由「重试」接续。

`POST /api/solve` 采用始终同步的协议，接受6个代表搜索器和8个原生解码器。
评价次数与时间预算必须二选一。例如：

```bash
curl -s http://localhost:8080/api/solve \
  -H 'Content-Type: application/json' \
  -d '{
    "instanceId":"gcut1",
    "searchAlgorithm":"ADAPTIVE_GA",
    "decoder":"SKYLINE",
    "seed":20261012,
    "rotationAllowed":true,
    "budgetMode":"EVALUATIONS",
    "maxEvaluations":20000,
    "maxMilliseconds":null
  }'
```

### 启动 Vue 前端

保持后端运行，再打开另一个终端：

```bash
cd frontend
pnpm install
pnpm dev
```

开发服务器默认位于 `http://localhost:5173`，并将 `/api` 代理到
`http://localhost:8080`。前端当前提供概览、单次求解、对比实验、实验记录、
算法档案和项目说明六个页面；单次求解使用真实后端数据和 SVG 交互画布，
支持缩放、平移、矩形选择、编号/尺寸切换与 SVG 导出。

前端提交前验证：

```bash
cd frontend
pnpm check   # TypeScript 严格检查 + Vitest + 覆盖率门禁
pnpm build   # 生产构建输出到 frontend/dist/
```

### 端到端冒烟

`frontend/e2e/` 下的 Playwright 用例要跑真实的前后端（单测把 `fetch` 换成替身，
SSE 建连、真实下载与前后端字段一致性这些接缝恰好全被绕过），因此不进 `pnpm check`：

```bash
# 终端 1：后端
java -jar target/rectangle-packing-1.0.0-web.jar

# 终端 2：前端与浏览器
cd frontend
pnpm e2e:install   # 首次需要：下载 Chromium。设 PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 可跳过
pnpm test:e2e
```

目前有三条用例。`experiment-smoke` 点选一个 2×2 矩阵、预览规模、启动实验，
等进度到 100% 并确认产物清单出现；`analysis-smoke` 再跑一个小实验进「结果分析」页，
断言热力图的 4 个格子都有数（无 `.empty`）、排名表给出百分比、箱线图两行且收敛曲线渲染出 SVG
——「后端返回的字段与图表要的口径对不对得上」这条接缝只有真实数据能验。

后端没起时它们直接失败而不是静默跳过——被跳过的接缝等于没人守。`E2E_BASE_URL` 指向已有
环境时，Playwright 不再自己拉起前端。

对比实验支持实例 × 搜索器 × 解码器 × 随机种子的矩阵预览、后台并行执行、
SSE 实时进度与运行结果明细。任务记录落在后端受控目录中并支持跨重启恢复
（见上文「实验任务的持久化与恢复」）；终态任务可在「对比实验」与「实验记录」两个页面
生成并下载 CSV / HTML / SVG 产物（见上文「实验产物与下载边界」）。

### 命令行参数

使用Maven Wrapper编译和打包。当前源码包含依赖Spring Boot的Web适配层，不能把全部Java文件直接交给不带依赖的 `javac`。

`Main` 参数依次为：

```text
数据目录  是否允许旋转  输入排序  算法  [算法参数...]
```

未指定参数时使用允许旋转、面积降序和 `ADAPTIVE_GA`（QUICK 配置、Skyline 解码器），以搜索优化算法作为默认演示入口。

当前 `ADAPTIVE_GA` 用变异前子代自身的装入面积计算变异概率，参照值仍为父代种群平均面积和最大面积。子代预评分计入真实解码预算并共用缓存；详细流程见[算法说明](../algorithms/README.md#adaptive_ga-的子代评分规则)。`results/ppt-report`是默认配置实验，与后续组合调参实验分开统计。

也可以使用结构化配置文件运行，避免较长的位置参数：

```bash
java -jar target/rectangle-packing-1.0.0.jar --config config/example.properties
```

`config/example.properties` 演示了单次运行的完整配置项：`dataDirectory`、`rotationAllowed`、`order`、`algorithm`、`decoder`、`profile`、`seed` 以及 `maxEvaluations` / `maxMilliseconds` 预算（两者最多设置一个）。解码器可选 `SHELF_BAF`、`FLOOR_CEILING`、`BOTTOM_LEFT`、`CONTINUOUS_BOTTOM_LEFT`、`SKYLINE`、`SKYLINE_BL_WM`、`MAXRECTS` 或 `GUILLOTINE`。未知配置项会直接报错，防止拼写错误被默认值掩盖。

## 实验命令

```bash
# 14个工程主入口（搜索优化在前，排样引擎随后）
./scripts/run-experiments.sh benchmark gcut01 results/benchmark 5 20261012 STANDARD

# 全部46个策略配置（含兼容配置，逐实例列出最优）
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

实验默认串行。独立运行之间可并行（不进入单次搜索内部），输出的运行列表与串行完全一致：

```bash
# 并行度8
PACKING_PARALLELISM=8 ./scripts/run-experiments.sh benchmark gcut01 results/benchmark 5 20261012 STANDARD
```

时间预算实验（`fair-time`）不允许并行：并行会争抢 CPU，耗时不再公平。

详细字段和统计口径见[实验运行指南](../experiments/RUNNING.md)，算法分层见[算法说明](../algorithms/README.md)。

## 输出结构

每个实验目录包含：

```text
runs.csv                 每次运行的原始结果
summary.csv              按算法和实例汇总
overall.csv              跨实例总览
instance-winners.csv     每个实例的最优算法（按多种子平均利用率，并列全列）
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
  web/                   Spring Boot 入口、API DTO、服务与错误协议
src/test/java/           JUnit 入口与断言式回归测试
frontend/                Vue 3 + TypeScript + Vite 前端
gcut01/                  12组基准数据
scripts/                 测试和实验脚本
docs/                    架构、算法与实验文档
.github/workflows/       持续集成配置
```

长期维护文档：

- [实验运行指南](../experiments/RUNNING.md)
- [代码结构](../development/ARCHITECTURE.md)
- [算法说明](../algorithms/README.md)
- [测试说明](../development/TESTING.md)
- [修改与验证约定](../../CONTRIBUTING.md)
- [安全说明](../../SECURITY.md)

## 可复现性

相同源码、数据、参数和随机种子会产生相同布局与搜索统计。墙钟耗时受 JIT、垃圾回收和系统负载影响，正式耗时报告应先预热并重复运行。
