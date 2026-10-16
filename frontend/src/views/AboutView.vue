<script setup lang="ts">
import { ref } from 'vue'
import { useCatalogStore } from '@/stores/catalog'

type Chapter = { id:string; number:string; eyebrow:string; title:string; summary:string; paragraphs:{title:string;text:string}[]; points:string[]; tags:string[] }
const catalog = useCatalogStore()
const chapterList = ref<HTMLElement | null>(null)
const chapters: Chapter[] = [
  { id:'problem', number:'01', eyebrow:'Problem model', title:'问题模型与优化目标', summary:'先说明平台究竟在解决什么，以及“利用率更高”为什么不等于结果一定合法。', paragraphs:[
    {title:'输入是什么',text:'一个实例由一张固定宽高的矩形板材和若干待排矩形组成。每个物品都有稳定身份、宽度、高度与是否允许旋转的信息；求解时可以选择不装入某些物品，但同一物品最多只能使用一次。平台内置 GCUT 基准实例，并且只允许通过后端建立的稳定实例 ID 访问，浏览器不会接触服务器文件路径。'},
    {title:'输出是什么',text:'输出是一组 Placement：它记录被选中的物品、左下角坐标、落板后的宽高与旋转状态。排样图只是这些结构化数据的可视化；真正可用于比较和复验的是布局数据、装入面积、利用率、运行耗时、评价次数、迭代次数和收敛轨迹。'},
    {title:'优化目标',text:'核心目标是在不越界、不重叠、不重复使用物品且尊重旋转权限的前提下，尽量增大装入总面积。利用率等于装入面积除以板材面积。搜索算法负责寻找更有希望的物品优先级，解码器负责把优先级转换成坐标布局，两者共同决定最终质量。'}],
    points:['矩形必须完全位于板材内部','任意两个已放置矩形不得发生面积重叠','同一物品最多使用一次，身份必须来自原实例','只有被授权的物品才允许旋转 90°','启发式算法追求高质量解，但不承诺全局最优'], tags:['单张板材','二维矩形','可选旋转','最大化面积'] },
  { id:'algorithms', number:'02', eyebrow:'Algorithm system', title:'搜索优化器与排样解码器', summary:'把“决定先尝试谁”和“决定放在哪里”拆成两个独立问题，使算法可以自由组合、单独验证。', paragraphs:[
    {title:'搜索器负责探索',text:'遗传算法、粒子群、差分进化、模拟退火、禁忌搜索和蚁群算法不会直接绘制矩形。它们维护、扰动并评价物品顺序或优先级，反复询问“这个候选顺序经过解码后能装下多少面积”，再根据群体协作、概率接受、记忆禁忌或信息素机制继续探索。固定种子让随机搜索可以复现。'},
    {title:'解码器负责落地',text:'Shelf、Floor-Ceiling、Bottom-Left、Skyline、MaxRects 与 Guillotine 等策略接收候选顺序，按照不同的空间表示和放置规则生成布局。解码器既决定当前矩形的候选位置，也决定空闲空间如何切分、合并和更新；同一搜索器搭配不同解码器，可能得到完全不同的结果。'},
    {title:'组合的价值',text:'产品层公开 6 种搜索优化器与 8 种顺序感知解码器，可形成 48 种标准组合。算法档案同时保留 46 个已实现入口，包括基础实现、GLOBAL 代表和历史高级变体。这种分层既能比较搜索能力，也能隔离解码策略的影响。'}],
    points:['搜索预算严格二选一：评价次数或墙钟时间','确定性解码器对相同输入应给出相同布局','随机优化器通过显式种子复现','每次解码结果都可交给独立验证器复核'], tags:['6 个搜索器','8 个产品解码器','48 种组合','46 个档案入口'] },
  { id:'solve', number:'03', eyebrow:'Single solve', title:'一次求解如何完成', summary:'从表单参数到可交互排样图，每一步都保留可检查的数据，而不是只返回一张图片。', paragraphs:[
    {title:'配置与预检',text:'用户选择实例、搜索器、解码器、随机种子和预算。前端负责易用性提示，后端再次执行权威校验：实例 ID、算法 ID、预算互斥关系和数值范围都必须有效，不能依赖浏览器输入天然可信。'},
    {title:'搜索与保留最优',text:'求解器生成候选优先级，解码器将其转成布局并计算装入面积。搜索循环持续保留历史最好解，同时记录收敛点；达到评价上限、时间上限或收到取消信号后，返回当时已经找到的最好合法结果。'},
    {title:'呈现与复验',text:'结果页将板材与矩形按比例绘制，提供缩放、物品选择和详细指标。布局进入产品结果前还会经过 SolutionValidator 独立检查，因此搜索器的自我判断不是最终依据，验证器才是几何合法性的最后防线。'}],
    points:['同步求解适合快速观察单个组合','收敛曲线展示最好面积随评价推进的变化','每个放置项可追溯到原物品身份','取消时仍尽量返回历史最好解'], tags:['输入校验','历史最优','交互排样图','独立复验'] },
  { id:'experiments', number:'04', eyebrow:'Experiment engine', title:'批量实验、并行与公平比较', summary:'把算法、解码器、实例和种子组成实验矩阵，用同一口径产生可比较的数据。', paragraphs:[
    {title:'实验矩阵',text:'实验任务是搜索器 × 解码器 × 实例 × 种子的笛卡尔积。创建前的预览会计算格子数量并验证预算规则，让用户在提交前看清工作量；任务提交后进入后台队列，页面无需保持打开。'},
    {title:'受控并发',text:'平台使用固定两级并发模型：调度层同时推进有限数量的实验，工作层共享有上限的线程池，每个实验再由自己的 parallelism 限制在飞格子数，避免多个任务各自创建线程池而造成 CPU 争抢和线程爆炸。'},
    {title:'公平与复现',text:'评价次数预算适合比较候选评估量；时间预算更接近固定响应时长，但受机器负载影响，因此时间预算实验禁止并行。随机算法使用明确种子，多次重复用于观察稳定性；并行结果按原清单下标回填，使输出顺序与串行一致。'}],
    points:['任务支持排队、运行、完成、失败和取消','取消由求解循环协作响应','重试只补算缺失或失败格子','SSE 带事件序号并支持断线续接'], tags:['矩阵预览','后台执行','受控并行','取消与重试'] },
  { id:'analysis', number:'05', eyebrow:'Analysis', title:'统计分析与结果解释', summary:'不仅回答“谁最好”，还展示差异是否稳定、何时收敛，以及不同组合在哪些实例上占优。', paragraphs:[
    {title:'四种互补视图',text:'热力图适合扫描搜索器—解码器组合；排名将多格结果归纳为可排序指标；箱线图展示多种子结果的中位数、离散程度和异常点；收敛对比说明算法在相同预算下提升得快不快、是否早早停滞。'},
    {title:'运行中分析',text:'分析端点允许读取尚未结束的任务，已完成格子立即进入图表，未完成位置保持为空。这和正式产物不同：图表是随进度更新的观察窗口，而 CSV、HTML、SVG 报告必须等待终态后再生成，避免把半成品误当最终报告。'},
    {title:'口径边界',text:'单次最好值不能代表随机算法的稳定表现，应结合多种子分布、均值或中位数阅读；墙钟耗时依赖硬件和负载，不宜跨机器直接排名。超长收敛轨迹会抽样到可视范围，但始终保留最后一点。'}],
    points:['利用率用于质量比较，耗时用于成本观察','箱线图需要多个独立种子才有解释力','收敛曲线应在相同预算下比较','缺失格子与真实零利用率不会混淆'], tags:['热力图','排名','箱线图','收敛对比'] },
  { id:'persistence', number:'06', eyebrow:'Persistence', title:'任务持久化与重启恢复', summary:'实验不只存在浏览器内存中；每个任务、格子结果和事件序号都会进入受控存储。', paragraphs:[
    {title:'增量落盘',text:'一个任务对应一个目录，元数据、每格标量结果、布局与收敛轨迹分别保存。每次写入先生成同目录唯一临时文件，再原子替换目标；即使进程中断，磁盘也应保留旧的完整版本或新的完整版本，而不是半截元数据。'},
    {title:'恢复语义',text:'服务启动时扫描任务目录：排队任务自动重新排队；运行任务改记为已取消，因为原进程工作线程已经不存在；完成、失败和取消任务按原状态恢复。用户可以对中断任务执行重试，继续补齐缺失格子。'},
    {title:'保留策略',text:'默认最多保留 200 个任务，超过上限时只清理最旧的终态记录。排队和运行目录绝不会为了凑上限被删除；如果没有安全候选，宁可临时超限。'}],
    points:['事件序号随进度落盘','标量与大体积布局分离','同一任务元数据写入串行化','任务在服务重启后仍可查看'], tags:['原子写入','增量结果','状态恢复','终态清理'] },
  { id:'artifacts', number:'07', eyebrow:'Artifacts', title:'实验产物与导出', summary:'把机器可读数据、可视化图形和浏览报告组合成可归档、可复查的实验包。', paragraphs:[
    {title:'包含内容',text:'平台可按需生成逐次运行 CSV、统计汇总 CSV、跨实例汇总、SVG 排样与收敛图、HTML 报告以及实验元数据。CSV 适合继续统计，SVG 适合无损放大和论文插图，HTML 把关键表格与图片组织成浏览入口。'},
    {title:'终态后生成',text:'导出代表一次确定的实验快照，所以运行中禁止生成。构建先写入独立暂存目录，全部成功后再整体替换正式目录；若中途失败，上一次完整产物仍然可用。'},
    {title:'下载边界',text:'浏览器传来的产物名是服务端清单中的键，而不是任意文件路径。控制字符、越界路径和未登记名称都会得到相同的未找到响应；HTML 与 SVG 统一作为附件下载，避免同源执行。'}],
    points:['CSV 保留种子、面积、利用率、耗时等字段','图形按实例或组合组织','元数据帮助复现实验口径','生成失败不破坏上一次完整导出'], tags:['CSV','SVG','HTML','可复现元数据'] },
  { id:'architecture', number:'08', eyebrow:'Architecture', title:'系统分层与接口边界', summary:'Web 是核心求解能力的适配层，不复制算法实现，也不让传输对象泄露内部文件和运行对象。', paragraphs:[
    {title:'核心层',text:'Instance / Item 描述问题，PackingAlgorithm 定义搜索，PackingDecoder 定义几何解码，Solution / Placement 承载结果，SolutionValidator 独立验证。CLI、批量脚本和 Web 共享同一套核心实现，避免不同入口产生不同算法语义。'},
    {title:'Web 适配层',text:'Spring Boot 控制器只处理 HTTP、JSON、状态码和 DTO 转换，再调用目录、实例、求解、实验与分析服务。DTO 不暴露求解器对象、内部配置类或服务器路径；异常映射成稳定结构化错误。'},
    {title:'前端层',text:'Vue 3 单页应用负责配置、过程观察、几何可视化和数据分析。单 JAR 部署对非 API、非静态文件路径回退到 index.html，因此直接刷新子路由仍能打开，同时未知 API 不会被伪装成网页。'}],
    points:['同一 Maven 模块服务核心、CLI 和 Web','Vite 本地开发代理 /api','生产资源可装入 Web JAR','稳定 ID 替代服务器真实路径'], tags:['Java 17+','Spring Boot','Vue 3','TypeScript'] },
  { id:'quality', number:'09', eyebrow:'Quality gates', title:'校验、测试与质量门禁', summary:'把算法正确性、接口契约、恢复语义和前端交互分别验证，避免“页面能开”掩盖深层错误。', paragraphs:[
    {title:'解的合法性',text:'SolutionValidator 检查坐标非负、矩形不越板、任意两件不重叠、物品身份存在且不重复、落板尺寸与原尺寸或合法旋转一致。它独立于具体算法，因此新增搜索器或解码器也必须通过同一判定。'},
    {title:'测试层次',text:'核心单元测试覆盖几何与算法不变量；固定种子回归监控实现漂移；Web 集成测试验证请求、响应、状态码、恢复与下载边界；前端测试覆盖状态计算、数据转换、图表和交互。当前项目维护 257 项自动化测试。'},
    {title:'覆盖率门禁',text:'覆盖率不是正确性的替代品，但能阻止关键分支在无测试状态下进入主干。后端完整验证要求行覆盖率至少 80%、分支覆盖率至少 70%；并发、取消、持久化和路径边界还有专门行为断言。'}],
    points:['固定种子强调可复现','测试非法 ID、预算与产物路径','构建前执行前端类型检查','视觉验证覆盖桌面和移动布局'], tags:['257 项测试','80% 行覆盖','70% 分支覆盖','固定种子回归'] },
  { id:'delivery', number:'10', eyebrow:'Delivery & scope', title:'构建、运行方式与能力边界', summary:'说明如何交付平台，也明确当前模型不解决哪些问题，避免把启发式演示误解成万能排样系统。', paragraphs:[
    {title:'开发与交付',text:'默认 Maven 验证不强制使用 Node；启用 frontend profile 时执行前端类型检查与生产构建，把静态资源装入 Spring Boot Web JAR。最终交付物可以用一条 Java 命令启动，浏览器通过同一服务访问页面和 API。'},
    {title:'能力边界',text:'平台面向单张板材上的二维矩形选择与排布，支持可选 90° 旋转。它目前不处理多张板材成本优化、任意多边形、带孔零件、连续角度旋转、刀路规划、材料纹理、间隙补偿或真实设备约束。'},
    {title:'正确解读',text:'所有产品搜索器都是启发式或元启发式方法：较高利用率只代表在给定实例、配置、预算和种子下找到更好的已知解，不代表证明了全局最优。严肃结论应固定环境和预算，使用多个实例与种子，并保存完整配置和版本。'}],
    points:['本地、CLI 与单 JAR 共用核心代码','临时公网隧道不等同长期托管','长期上线需域名、HTTPS、鉴权、备份和监控','结论需注明版本、实例、预算、种子和硬件'], tags:['单 JAR','本地与 Web','明确非目标','可复现实验'] }
]
function setAllChapters(open:boolean){ chapterList.value?.querySelectorAll<HTMLDetailsElement>('details').forEach(c=>{c.open=open}) }
</script>

