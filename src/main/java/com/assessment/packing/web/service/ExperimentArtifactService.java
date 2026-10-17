package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.experiment.ExperimentArtifacts;
import com.assessment.packing.experiment.RunObservation;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Placement;
import com.assessment.packing.model.Item;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.MaxRectsBestStatistics;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.dto.SolveDtos;
import com.assessment.packing.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Web 实验任务的产物生成与安全下载。
 *
 * <p>这里刻意<b>适配</b>核心层的 {@link ExperimentArtifacts} 而不是复制它的实现：CSV 的列、
 * SVG 的坐标变换、报告里的统计口径都只有一份，改一处两边一起变，不会出现「Web 下载的图和
 * 命令行跑出来的图不一致」这种最难查的分叉。
 *
 * <p>产物落在任务目录下的 {@code artifacts/}，与持久化文件（{@code meta.bin}、{@code cells/}）
 * 分开存放，因此清理产物不会伤到任务恢复所需的数据，反之亦然。
 *
 * <p><b>下载的安全边界是三层叠加的</b>，缺一层都可能被绕过：
 * <ol>
 *   <li><b>白名单</b>：文件名必须命中服务端自己扫出来的产物清单。客户端给的名字不是路径，
 *       只是一把「查表用的钥匙」，表里没有就是 404。</li>
 *   <li><b>分隔符黑名单</b>：拒绝含 {@code /}、{@code \\} 或 {@code ..} 的名字，
 *       与 {@link ExperimentTaskStore} 挡任务 id 用的是同一套判据。</li>
 *   <li><b>归一化兜底</b>：{@code resolve(name).normalize()} 之后必须仍以任务根目录开头。
 *       哪怕前两层将来被改松，这一层也能挡住逃逸。</li>
 * </ol>
 */
