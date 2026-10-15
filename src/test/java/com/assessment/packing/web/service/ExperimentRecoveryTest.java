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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 实验任务的跨进程恢复。
 *
 * <p>「跨进程」在测试里的做法是：用同一个临时存储目录先后建两个服务实例，
 * 第一个跑一半后直接 {@code close()}（等价于进程被杀掉，没有任何优雅收尾），
 * 第二个在构造时从该目录恢复状态。不污染仓库，也不依赖真实的进程重启。
 */
final class ExperimentRecoveryTest {
    @TempDir
    Path storeDirectory;

    private ExperimentTaskService service;

    @AfterEach
    void stop() {
        if (service != null) service.close();
    }

    /**
     * 重启时正在跑的实验转为「已取消」，已算完的格子原样保留。
     *
     * <p>这是恢复规则里最需要解释的一条：进程已经没了，没有任何线程在推进这个任务，
     * 把它继续标成 RUNNING 是撒谎；而自动续跑则会在运维重启时静默拉起重活。
     * 转成 CANCELLED 后用户能一眼看到「停在哪」，并且可以自己决定要不要重试。
     */
    @Test
    void runningExperimentBecomesCancelledOnRestartKeepingFinishedCells() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(3_000), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        awaitUntil("已有格子落盘", () -> persistedCellCount(created.id()) >= 1, 20);
        // 模拟进程被杀：直接关掉线程池，不给任何任务收尾的机会。
        first.close();

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task restored = service.require(created.id());