<template><div class="about-page">
  <header class="about-hero"><div><p class="section-kicker">项目说明 · Project Handbook</p><h2>从一组矩形，到一份<br><span>可验证、可复现</span>的排样结论。</h2></div><p>这不是只会画排样图的演示页面。平台把问题建模、搜索、几何解码、独立校验、批量实验、实时进度、统计分析和产物归档串成一条完整工程链路。</p></header>
  <section class="about-facts"><div><strong>6 × 8</strong><span>产品算法矩阵</span><small>搜索优化器 × 顺序感知解码器</small></div><div><strong>46</strong><span>档案实现入口</span><small>基础、GLOBAL 与历史高级变体</small></div><div><strong>12</strong><span>GCUT 基准实例</span><small>通过稳定 ID 安全读取</small></div><div><strong>257</strong><span>自动化测试</span><small>核心、Web 与前端多层验证</small></div></section>
  <section class="about-definition"><div class="definition-copy"><p class="section-kicker">一句话定义</p><h3>在所有合法摆法中，寻找装入面积尽可能大的那一个。</h3><p>每件物品最多使用一次，可以在授权时旋转 90°；所有矩形必须留在板材内且互不重叠。平台用启发式搜索寻找高质量答案，再由独立验证器检查答案是否合法。</p></div><div class="formula-card"><span>PRIMARY METRIC</span><strong>利用率 = 装入总面积 ÷ 板材面积</strong><small>质量比较还需同时观察预算、种子分布、收敛过程与运行环境。</small></div></section>
  <section class="about-pipeline"><header><div><p class="section-kicker">端到端链路</p><h3>数据怎样变成结论</h3></div><p>每一层只承担清晰职责，并把可检查的数据交给下一层。</p></header><div class="pipeline-grid"><article v-for="item in [['01 · INPUT','实例与约束','读取板材、物品尺寸、身份和旋转权限，建立受控输入。'],['02 · SEARCH','探索优先级','搜索器在预算内产生并改进候选物品顺序。'],['03 · DECODE','生成几何布局','解码器将顺序翻译成坐标、尺寸与旋转状态。'],['04 · VERIFY','独立合法性检查','复核越界、重叠、身份、重复使用和旋转权限。'],['05 · EXPERIMENT','矩阵化运行','跨算法、实例和种子批量执行，持久化每格结果。'],['06 · EXPLAIN','分析与归档','用图表、CSV、SVG 和 HTML 把结果变成证据。']]" :key="item[0]"><span>{{ item[0] }}</span><strong>{{ item[1] }}</strong><p>{{ item[2] }}</p></article></div></section>
  <section class="about-index"><header><div><p class="section-kicker">阅读目录</p><h3>十个章节覆盖完整工程</h3></div><p>点击目录可直接跳到对应章节；每章都可独立展开。</p></header><nav><a v-for="c in chapters" :key="c.id" :href="`#${c.id}`"><span>{{c.number}}</span><strong>{{c.title}}</strong><small>{{c.eyebrow}}</small></a></nav></section>
  <section class="about-chapter-shell"><header class="chapter-toolbar"><div><p class="section-kicker">详细说明</p><h3>按需阅读，不让长文压垮页面</h3></div><div><button type="button" @click="setAllChapters(true)">全部展开</button><button type="button" @click="setAllChapters(false)">全部收起</button></div></header>
    <div ref="chapterList" class="about-chapters"><details v-for="(c,i) in chapters" :id="c.id" :key="c.id" class="about-chapter" :open="i===0"><summary><span>{{c.number}}</span><div><small>{{c.eyebrow}}</small><h3>{{c.title}}</h3><p>{{c.summary}}</p></div><i>＋</i></summary><div class="chapter-content"><div class="chapter-prose"><article v-for="p in c.paragraphs" :key="p.title"><h4>{{p.title}}</h4><p>{{p.text}}</p></article></div><aside><strong>关键约束与结论</strong><ul><li v-for="x in c.points" :key="x">{{x}}</li></ul><div><span v-for="t in c.tags" :key="t">{{t}}</span></div></aside></div></details></div>
  </section>
  <section class="about-boundary"><div><p class="section-kicker">使用建议</p><h3>先看单次布局，再用实验回答“是否稳定更好”。</h3></div><p>“在线求解”适合观察一个组合如何工作；“实验中心”适合按统一预算批量比较；“分析视图”用于解释质量、分布和收敛；“运行记录”保存任务、恢复状态并生成最终产物。算法档案则提供每种搜索器、解码器和历史变体的思想说明。</p></section>
  <section class="version-line"><span>平台版本</span><strong>{{catalog.meta?.version??'—'}}</strong><span>Java {{catalog.meta?.javaVersion??'17+'}}</span><span>Vue 3 + Spring Boot</span></section>
</div></template>
