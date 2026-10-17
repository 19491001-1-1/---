package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.dto.SolveDtos;
import com.assessment.packing.web.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产物生成与安全下载。
 *
 * <p>这一层有两条必须钉死的约定，都是「一旦破了很难从上层发现」的类型：
 *
 * <ol>
 *   <li><b>下载名的三层校验缺一不可。</b>第一层白名单让客户端给的名字只能是一把查表的钥匙；
 *       第二层挡分隔符与上跳；第三层做归一化兜底。测试逐层各打一枪，确保将来改松任何一层都会红。</li>
 *   <li><b>report.html 里不能有死链。</b>核心层不生成 experiment-metadata.properties，
 *       但它的报告里有一条指向该文件的链接；不补写，报告点一下就 404。</li>
 * </ol>
 */
final class ExperimentArtifactServiceTest {
    @TempDir
    Path storeDirectory;

    private ExperimentTaskService service;

    @AfterEach
    void stop() {
        if (service != null) service.close();
    }

    private ExperimentArtifactService artifacts() {
        return new ExperimentArtifactService(service, instances(), storeDirectory.toString());
    }

    private static InstanceCatalogService instances() {
        return new InstanceCatalogService("gcut01");
    }

    /** 一个真实的终态实验：8 个格子，每个都带布局与收敛轨迹。 */
    private String completedExperiment() {
        service = new ExperimentTaskService((request, cell) -> layoutOutcome(cell),
                instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = service.create(request("产物实验"));
        ExperimentDtos.Task settled = awaitTerminal(created.id(), 60);
        assertEquals("COMPLETED", settled.status());
        return created.id();
    }

    /**
     * 生成产物必须落齐核心层的全部文件，并且补上 experiment-metadata.properties——
     * 报告里那条链接指向它，缺了就是死链。
     */
    @Test
    void generatesEveryArtifactAndFillsTheMetadataLink() throws IOException {
        String id = completedExperiment();

        List<ExperimentDtos.Artifact> produced = artifacts().generate(id);
        Set<String> names = produced.stream().map(ExperimentDtos.Artifact::name).collect(
                java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        assertTrue(names.containsAll(List.of("runs.csv", "summary.csv", "overall.csv",
                        "traces.csv", "algorithm-catalog.csv", "report.html", "instance-winners.csv",
                        "experiment-metadata.properties")),
                "核心产物必须齐备，实际=" + names);
        assertTrue(names.stream().anyMatch(name -> name.startsWith("layouts/")),
                "应当按实例与算法生成最佳排样 SVG，实际=" + names);
        assertTrue(names.stream().anyMatch(name -> name.startsWith("convergence/")),
                "应当生成收敛曲线 SVG，实际=" + names);

        // 报告里指向的两条相对链接必须都能落地，否则报告在浏览器里点一下就 404。
        Path directory = artifacts().artifactDirectoryForTest(id);
        assertTrue(Files.isRegularFile(directory.resolve("experiment-metadata.properties")),
                "report.html 链接的元数据文件必须存在");
        String report = Files.readString(directory.resolve("report.html"));
        assertTrue(report.contains("experiment-metadata.properties"));
        assertTrue(report.contains("逐实例最优配置"));
        String winners = Files.readString(directory.resolve("instance-winners.csv"));
        assertTrue(winners.startsWith("instance,algorithm,decoder,"));
        for (String name : names) {
            assertTrue(Files.isRegularFile(directory.resolve(name)), "清单里的每个名字都必须有文件：" + name);
        }
        // 清单里的文件都要有非零大小，空文件是「写了一半」的典型症状。
        assertTrue(produced.stream().allMatch(artifact -> artifact.sizeBytes() > 0),
                "产物不应出现空文件：" + produced);
    }

    /** 运行中的任务没有产物可生成：格子还在陆续到达，此刻的报告注定残缺，必须明确拒绝。 */
    @Test
    void refusesToGenerateWhileTheExperimentIsStillRunning() {
        service = new ExperimentTaskService((request, cell) -> {
            sleep(3_000);
            return ExperimentTaskService.CellOutcome.scalar(row(cell, 100));
        }, instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = service.create(request("还没跑完"));
        awaitUntil("实验开始运行", () -> !service.require(created.id()).status().equals("QUEUED"), 20);

        ApiException error = assertThrows(ApiException.class, () -> artifacts().generate(created.id()));
        assertEquals("EXPERIMENT_NOT_COMPLETED", error.code());
        assertEquals(409, error.status().value());

        // 清理：不让后台任务影响 @TempDir 删除。
        service.cancel(created.id());
        awaitTerminal(created.id(), 30);
    }

    /** 还没生成过产物时清单是空的，而不是 404——「尚未生成」与「任务不存在」必须能分开。 */
    @Test
    void emptyManifestIsNotAnError() {
        String id = completedExperiment();
        assertTrue(artifacts().list(id).isEmpty(), "尚未生成时清单应为空");

        assertEquals("EXPERIMENT_NOT_FOUND",
                assertThrows(ApiException.class, () -> artifacts().list("no-such-task")).code());
    }

    /** 每个产物名字都能解析成一个落在产物目录内的真实文件。 */
    @Test
    void everyListedArtifactResolvesInsideTheTaskDirectory() {
        String id = completedExperiment();
        ExperimentArtifactService artifacts = artifacts();
        Path root = artifacts.artifactDirectoryForTest(id).toAbsolutePath().normalize();

        for (ExperimentDtos.Artifact artifact : artifacts.generate(id)) {
            Path file = artifacts.resolveForDownload(id, artifact.name());
            assertTrue(file.startsWith(root), "解析结果必须落在产物目录内：" + file);
            assertTrue(Files.isRegularFile(file));
        }
    }

    /**
     * 三层校验各打一枪。对客户端一律 404：把「名字非法」和「文件不存在」区分开，
     * 等于把这个端点变成探测存储目录结构的工具。
     */
    @Test
    void downloadRejectsAnythingOutsideTheArtifactManifest() {
        String id = completedExperiment();
        ExperimentArtifactService artifacts = artifacts();
        artifacts.generate(id);

        // 第一层（白名单）：这些文件在磁盘上真实存在，但不在产物清单里，就不是可下载的产物。
        // 这是最要紧的一条——它把「客户端给的名字」和「磁盘上的路径」彻底解耦：
        // 任务元数据与单格结果都躺在同一个任务目录下，只能靠白名单把它们挡在下载之外。
        for (String name : List.of("meta.bin", "cells/0.bin", "cells/0.layout.bin", "nope.csv")) {
            assertEquals("EXPERIMENT_ARTIFACT_NOT_FOUND",
                    assertThrows(ApiException.class, () -> artifacts.resolveForDownload(id, name),
                            "不在清单里的名字必须被拒：" + name).code());
        }
        // 第二层（上跳与另一套分隔符）。注意这里**不能**一刀切禁掉斜杠：
        // 核心层产出的 convergence/gcut1.svg 本来就带一级子目录，禁掉斜杠会把服务
        // 自己列出来的清单全部拒之门外。斜杠路径的授权交给上面的白名单。
        for (String name : List.of("../meta.bin", "layouts/../../meta.bin", "a/b/../../../etc/passwd",
                "..\\meta.bin", "/etc/passwd", "", "   ", "layouts/\u0000.svg")) {
            assertEquals("EXPERIMENT_ARTIFACT_NOT_FOUND",
                    assertThrows(ApiException.class, () -> artifacts.resolveForDownload(id, name),
                            "含上跳、绝对路径或控制字符的名字必须被拒：" + name).code());
        }
    }

    /** 合法的嵌套产物名（带一级子目录）必须能正常下载，不能被「禁斜杠」误伤。 */
    @Test
    void nestedArtifactNamesStayDownloadable() {
        String id = completedExperiment();
        ExperimentArtifactService artifacts = artifacts();
        artifacts.generate(id);

        List<String> nested = artifacts.list(id).stream()
                .map(ExperimentDtos.Artifact::name)
                .filter(name -> name.contains("/"))
                .toList();
        assertFalse(nested.isEmpty(), "核心产物本就含 layouts/ 与 convergence/ 子目录");
        for (String name : nested) {
            Path file = artifacts.resolveForDownload(id, name);
            assertTrue(Files.isRegularFile(file), "嵌套产物名必须能解析成真实文件：" + name);
        }
    }

    /** 下载文件名与内容类型：CSV/HTML/SVG 各归各类，不认识的一律当二进制流，不在浏览器里内联渲染。 */
    @Test
    void downloadNameAndContentTypeFollowTheExtension() {
        assertEquals("text/csv; charset=UTF-8", ExperimentArtifactService.contentType("runs.csv"));
        assertEquals("text/html; charset=UTF-8", ExperimentArtifactService.contentType("report.html"));
        assertEquals("image/svg+xml", ExperimentArtifactService.contentType("layouts/gcut1/ils.svg"));
        assertEquals("application/octet-stream", ExperimentArtifactService.contentType("mystery.bin"));
        // 下载名只取文件名，避免把目录结构写进 Content-Disposition。
        assertEquals("ils.svg", ExperimentArtifactService.downloadName("layouts/gcut1/ils.svg"));
    }

    /** 重复生成必须整体替换而不是叠加：产物目录里不能留下上一次的残骸。 */
    @Test
    void regeneratingReplacesTheDirectoryInsteadOfAccumulating() throws IOException {
        String id = completedExperiment();
        ExperimentArtifactService artifacts = artifacts();
        List<ExperimentDtos.Artifact> first = artifacts.generate(id);

        // 塞一个上一次生成可能留下的陈旧文件，再重新生成一次。
        Path directory = artifacts.artifactDirectoryForTest(id);
        Files.writeString(directory.resolve("stale-from-previous-run.html"), "陈旧");
        assertTrue(artifacts.list(id).stream()
                .anyMatch(artifact -> artifact.name().equals("stale-from-previous-run.html")));

        List<ExperimentDtos.Artifact> second = artifacts.generate(id);

        assertFalse(second.stream().anyMatch(artifact -> artifact.name().equals("stale-from-previous-run.html")),
                "整体替换后不该留下上一次的文件");
        assertEquals(first.size(), second.size(), "两次生成的产物清单应当一致");
        // 换手用的中转目录也必须清干净，否则存储目录会只增不减。
        try (var files = Files.list(directory.getParent())) {
            assertTrue(files.map(path -> path.getFileName().toString())
                            .noneMatch(name -> name.contains(".building-") || name.contains(".retired-")),
                    "整体替换的中转目录必须被清理");
        }
    }

    /**
     * 分析视图：把标量结果、收敛轨迹与实例元数据凑成一个响应。
     *
     * <p>这里要钉的是「三份数据对齐」——前端的热力图、排名、收敛对比全靠它一次拿齐，
     * 任何一个字段错位都会表现成图上的张冠李戴，而不是一个报错。
     */
    @Test
    void analysisJoinsResultsTracesAndInstanceMetadata() {
        String id = completedExperiment();
        ExperimentDtos.Analysis analysis = artifacts().analysis(id);

        assertEquals("COMPLETED", analysis.status());
        assertEquals(8, analysis.totalTasks());
        assertEquals(8, analysis.completedTasks());
        assertEquals(8, analysis.results().size());
        assertEquals(8, analysis.cells().size());
        assertEquals(8, analysis.cells().stream()
                .filter(cell -> cell.convergence().size() == 2).count(), "每格都该带上那条两点的轨迹");

        for (int index = 0; index < 8; index++) {
            ExperimentDtos.AnalysisCell cell = analysis.cells().get(index);
            ExperimentDtos.ResultRow row = analysis.results().get(index);
            // 下标必须与 results 逐位对齐：前端是按下标与布局、实例做关联的。
            assertEquals(index, cell.index());
            assertEquals(row.instanceId(), cell.instanceId());
            assertEquals(row.searchAlgorithm(), cell.searchAlgorithm());
            // 铺设顺序是 实例 → 搜索器 → 解码器 → 种子，因此种子每 4 格循环一次。
            assertEquals((index % 4) + 1L, cell.seed(), "种子按 request 的笛卡尔积顺序铺开");
            assertFalse(cell.curveTruncated(), "两点轨迹远未到抽样阈值");
        }

        // 实例元数据是热力图的轴标签来源；缺了实例名，图上就只剩下一串 id。
        assertEquals(Set.of("gcut1", "gcut5"), analysis.instances().keySet());
        ExperimentDtos.AnalysisInstance instance = analysis.instances().get("gcut1");
        assertEquals("gcut1", instance.id());
        assertTrue(instance.itemCount() > 0);
        assertTrue(instance.binWidth() > 0 && instance.binHeight() > 0);
        assertFalse(instance.name().isBlank());
    }

    /**
     * 分析数据**不要求任务终态**：这正是与产物生成（409）刻意分开的地方。
     * 产物是一次成型的快照，半途生成会给出误导性的报告；分析视图本来就是随结果陆续到达而更新的。
     */
    @Test
    void analysisIsAvailableWhileTheExperimentIsStillRunning() {
        service = new ExperimentTaskService((request, cell) -> {
            sleep(2_000);
            return layoutOutcome(cell);
        }, instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = service.create(request("边跑边看"));
        awaitUntil("实验开始运行", () -> !service.require(created.id()).status().equals("QUEUED"), 20);

        ExperimentDtos.Analysis analysis = artifacts().analysis(created.id());
        assertFalse(isTerminal(analysis.status()), "此时任务不该已经结束");
        // 结果与格子都还没到齐，但响应必须成形——前端是拿它当「当前进度」渲染的。
        assertEquals(analysis.totalTasks(), 8, "总格数由请求决定，与跑到哪儿无关");
        assertTrue(analysis.cells().size() <= analysis.totalTasks());
        // 实例元数据此时就该齐：轴的取值范围不依赖结果。
        assertEquals(Set.of("gcut1", "gcut5"), analysis.instances().keySet());

        service.cancel(created.id());
        awaitTerminal(created.id(), 30);
    }

    /** 未知任务仍要报 404，不能因为「分析不做终态校验」就把存在性校验一起放松了。 */
    @Test
    void analysisStillRejectsUnknownTask() {
        service = new ExperimentTaskService((request, cell) -> layoutOutcome(cell),
                instances(), new ExperimentTaskStore(storeDirectory));
        assertEquals("EXPERIMENT_NOT_FOUND",
                assertThrows(ApiException.class, () -> artifacts().analysis("no-such-task")).code());
    }

    /**
     * 长轨迹必须被抽样，否则一个 20 万次评价的实验能在响应里塞进几十 MB。
     *
     * <p>关键约定是**最后一个点必须留下**：收敛曲线的终点就是这条轨迹的结论，
     * 被抽样规则顺手丢掉的话，图上最后一段会停在一个中途值上，看起来像「没跑到」。
     */
    @Test
    void analysisDownsamplesLongTracesButKeepsTheLastPoint() {
        int length = 5_001;
        service = new ExperimentTaskService((request, cell) -> {
            List<SolveDtos.SearchPoint> trace = new ArrayList<>(length);
            for (int step = 0; step < length; step++) {
                trace.add(new SolveDtos.SearchPoint(step, step, 1_000L + step));
            }
            List<SolveDtos.Placement> placements = List.of(
                    new SolveDtos.Placement(1, 100, 50, 0, 0, 100, 50, false, 5_000L));
            return new ExperimentTaskService.CellOutcome(
                    row(cell, 7_400), new ExperimentDtos.CellLayout(placements, trace));
        }, instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = service.create(oneCellRequest("超长轨迹"));
        awaitTerminal(created.id(), 60);

        ExperimentDtos.AnalysisCell cell = artifacts().analysis(created.id()).cells().get(0);
        assertTrue(cell.curveTruncated(), "轨迹被抽样时必须如实标记，否则前端会把折线画成「完整历史」");
        assertTrue(cell.convergence().size() < length, "抽样后点数必须显著下降");
        assertTrue(cell.convergence().size() <= 2_001, "抽样后不应超过阈值一条");
        // 首末两点是折线的两端，任何抽样规则都不该动它们。
        assertEquals(0, cell.convergence().get(0).step(), "首点保留");
        SolveDtos.SearchPoint lastPoint = cell.convergence().get(cell.convergence().size() - 1);
        assertEquals(length - 1, lastPoint.step(), "末点必须保留");
        assertEquals(1_000L + length - 1, lastPoint.bestPackedArea());
        // 抽样只能变稀，不能乱序或凭空造点。
        for (int index = 1; index < cell.convergence().size(); index++) {
            assertTrue(cell.convergence().get(index).step() > cell.convergence().get(index - 1).step(),
                    "抽样后仍须严格递增");
        }
    }

    /** 一个格子的布局数据：放两个零件，带一条收敛轨迹。 */
    private static ExperimentTaskService.CellOutcome layoutOutcome(ExperimentTaskService.Cell cell) {
        List<SolveDtos.Placement> placements = List.of(
                new SolveDtos.Placement(1, 100, 50, 0, 0, 100, 50, false, 5_000L),
                new SolveDtos.Placement(2, 60, 40, 100, 0, 40, 60, true, 2_400L));
        List<SolveDtos.SearchPoint> convergence = List.of(
                new SolveDtos.SearchPoint(0, 1, 5_000L),
                new SolveDtos.SearchPoint(1, 5, 7_400L));
        return new ExperimentTaskService.CellOutcome(
                row(cell, 7_400), new ExperimentDtos.CellLayout(placements, convergence));
    }

    private static ExperimentDtos.ResultRow row(ExperimentTaskService.Cell cell, long area) {
        return new ExperimentDtos.ResultRow(cell.instanceId(), cell.algorithm().name(),
                cell.decoder().name(), cell.seed(), true, area / 1000.0, area, 1L, area);
    }

    /** 2 实例 × 1 搜索器 × 1 解码器 × 4 种子 = 8 个格子。 */
    private static ExperimentDtos.Request request(String name) {
        return new ExperimentDtos.Request(
                name, List.of("gcut1", "gcut5"), List.of(AlgorithmId.ILS),
                List.of(PackingDecoderType.SKYLINE), List.of(1L, 2L, 3L, 4L),
                true, SolveDtos.BudgetMode.EVALUATIONS, 100L, (Long) null, 2);
    }

    /** 只跑一个格子：用来把「轨迹很长」这件事单独放大，不被 8 个格子的等待时间稀释。 */
    private static ExperimentDtos.Request oneCellRequest(String name) {
        return new ExperimentDtos.Request(
                name, List.of("gcut1"), List.of(AlgorithmId.ILS),
                List.of(PackingDecoderType.SKYLINE), List.of(1L),
                true, SolveDtos.BudgetMode.EVALUATIONS, 100L, (Long) null, 1);
    }

    private ExperimentDtos.Task awaitTerminal(String id, int seconds) {
        boolean done = awaitUntilQuietly("实验进入终态",
                () -> isTerminal(service.require(id).status()), seconds);
        assertTrue(done, "实验未在 " + seconds + " 秒内结束");
        return service.require(id);
    }

    private static boolean isTerminal(String status) {
        return status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED");
    }

    private void awaitUntil(String what, BooleanSupplier condition, int seconds) {
        assertTrue(awaitUntilQuietly(what, condition, seconds), "等待超时：" + what);
    }

    private static boolean awaitUntilQuietly(String what, BooleanSupplier condition, int seconds) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(seconds));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