        assertEquals("CANCELLED", restored.status(), "重启时在跑的实验应转为已取消");
        assertNotNull(restored.completedAt(), "转为终态必须带上结束时间");
        assertTrue(restored.completedTasks() >= 1,
                "已经算完的格子必须保留，实际=" + restored.completedTasks());
        assertNotNull(restored.error(), "应说明为什么变成已取消，否则用户会以为是自己取消的");
        assertTrue(restored.error().contains("重启"), "原因应当提到重启，实际=" + restored.error());
        // 恢复出来的结果必须仍然逐条可见，不能只剩一个进度数字。
        assertEquals(restored.completedTasks(), restored.results().size(),
                "进度数字与明细行数必须一致");
    }

    /**
     * 恢复后重试只补缺口：已经落盘的格子不得重算。
     *
     * <p>用一个会计数的求解器来证明「没被重复执行」，而不是只看最终进度——
     * 只看进度的话，全部重算一遍也能得到同样的 8/8。
     */
    @Test
    void retryAfterRestartOnlyExecutesTheMissingCells() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(3_000), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        awaitUntil("已有两格落盘", () -> persistedCellCount(created.id()) >= 2, 20);
        first.close();

        AtomicInteger executed = new AtomicInteger();
        service = new ExperimentTaskService((request, cell) -> {
            executed.incrementAndGet();
            return ExperimentTaskService.CellOutcome.scalar(row(cell, 100));
        }, instances(), new ExperimentTaskStore(storeDirectory));

        ExperimentDtos.Task restored = service.require(created.id());
        int alreadyDone = restored.completedTasks();
        assertTrue(alreadyDone >= 2 && alreadyDone < restored.totalTasks(),
                "重启后应当停在中间，实际=" + alreadyDone + "/" + restored.totalTasks());

        ExperimentDtos.Task retried = service.retry(created.id());
        assertEquals(alreadyDone, retried.completedTasks(), "重试不得丢弃重启前算完的结果");

        ExperimentDtos.Task settled = awaitTerminal(created.id(), 30);
        assertEquals("COMPLETED", settled.status());
        assertEquals(settled.totalTasks(), settled.completedTasks());
        assertEquals(settled.totalTasks() - alreadyDone, executed.get(),
                "重试只应执行缺口部分的格子，已完成的不许重算");
    }

    /**
     * 排队中就被重启打断的实验自动重新排队。
     *
     * <p>与 RUNNING 不同，这类任务一个格子都没跑过，恢复它没有任何「静默拉起重活」的问题，
     * 因此自动继续，对用户完全透明。
     */
    @Test
    void queuedExperimentIsRescheduledAfterRestart() throws IOException {
        // 先占满 dispatcher 的两个位置，让目标任务只能停在队列里。
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(5_000), instances(), new ExperimentTaskStore(storeDirectory));
        first.create(retryRequest());
        first.create(retryRequest());
        ExperimentDtos.Task queued = first.create(retryRequest());
        assertEquals("QUEUED", queued.status(), "dispatcher 已被占满，第三个实验应停在队列里");
        first.close();

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task restored = service.require(queued.id());
        assertFalse(restored.status().equals("CANCELLED"),
                "排队中的实验重启后不应被取消，它本来就还没开始");

        ExperimentDtos.Task settled = awaitTerminal(queued.id(), 30);
        assertEquals("COMPLETED", settled.status(), "重新排队后应当自己跑完");
    }

    /** 终态任务重启后原样恢复：状态、进度、明细、结束时间都对得上。 */
    @Test
    void completedExperimentSurvivesRestartUnchanged() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        ExperimentDtos.Task settled = awaitTerminal(first, created.id(), 30);
        assertEquals("COMPLETED", settled.status());
        first.close();

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task restored = service.require(created.id());

        assertEquals("COMPLETED", restored.status());
        assertEquals(settled.totalTasks(), restored.completedTasks());
        assertEquals(settled.bestUtilization(), restored.bestUtilization());
        assertEquals(settled.averageUtilization(), restored.averageUtilization());
        assertEquals(settled.legalResults(), restored.legalResults());
        assertEquals(settled.createdAt(), restored.createdAt(), "创建时间必须原样保留");
        assertEquals(settled.completedAt(), restored.completedAt(), "结束时间必须原样保留");
    }

    /** 列表接口在重启后仍然返回磁盘上的全部任务，且按创建时间倒序。 */
    @Test
    void listReturnsAllRestoredTasksNewestFirst() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        String older = first.create(namedRequest("较早的实验")).id();
        String newer = first.create(namedRequest("较晚的实验")).id();
        first.close();

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        List<ExperimentDtos.Task> tasks = service.list();
        assertEquals(2, tasks.size(), "两个任务都应恢复");
        assertEquals(newer, tasks.get(0).id(), "列表应按创建时间倒序");
        assertEquals(older, tasks.get(1).id());
    }

    /** 取消标记要跨重启保留：否则重启会把用户明确叫停的任务重新跑起来。 */
    @Test
    void cancelRequestSurvivesRestart() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(3_000), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        awaitUntil("实验开始运行", () -> !first.require(created.id()).status().equals("QUEUED"), 20);
        first.cancel(created.id());
        first.close();

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task restored = service.require(created.id());
        assertEquals("CANCELLED", restored.status());
        assertTrue(restored.completedTasks() < restored.totalTasks(),
                "取消时还有缺口，恢复后应看得出缺口");
    }

    /** 存储里混进坏文件时，坏的那一个被跳过，其余任务照常恢复，服务不会因此起不来。 */
    @Test
    void corruptTaskDirectoryIsSkippedInsteadOfBreakingStartup() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task healthy = first.create(namedRequest("完好的实验"));
        awaitTerminal(first, healthy.id(), 30);
        first.close();

        Path broken = storeDirectory.resolve("broken-task");
        Files.createDirectories(broken);
        Files.write(broken.resolve("meta.bin"), new byte[] {0, 0});

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        List<ExperimentDtos.Task> tasks = service.list();
        assertEquals(1, tasks.size(), "坏任务应被跳过，好任务必须还在");
        assertEquals(healthy.id(), tasks.get(0).id());
    }

    /** 格子文件损坏只丢那一个格子：任务其余部分照常恢复，缺的那格会被重试补上。 */
    @Test
    void corruptCellFileOnlyLosesThatCell() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        awaitTerminal(first, created.id(), 30);
        first.close();

        Files.write(storeDirectory.resolve(created.id()).resolve("cells").resolve("3.bin"),
                new byte[] {1, 2, 3, 4});

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task restored = service.require(created.id());
        assertEquals(restored.totalTasks() - 1, restored.completedTasks(),
                "只有被损坏的那一格应当丢失");
        assertTrue(restored.results().stream().allMatch(ExperimentDtos.ResultRow::legal),
                "其余格子的结果必须完好");
    }

    /** 临时文件不得被当成格子结果读进来，否则重启后进度会凭空多出一格。 */
    @Test
    void temporaryFilesAreNotCountedAsResults() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        awaitTerminal(first, created.id(), 30);
        first.close();

        // 模拟一次写盘写到一半就被打断：只剩临时文件，没有正式文件。
        Files.write(storeDirectory.resolve(created.id()).resolve("cells").resolve("99.bin.tmp"),
                new byte[] {0, 0, 0, 1});

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task restored = service.require(created.id());
        assertEquals(restored.totalTasks(), restored.completedTasks(),
                "残留的临时文件不应被计入结果");
    }

    /**
     * 事件序号跨重启继续递增。
     *
     * <p>如果重启后序号从 1 重新开始，一个持有旧序号的客户端重连时会收到一堆「序号更小」的事件，
     * 补发逻辑会因为「不大于我已知的序号」而把它们全部丢弃，进度就永远停在旧值上。
     */
    @Test
    void eventSequenceContinuesAcrossRestart() throws IOException {
        // 跑到一半就被「杀掉」，留下缺口——重试才有事可做，才能产出新一轮的进度事件。
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(3_000), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        // 等待的条件是「序号已经落盘」，而不是「已经算完两格」。格子结果是工作线程写进
        // 结果数组的，比提交线程在 collect() 里的那次落盘要早；盯着完成数就关服，会撞上
        // 「数组里有结果、磁盘上序号还是 0」的瞬间，测试自己变成随机的。
        awaitUntil("事件序号落盘", () -> lastPersistedSequenceOrZero(created.id()) > 0, 20);
        first.close();

        long beforeRestart = lastPersistedSequence(created.id());
        assertTrue(beforeRestart > 0, "重启前必须已经有事件序号落盘");

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        service.retry(created.id());
        ExperimentDtos.Task settled = awaitTerminal(created.id(), 30);
        assertEquals("COMPLETED", settled.status());

        long afterRestart = lastPersistedSequence(created.id());
        assertTrue(afterRestart > beforeRestart,
                "重启后的事件序号必须继续递增，实际 " + beforeRestart + " -> " + afterRestart);
    }

    /** 客户端可以在任务结束后才连上来，此时必须立刻收到一个终止事件，而不是被挂住。 */
    @Test
    void subscribingToARestoredTerminalTaskEmitsCompleteImmediately() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(retryRequest());
        awaitTerminal(first, created.id(), 30);
        first.close();

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        var emitter = service.events(created.id(), null);
        assertNotNull(emitter, "订阅已恢复的终态任务必须立刻返回一个可用的连接");
    }

    // ------------------------------------------------------------------ 被杀时机演练
    //
    // 以下三条模拟「进程恰好死在两次原子替换之间」。真实的 SIGKILL 无法在测试里精确卡点，
    // 但原子替换的失败形态是确定的、有限的：要么目标还在（旧的完整版本），要么只剩暂存文件。
    // 把这两种残骸直接摆到磁盘上，比试图精确控制进程死亡时刻更可靠，也更可重复。

    /**
     * 「有合法 meta.bin + 一个垃圾暂存文件」：必须读到合法的那份。
     *
     * <p>这正是「写了一半就被杀」留下的现场——暂存文件是半截的，正式文件还没被替换。
     * 若读取端因为目录里有垃圾而放弃整个任务，用户会莫名其妙地丢掉一条记录。
     */
    @Test
    void garbageTemporaryFileAlongsideAValidMetaKeepsTheTaskReadable() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task created = first.create(namedRequest("被杀在替换之间"));
        awaitTerminal(first, created.id(), 30);
        first.close();

        Path taskDirectory = storeDirectory.resolve(created.id());
        // 一次被中断的元数据写入留下的残骸：只有版本号，后面什么都没有。
        Files.write(taskDirectory.resolve(".tmp-1728000000000"), new byte[] {0, 0, 0, 1});
        Files.write(taskDirectory.resolve("cells").resolve(".tmp-1728000000001"), new byte[] {0, 0, 0, 1});

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task restored = service.require(created.id());
        assertEquals("COMPLETED", restored.status(), "垃圾暂存文件不该影响任务恢复");
        assertEquals(restored.totalTasks(), restored.completedTasks(), "标量结果必须全部读回");
    }

    /**
     * 「只有暂存文件、没有 meta.bin」：任务被**跳过**，而不是让启动失败。
     *
     * <p>这个目录名不在 id 白名单里，它只会出现在存储根下。启动过程必须容得下这种残骸——
     * 一个永远写不完的临时目录不该让整个服务起不来。
     */
    @Test
    void taskDirectoryWithoutMetaIsSkippedInsteadOfBreakingStartup() throws IOException {
        ExperimentTaskService first = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        ExperimentDtos.Task healthy = first.create(namedRequest("完好的实验"));
        awaitTerminal(first, healthy.id(), 30);
        first.close();

        // 死在「写了临时文件、还没替换成 meta.bin」那一刻：目录里只有 .tmp-*。
        Path halfWritten = storeDirectory.resolve("half-written-task");
        Files.createDirectories(halfWritten.resolve("cells"));
        Files.write(halfWritten.resolve(".tmp-1728000000002"), new byte[] {0, 0, 0, 1, 1, 2});
        Files.write(halfWritten.resolve("cells").resolve("0.bin"), new byte[] {0, 0, 0, 1});

        service = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(storeDirectory));
        List<ExperimentDtos.Task> tasks = service.list();
        assertEquals(1, tasks.size(), "写不完的临时目录应被跳过，而不是让服务起不来");
        assertEquals(healthy.id(), tasks.get(0).id());
    }

    /**
     * 目录只读：写入失败只记日志，任务本身继续推进。
     *
     * <p>存储故障不应该让用户的实验「失败」——结果还在内存里，任务仍然在跑，
     * 只是这一次落盘没成功。这一条直接对着 {@code persist()} 的容错设计。
     *
     * <p>macOS 下 root 会绕过文件权限位，因此以 root 运行时这条断言没有意义，直接跳过。
     */
    @Test
    void readOnlyStoreDirectoryDoesNotFailTheExperiment() throws IOException {
        assumeFalse(isRoot(), "root 会绕过文件权限，只读目录造不出来");
        Path readOnly = storeDirectory.resolve("read-only");
        Files.createDirectories(readOnly);
        setWritable(readOnly, false);
        ExperimentTaskService readOnlyService = null;
        try {
            readOnlyService = new ExperimentTaskService(
                    slowSolver(0), instances(), new ExperimentTaskStore(readOnly));
            ExperimentDtos.Task created = readOnlyService.create(namedRequest("只读目录"));
            ExperimentDtos.Task settled = awaitTerminal(readOnlyService, created.id(), 30);

            assertEquals("COMPLETED", settled.status(),
                    "落盘失败不该把任务判死：结果仍在内存里，任务照常跑完");
            assertEquals(settled.totalTasks(), settled.completedTasks());
        } catch (UncheckedIOException exception) {
            throw new AssertionError("写入只读目录应当只记日志，不应把异常抛给调用方", exception);
        } finally {
            if (readOnlyService != null) readOnlyService.close();
            // 恢复写权限，否则 @TempDir 清理时会失败。
            setWritable(readOnly, true);
        }
    }

    /**
     * 目录只读时重试也不能崩：服务在 {@code require} / {@code retry} 路径上会读盘，
     * 而读是允许的；真正会被拒的是写入，它必须被容错吞掉。
     */
    @Test
    void retryOnAReadOnlyStoreStillSettles() throws IOException {
        assumeFalse(isRoot(), "root 会绕过文件权限，只读目录造不出来");
        Path readOnly = storeDirectory.resolve("read-only-retry");
        Files.createDirectories(readOnly);
        setWritable(readOnly, false);
        ExperimentTaskService readOnlyService = new ExperimentTaskService(
                slowSolver(0), instances(), new ExperimentTaskStore(readOnly));
        try {
            ExperimentDtos.Task created = readOnlyService.create(namedRequest("只读目录重试"));
            awaitTerminal(readOnlyService, created.id(), 30);

            // 已完成、没有缺口，重试本就该被拒——这里要确认的是它拒绝得干净（409 语义），
            // 而不是在写盘失败的噪音里抛出别的异常。
            ExperimentTaskService target = readOnlyService;
            assertEquals("EXPERIMENT_NOTHING_TO_RETRY",
                    assertThrows(ApiException.class, () -> target.retry(created.id())).code());
        } finally {
            readOnlyService.close();
            setWritable(readOnly, true);
        }
    }

    private static boolean isRoot() {
        return "root".equals(System.getProperty("user.name"));
    }

    private static void setWritable(Path directory, boolean writable) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
            Set<PosixFilePermission> updated = EnumSet.copyOf(permissions);
            if (writable) {
                updated.addAll(EnumSet.of(
                        PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            } else {
                updated.removeAll(EnumSet.of(
                        PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE,
                        PosixFilePermission.OTHERS_WRITE));
                updated.add(PosixFilePermission.OWNER_EXECUTE);
            }
            Files.setPosixFilePermissions(directory, updated);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "当前文件系统不支持 POSIX 权限，跳过只读演练：" + exception.getMessage());
        }
    }

    /** 直接读磁盘上的序号。走存储层自己的解析器，避免在这里重复一份格式知识。 */
    private long lastPersistedSequence(String taskId) {
        return new ExperimentTaskStore(storeDirectory).loadAll().stream()
                .filter(stored -> stored.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("磁盘上找不到任务：" + taskId))
                .lastEventId();
    }

    /** 轮询用：任务还没落盘时返回 0，而不是把断言异常抛进等待循环里。 */
    private long lastPersistedSequenceOrZero(String taskId) {
        return new ExperimentTaskStore(storeDirectory).loadAll().stream()
                .filter(stored -> stored.id().equals(taskId))
                .findFirst()
                .map(ExperimentTaskStore.StoredTask::lastEventId)
                .orElse(0L);
    }

    /**
     * 磁盘上已经躺着的格子数。等待重启时机必须看这个，而不是内存里的完成数：
     * 工作线程先把结果写进数组、提交线程稍后才把它写进文件，两者之间有一个真实的窗口。
     */
    private int persistedCellCount(String taskId) {
        Path cells = storeDirectory.resolve(taskId).resolve("cells");
        if (!Files.isDirectory(cells)) return 0;
        try (var files = Files.list(cells)) {
            return (int) files.filter(path -> path.getFileName().toString().endsWith(".bin")).count();
        } catch (IOException exception) {
            return 0;
        }
    }

    private static ExperimentTaskService.CellSolver slowSolver(long millis) {
        return (request, cell) -> {
            if (millis > 0) sleep(millis);
            return ExperimentTaskService.CellOutcome.scalar(row(cell, 100));
        };
    }

    private static InstanceCatalogService instances() {
        return new InstanceCatalogService("gcut01");
    }

    private static ExperimentDtos.ResultRow row(ExperimentTaskService.Cell cell, long area) {
        return new ExperimentDtos.ResultRow(cell.instanceId(), cell.algorithm().name(),
                cell.decoder().name(), cell.seed(), true, area / 1000.0, area, 1L, area);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** 8 个格子、并行度 2：2 实例 × 1 搜索器 × 1 解码器 × 4 种子。 */
    private static ExperimentDtos.Request retryRequest() {
        return namedRequest("恢复实验");
    }

    private static ExperimentDtos.Request namedRequest(String name) {
        return new ExperimentDtos.Request(
                name, List.of("gcut1", "gcut5"), List.of(AlgorithmId.GA_TABU),
                List.of(PackingDecoderType.SKYLINE), List.of(1L, 2L, 3L, 4L),
                true, SolveDtos.BudgetMode.EVALUATIONS, 100L, null, 2);
    }

    private ExperimentDtos.Task awaitTerminal(String id, int seconds) {
        return awaitTerminal(service, id, seconds);
    }

    private static ExperimentDtos.Task awaitTerminal(
            ExperimentTaskService target, String id, int seconds) {
        boolean done = awaitUntilQuietly("实验进入终态",
                () -> isTerminal(target.require(id).status()), seconds);
        assertTrue(done, "实验未在 " + seconds + " 秒内结束");
        return target.require(id);
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
}