@Service
public final class ExperimentArtifactService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExperimentArtifactService.class);

    /** 产物子目录名。与 {@code meta.bin}、{@code cells/} 平级。 */
    static final String ARTIFACT_DIRECTORY = "artifacts";

    /**
     * 一次生成的产物上限。清单与下载都只认这些名字，因此它同时也是下载白名单的定义。
     * 布局与收敛图按「实例 × 算法」展开，理论上会更多，这里只挡病态输入（例如几百个实例）。
     */
    private static final int MAX_ARTIFACTS = 512;

    /**
     * 分析视图里单格收敛轨迹的点数上限。轨迹是「每代/每轮一个点」，20 万次评价的实验能攒出
     * 十几万个点，原样塞进 JSON 是几十 MB：前端画不动，网络也白扛。超过就等距抽样
     * （见 {@link #downsampled}），两千点以内曲线的形状与终点已经看不出差别。
     */
    private static final int MAX_ANALYSIS_POINTS = 2_000;

    private final ExperimentTaskService tasks;
    private final InstanceCatalogService instances;
    private final Path storeRoot;

    public ExperimentArtifactService(
            ExperimentTaskService tasks,
            InstanceCatalogService instances,
            @Value("${packing.experiment-store:results/web-experiments}") String storeDirectory) {
        this.tasks = tasks;
        this.instances = instances;
        this.storeRoot = Path.of(storeDirectory).toAbsolutePath().normalize();
    }

    // ---------------------------------------------------------------- 清单

    /**
     * 列出任务目录下已生成的产物。任务不存在报 404；还没生成过产物时返回空清单而不是 404——
     * 「尚未生成」和「任务不存在」是两回事，前端据此决定是「点一下生成」还是「报错」。
     */
    public List<ExperimentDtos.Artifact> list(String id) {
        ExperimentTaskService.JobView job = tasks.jobView(id);
        Path directory = artifactDirectory(job.id());
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .map(path -> descriptor(directory, path))
                    .sorted(Comparator.comparing(ExperimentDtos.Artifact::name))
                    .limit(MAX_ARTIFACTS)
                    .toList();
        } catch (IOException exception) {
            LOGGER.warn("读取实验任务 {} 的产物清单失败", id, exception);
            return List.of();
        }
    }

    private static ExperimentDtos.Artifact descriptor(Path root, Path file) {
        String name = root.relativize(file).toString().replace('\\', '/');
        long size;
        try {
            size = Files.size(file);
        } catch (IOException exception) {
            size = 0L;
        }
        return new ExperimentDtos.Artifact(name, size, contentType(name));
    }

    // ---------------------------------------------------------------- 生成

    /**
     * 为一个终态任务生成全部产物（CSV、HTML、SVG）。
     *
     * <p>生成方式是「先写临时目录，再整体替换」。{@link ExperimentArtifacts#writeAll} 内部
     * 会静默覆盖同名文件、且不做任何同步，若直接写进目标目录，中途失败会留下一个
     * 「一半是旧报告、一半是新报告」的目录；而报告里的链接指向的是按名字引用的兄弟文件，
     * 半新半旧比彻底没有更难排查。
     *
     * <p>非终态任务会被拒绝（409）：它的格子还在陆续到达，此刻生成的产物注定是残缺的，
     * 与其给出一份会误导人的报告，不如让用户等它跑完——或者先取消再生成。
     */
    public List<ExperimentDtos.Artifact> generate(String id) {
        ExperimentTaskService.JobView job = tasks.jobView(id);
        if (!job.terminal()) {
            throw new ApiException(HttpStatus.CONFLICT, "EXPERIMENT_NOT_COMPLETED",
                    "实验尚未结束，产物生成需要任务进入终态：" + job.status());
        }
        List<RunObservation> runs = observations(job);
        if (runs.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "EXPERIMENT_NOT_COMPLETED",
                    "实验没有任何已完成的结果，无法生成产物");
        }

        Path taskRoot = taskRoot(job.id());
        Path target = taskRoot.resolve(ARTIFACT_DIRECTORY);
        Path staging = taskRoot.resolve(ARTIFACT_DIRECTORY + ".building-" + System.nanoTime());
        try {
            Files.createDirectories(staging);
            PackingDecoderType decoder = decoderOf(job);
            List<ExperimentArtifacts.ConfigurationRun> configurations = java.util.Arrays.stream(job.results())
                    .filter(java.util.Objects::nonNull)
                    .map(row -> new ExperimentArtifacts.ConfigurationRun(row.instanceId(), row.searchAlgorithm(),
                            row.decoder(), row.legal(), row.utilization() * 100)).toList();
            ExperimentArtifacts.writeAll(staging, runs, job.name(), decoder, configurations);
            writeMetadata(staging, job, decoder);
            // 换手：先把旧的挪开，再把新的挪进来。旧目录只在成功后删除，
            // 因此这一步失败时磁盘上仍是上一次的完整产物，而不是空目录。
            replaceDirectory(staging, target);
        } catch (IOException exception) {
            LOGGER.error("实验任务 {} 的产物生成失败", id, exception);
            deleteQuietly(staging);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPERIMENT_ARTIFACT_FAILED",
                    "产物生成失败，请查看后端日志");
        }
        return list(id);
    }

    /**
     * 用一份已经生成好的目录整体替换目标目录。顺序是「旧目录改名 → 新目录就位 → 删旧目录」，
     * 中间任何一步失败都保住一份完整产物，不会出现「目标目录存在但里面什么都没有」的窗口。
     */
    private static void replaceDirectory(Path staging, Path target) throws IOException {
        Path retired = null;
        if (Files.isDirectory(target)) {
            retired = target.resolveSibling(target.getFileName() + ".retired-" + System.nanoTime());
            Files.move(target, retired, StandardCopyOption.ATOMIC_MOVE);
        }
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            // 新目录没能就位，把旧的放回去，别让用户连上一次的产物都看不到。
            if (retired != null) {
                try {
                    Files.move(retired, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException suppressed) {
                    exception.addSuppressed(suppressed);
                }
            }
            throw exception;
        }
        if (retired != null) deleteQuietly(retired);
    }

    /**
     * 补写 {@code experiment-metadata.properties}。
     *
     * <p>{@link ExperimentArtifacts#writeAll} 不生成这个文件，但它的 {@code report.html}
     * 里有一条指向它的链接（「实验环境和解码器记录见 …」）。不补就是一条死链——
     * 报告在浏览器里点一下就 404，比没有这条链接更糟。
     */
    private void writeMetadata(Path directory, ExperimentTaskService.JobView job, PackingDecoderType decoder)
            throws IOException {
        ExperimentDtos.Request request = tasks.requestOf(job.id());
        long budgetValue = request.budgetMode() == SolveDtos.BudgetMode.EVALUATIONS
                ? (request.maxEvaluations() == null ? 0 : request.maxEvaluations())
                : (request.maxMilliseconds() == null ? 0 : request.maxMilliseconds());
        List<String> algorithms = request.searchAlgorithms().stream().map(AlgorithmId::name).toList();
        List<String> seeds = request.seeds().stream().map(String::valueOf).toList();
        String text = """
                experiment=%s
                generatedAtUtc=%s
                projectVersion=1.0.0
                experimentId=%s
                instanceIds=%s
                algorithms=%s
                decoder=%s
                rotationAllowed=%s
                seeds=%s
                budgetKind=%s
                budgetValue=%d
                parallelism=%d
                totalTasks=%d
                completedTasks=%d
                javaVersion=%s
                os=%s
                """.formatted(
                job.name(), Instant.now(), job.id(), request.instanceIds(), algorithms, decoder,
                request.rotationAllowed(), seeds, request.budgetMode().name(), budgetValue,
                request.parallelism(), job.total(), countResults(job),
                System.getProperty("java.version"), System.getProperty("os.name"));
        Files.writeString(directory.resolve("experiment-metadata.properties"), text, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- 下载

    /**
     * 解析一个待下载的产物路径，三层校验全部通过才返回。任何一层不通过都报 404 而不是 400：
     * 对客户端而言，「这个名字没有对应的产物」与「这个名字非法」应当无法区分，
     * 否则就成了一个探测存储目录结构的接口。
     */
    public Path resolveForDownload(String id, String name) {
        // 第二层先跑：它是纯字符串判断，不碰磁盘，能在任何 I/O 之前把明显的逃逸挡掉。
        //
        // 注意这里挡的是「上跳与另一套分隔符」，而不是所有斜杠。核心层生成的产物本来就分
        // layouts/ 与 convergence/ 两级子目录（例如 convergence/gcut1.svg），一刀切禁掉
        // 斜杠会把服务自己列出来的清单全部拒之门外。允许的斜杠路径由下面第一层负责授权：
        // 名字必须逐字出现在服务端自己扫出来的清单里，而清单是相对产物根目录枚举出来的，
        // 结构上不可能含 ".." 或反斜杠。
        // 控制字符必须先查：含 NUL 的字符串连 Path.of 都过不去，会抛 InvalidPathException
        // 而不是我们的 404——一个「名字里有不可见字符」的请求就变成 500 了。
        if (name == null || name.isBlank() || containsControl(name)
                || name.contains("..") || name.contains("\\") || name.startsWith("/")
                || Path.of(name).isAbsolute()) {
            throw notFound(id, name);
        }
        // 第一层：必须命中服务端自己扫出来的清单。名字在这里只是查表的钥匙，不是路径。
        boolean listed = list(id).stream().anyMatch(artifact -> artifact.name().equals(name));
        if (!listed) throw notFound(id, name);

        // 第三层：归一化兜底。前面两层将来被改松时，这一层仍能挡住逃逸。
        Path root = artifactDirectory(id).toAbsolutePath().normalize();
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw notFound(id, name);
        return file;
    }

    private static boolean containsControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return true;
        }
        return false;
    }

    private static ApiException notFound(String id, String name) {
        return new ApiException(HttpStatus.NOT_FOUND, "EXPERIMENT_ARTIFACT_NOT_FOUND",
                "不存在的产物：" + (name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "")));
    }

    // ---------------------------------------------------------------- 适配

    /**
     * 把 Web 的「单格结果 + 布局数据」拼成核心层的 {@link RunObservation}。
     *
     * <p>核心结构里有几样 Web 侧压根没有的东西，按下面的方式处理，而不是硬造一个假值：
     * <ul>
     *   <li>{@code iterations}：单次求解没有记录迭代次数，只能填 0。CSV 里那一列因此是 0，
     *       但评价次数列是真的，且报告明确以评价次数为准。</li>
     *   <li>{@code maxRectsBestStatistics}：留空，由紧凑构造器兜底成「不可用」，
     *       报告里的 MaxRects-BEST 组成明细整块跳过，不会显示一堆空行。</li>
     *   <li>{@code validationErrors}：Web 单格结果只存了合法性布尔值，不存错误明细，
     *       非法的格子在这里补一句说明，免得 CSV 里「非法」却看不出为什么。</li>
     * </ul>
     */
    private List<RunObservation> observations(ExperimentTaskService.JobView job) {
        ExperimentDtos.Request request = tasks.requestOf(job.id());
        ExperimentDtos.CellLayout[] layouts = tasks.layoutsOf(job.id());
        ExperimentTaskService.Cell[] cells = cellsOf(request);
        List<RunObservation> runs = new ArrayList<>();
        for (int index = 0; index < job.total() && index < cells.length; index++) {
            ExperimentDtos.ResultRow row = job.results()[index];
            if (row == null) continue;
            runs.add(observation(job, request, cells[index], row, layouts[index]));
        }
        return runs;
    }

    private RunObservation observation(
            ExperimentTaskService.JobView job, ExperimentDtos.Request request,
            ExperimentTaskService.Cell cell, ExperimentDtos.ResultRow row,
            ExperimentDtos.CellLayout layout) {
        Instance instance = instances.require(cell.instanceId());
        List<Placement> placements = layout == null
                ? List.of()
                : layout.placements().stream().map(placement -> toPlacement(instance, placement)).toList();
        Solution solution = new Solution(cell.algorithm().name(), placements,
                row.elapsedMilliseconds() * 1_000_000L);
        List<ConvergencePoint> trace = layout == null
                ? List.of()
                : layout.convergence().stream()
                        .map(point -> new ConvergencePoint(
                                point.step(), point.evaluations(), point.bestPackedArea()))
                        .toList();
        String errors = row.legal() ? "" : "该格子在 Web 求解路径下未通过合法性校验";
        return new RunObservation(
                request.name(), request.budgetMode().name(),
                request.budgetMode() == SolveDtos.BudgetMode.EVALUATIONS
                        ? (request.maxEvaluations() == null ? 0 : request.maxEvaluations())
                        : (request.maxMilliseconds() == null ? 0 : request.maxMilliseconds()),
                cell.algorithm(), instance, repetitionOf(request, cell), cell.seed(), solution,
                row.legal(), errors, row.evaluations(), 0L,
                MaxRectsBestStatistics.empty(), trace);
    }

    /**
     * 用实例里的零件把 Web 的放置记录还原成核心 {@link Placement}。
     *
     * <p>Web 的 {@code Placement} 是扁平的（带原始宽高与是否旋转），核心的要求是拿到
     * {@link Item} 本身。这里按零件编号回填而不是新造一个 Item：核心的合法性校验与面积统计
     * 都以 {@code item.area()} 为准，新造一个尺寸略有出入的 Item 会让「Web 下载的图」
     * 与「命令行跑的图」在统计上对不上。
     */
    private static Placement toPlacement(Instance instance, SolveDtos.Placement placement) {
        for (Item item : instance.items()) {
            if (item.id() == placement.itemId()) {
                return new Placement(item, placement.x(), placement.y(), placement.rotated());
            }
        }
        // 找不到零件说明持久化的布局与实例已经对不上（例如实例文件被换过）。
        // 用记录里的原始尺寸补一个，让产物仍能生成，而不是整个任务报错。
        return new Placement(
                new Item(placement.itemId(),
                        Math.max(1, placement.originalWidth()), Math.max(1, placement.originalHeight())),
                placement.x(), placement.y(), placement.rotated());
    }

    /**
     * 与 {@link ExperimentTaskService} 展开任务清单时的口径一致：实例 × 搜索器 × 解码器 × 种子，
     * 下标即结果数组的位置——这是「第几个结果行属于哪一个组合」的唯一依据，展开顺序错一处，
     * 产出的每一行都会配错实例与算法。
     */
    private static ExperimentTaskService.Cell[] cellsOf(ExperimentDtos.Request request) {
        List<ExperimentTaskService.Cell> cells = new ArrayList<>();
        int index = 0;
        for (String instanceId : request.instanceIds()) {
            for (AlgorithmId algorithm : request.searchAlgorithms()) {
                for (PackingDecoderType decoder : request.decoders()) {
                    for (long seed : request.seeds()) {
                        cells.add(new ExperimentTaskService.Cell(index++, instanceId, algorithm, decoder,
                                seed, instanceId + " · " + algorithm.name() + " × " + decoder.name()));
                    }
                }
            }
        }
        return cells.toArray(ExperimentTaskService.Cell[]::new);
    }

    /**
     * 种子在请求里的位次，就是核心层的 {@code repetition}（同一次实验的第几次重复）。
     * 刻意不按「种子值排序后的位次」算：种子列表允许重复，按值排序会把两次不同的运行
     * 归成同一次重复，报告里的标准差与并列判定都会失真。
     */
    private static int repetitionOf(ExperimentDtos.Request request, ExperimentTaskService.Cell cell) {
        List<Long> seeds = request.seeds();
        for (int index = 0; index < seeds.size(); index++) {
            if (seeds.get(index) == cell.seed()) {
                return index;
            }
        }
        return 0;
    }

    /**
     * 报告的解码器口径。一次 Web 实验可以混用多个解码器，而核心报告只有一个
     * {@code decoderType} 参数，用来决定要不要计算「相对 Skyline 基线」那一节。
     * 只有全部格子都用同一个解码器时才敢填它；混用时填 {@code null}，报告会走
     * 「无法归因到单一解码器」的兜底分支，而不是错误地把搜索收益算到某一个解码器头上。
     */
    private static PackingDecoderType decoderOf(ExperimentTaskService.JobView job) {
        Set<PackingDecoderType> used = new java.util.LinkedHashSet<>();
        for (ExperimentDtos.ResultRow row : job.results()) {
            if (row != null) used.add(parseDecoder(row.decoder()));
        }
        return used.size() == 1 ? used.iterator().next() : null;
    }

    private static PackingDecoderType parseDecoder(String name) {
        try {
            return PackingDecoderType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return PackingDecoderType.SKYLINE;
        }
    }

    // ---------------------------------------------------------------- 分析视图

    /**
     * 一个任务的**分析数据**：把标量结果、单格轨迹与实例几何一起交给前端，供热力图、
     * 排名、箱线图与收敛曲线对比使用。
     *
     * <p>与产物生成的关系：这是同一条数据的<b>实时</b>出口，产物是它的<b>离线归档</b>。
     * 两者共用同一套适配逻辑（{@link #observations}），因此「页面上看到的曲线」与
     * 「下载到的 CSV」不会对不上——那是最难查的一类分叉。
     *
     * <p>但分析数据<b>不要求任务终态</b>：进度中的任务同样可以看热力图与排名，
     * 只是缺的格子为空。这与产物生成的 409 是刻意的差别：产物是一次成型的快照，
     * 半途生成会给出误导性的报告；而分析视图本来就是「随结果陆续到达而更新」的。
     */
    public ExperimentDtos.Analysis analysis(String id) {
        ExperimentTaskService.JobView job = tasks.jobView(id);
        ExperimentDtos.Request request = tasks.requestOf(job.id());
        ExperimentDtos.CellLayout[] layouts = tasks.layoutsOf(job.id());
        List<ExperimentTaskService.Cell> cells = List.of(cellsOf(request));

        List<ExperimentDtos.ResultRow> rows = new ArrayList<>();
        List<ExperimentDtos.AnalysisCell> points = new ArrayList<>();
        for (int index = 0; index < cells.size(); index++) {
            ExperimentTaskService.Cell cell = cells.get(index);
            ExperimentDtos.ResultRow row = index < job.total() ? job.results()[index] : null;
            if (row == null) continue;
            rows.add(row);
            ExperimentDtos.CellLayout layout = index < layouts.length ? layouts[index] : null;
            boolean truncated = layout != null && layout.convergence().size() > MAX_ANALYSIS_POINTS;
            List<SolveDtos.SearchPoint> curve = layout == null
                    ? List.of()
                    : downsampled(layout.convergence(), truncated);
            points.add(new ExperimentDtos.AnalysisCell(
                    index, cell.instanceId(), cell.algorithm().name(), cell.decoder().name(), cell.seed(),
                    row.legal(), row.utilization(), row.packedArea(), row.elapsedMilliseconds(),
                    row.evaluations(), curve, truncated));
        }

        Map<String, ExperimentDtos.AnalysisInstance> instances = new LinkedHashMap<>();
        for (String instanceId : request.instanceIds()) {
            Instance instance = instancesOrNull(instanceId);
            if (instance == null) continue;
            instances.put(instanceId, new ExperimentDtos.AnalysisInstance(
                    instanceId, instance.name(), instance.binWidth(), instance.binHeight(),
                    instance.items().size()));
        }
        return new ExperimentDtos.Analysis(job.id(), request.name(), job.status(), job.total(),
                countResults(job), request.rotationAllowed(), request.budgetMode().name(),
                rows, points, instances);
    }

    /**
     * 把长轨迹按步长抽样，并<b>始终保留最后一个点</b>。
     *
     * <p>抽样的理由：轨迹是「每代一个点」，20 万次评价的实验能攒出十几万个点，原样塞进
     * JSON 是几十 MB，前端画不动、网络也白扛。抽到两千点以内，曲线的形状与终点都看不出来差别。
     *
     * <p>末点必须留：收敛曲线的终点就是这条轨迹的结论（「最终跑到多少」），
     * 被抽样规则顺手丢掉的话，图上最后一段会停在一个中途值上。
     */
    private static List<SolveDtos.SearchPoint> downsampled(List<SolveDtos.SearchPoint> trace, boolean truncated) {
        if (!truncated) return trace;
        int step = (int) Math.ceil((double) trace.size() / MAX_ANALYSIS_POINTS);
        List<SolveDtos.SearchPoint> sampled = new ArrayList<>(MAX_ANALYSIS_POINTS + 1);
        for (int index = 0; index < trace.size(); index += step) {
            sampled.add(trace.get(index));
        }
        SolveDtos.SearchPoint last = trace.get(trace.size() - 1);
        if (!sampled.get(sampled.size() - 1).equals(last)) sampled.add(last);
        return sampled;
    }

    private Instance instancesOrNull(String instanceId) {
        try {
            return instances.require(instanceId);
        } catch (RuntimeException exception) {
            LOGGER.warn("分析视图跳过未知实例：{}", instanceId);
            return null;
        }
    }

    // ---------------------------------------------------------------- 路径

    private Path taskRoot(String id) {
        return storeRoot.resolve(id).normalize();
    }

    private Path artifactDirectory(String id) {
        if (id == null || id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw notFound(id, null);
        }
        return taskRoot(id).resolve(ARTIFACT_DIRECTORY);
    }

    private static int countResults(ExperimentTaskService.JobView job) {
        int count = 0;
        for (ExperimentDtos.ResultRow row : job.results()) if (row != null) count++;
        return count;
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            LOGGER.debug("临时产物目录未能完全清理：{}", directory, exception);
        }
    }

    /** 下载响应的内容类型。按扩展名判定，不认识的一律当二进制流，避免浏览器内联执行。 */
    public static String contentType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return "text/csv; charset=UTF-8";
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".properties")) return "text/plain; charset=UTF-8";
        return "application/octet-stream";
    }

    /** 产物文件的下载名：只取文件名部分，避免把目录结构带进 Content-Disposition。 */
    public static String downloadName(String artifactName) {
        return Optional.ofNullable(Path.of(artifactName).getFileName())
                .map(Path::toString)
                .orElse("artifact");
    }

    /** 保留给测试与诊断：产物目录的绝对路径（不存在时也返回，调用方自行判断）。 */
    Path artifactDirectoryForTest(String id) {
        return artifactDirectory(id);
    }

    /** 便于把「有哪些产物」映射成「有哪些名字」的白名单。 */
    Map<String, Long> manifest(String id) {
        Map<String, Long> manifest = new LinkedHashMap<>();
        for (ExperimentDtos.Artifact artifact : list(id)) {
            manifest.put(artifact.name(), artifact.sizeBytes());
        }
        return manifest;
    }
}
