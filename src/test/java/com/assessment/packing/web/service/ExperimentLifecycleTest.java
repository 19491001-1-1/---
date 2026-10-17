package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.dto.SolveDtos;
import com.assessment.packing.web.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实验任务的生命周期：取消、重试与状态机边界。
 *
 * <p>这些用例直接用真实实例目录（{@code gcut01}）驱动服务本身，不走 MockMvc：
 * 要验证的是任务「跑到一半」时的状态，只有让真实求解跑起来才测得到。
 * 实例 gcut11 有 1000 个零件，配合高评价预算足以让任务稳定地停在运行中。
 */
final class ExperimentLifecycleTest {
    /** 足够大，保证任务不会在测试来得及取消之前跑完。 */
    private static final long SLOW_BUDGET = 5_000_000L;

    private ExperimentTaskService service;
    /** 每个用例独占一个临时目录，绝不写进仓库。 */
    @TempDir
    Path storeDirectory;

    @AfterEach
    void stop() {
        if (service != null) service.close();
    }

    private ExperimentTaskService service() {
        // 实例目录相对项目根目录解析，Maven 测试的工作目录即项目根。
        InstanceCatalogService instances = new InstanceCatalogService("gcut01");
        return new ExperimentTaskService(
                new ExperimentTaskService.ServiceCellSolver(new SolveService(instances)), instances,
                new ExperimentTaskStore(storeDirectory));
    }

    @Test
    void cancelStopsRunningExperimentAndRecordsTerminalState() {
        CountDownLatch cellStarted = new CountDownLatch(1);
        service = blockingService(cellStarted);
        ExperimentDtos.Task created = service.create(request("取消实验", 1, SLOW_BUDGET));
        // create() 返回时后台线程可能已经开跑，QUEUED 与 RUNNING 都是合法初始状态。
        assertTrue(created.status().equals("QUEUED") || created.status().equals("RUNNING"),
                "新建任务的初始状态应为排队中或运行中，实际=" + created.status());

        awaitUntil("求解格子实际开始运行", () -> cellStarted.getCount() == 0, 20);
        service.cancel(created.id());

        ExperimentDtos.Task settled = awaitTerminal(created.id(), 60);
        assertEquals("CANCELLED", settled.status());
        assertTrue(settled.completedAt() != null, "取消后必须记录结束时间");
    }

    @Test
    void cancelledExperimentKeepsOnlyValidPartialResults() {
        CountDownLatch cellStarted = new CountDownLatch(1);
        service = blockingService(cellStarted);
        ExperimentDtos.Task created = service.create(request("取消结果校验", 1, SLOW_BUDGET));
        awaitUntil("求解格子实际开始运行", () -> cellStarted.getCount() == 0, 20);
        service.cancel(created.id());

        ExperimentDtos.Task settled = awaitTerminal(created.id(), 60);
        assertEquals("CANCELLED", settled.status());
        // 取消保留的是已经算完的结果，它们必须都是合法解——取消绝不能产出半个布局。
        assertTrue(settled.results().stream().allMatch(ExperimentDtos.ResultRow::legal),
                "取消保留的每个结果都必须是合法解");
        assertEquals(settled.completedTasks(), settled.results().size(),
                "进度数字与明细行数必须一致");
    }

    @Test
    void runningExperimentMustBeCancelledBeforeDeletion() {
        CountDownLatch cellStarted = new CountDownLatch(1);
        service = blockingService(cellStarted);
        ExperimentDtos.Task created = service.create(request("删除运行中实验", 1, SLOW_BUDGET));
        awaitUntil("求解格子实际开始运行", () -> cellStarted.getCount() == 0, 20);

        ApiException exception = assertThrows(ApiException.class, () -> service.delete(created.id()));
        assertEquals("EXPERIMENT_DELETE_RUNNING", exception.code());
        assertTrue(Files.exists(storeDirectory.resolve(created.id())), "被拒绝的删除不得破坏任务目录");
    }

    @Test
    void deletionRemovesTerminalRecordAndPersistentDirectory() {
        service = service();
        ExperimentDtos.Task created = service.create(request("可删除实验", 1, 10));
        awaitTerminal(created.id(), 30);
        assertTrue(Files.exists(storeDirectory.resolve(created.id())));

        service.delete(created.id());

        assertTrue(service.list().stream().noneMatch(task -> task.id().equals(created.id())));
        assertTrue(Files.notExists(storeDirectory.resolve(created.id())), "元数据、结果和产物应随任务目录一起删除");
        ApiException exception = assertThrows(ApiException.class, () -> service.require(created.id()));
        assertEquals("EXPERIMENT_NOT_FOUND", exception.code());
    }

    /**
     * 重试只补没算出来的格子，已算完的结果原样保留。
     *
     * <p>8 个格子、并行度 2：前 2 格秒回，其余 6 格要 3 秒。因此取消后必定只完成了一部分
     * ——在途的格子会被中断并交出「当前最优解」（这与真实求解器一致，是合法结果，应当计分），
     * 但排在后面、压根没提交的格子则没有任何结果。剩下这个缺口，正是重试要补的东西。
     */
    @Test
    void retryResumesOnlyTheMissingCells() {
        InstanceCatalogService instances = new InstanceCatalogService("gcut01");
        service = new ExperimentTaskService((request, cell) -> {
            if (cell.index() >= 2) sleep(3_000);
            return ExperimentTaskService.CellOutcome.scalar(row(cell, Math.max(1, 400 - cell.index())));
        }, instances, new ExperimentTaskStore(storeDirectory));

        ExperimentDtos.Task created = service.create(retryRequest());
        awaitUntil("前两格算完", () -> service.require(created.id()).completedTasks() >= 2, 20);
        service.cancel(created.id());

        ExperimentDtos.Task cancelled = awaitTerminal(created.id(), 30);
        assertEquals("CANCELLED", cancelled.status());
        int partial = cancelled.completedTasks();
        assertTrue(partial >= 2 && partial < cancelled.totalTasks(),
                "取消时应当只完成了一部分，实际=" + partial + "/" + cancelled.totalTasks());

        // 重试：只补没算出来的格子，已算完的结果原样保留。
        ExperimentDtos.Task retried = service.retry(created.id());
        // 与 create() 同理：返回快照时后台可能已经开跑，QUEUED 与 RUNNING 都合法。
        assertTrue(retried.status().equals("QUEUED") || retried.status().equals("RUNNING"),
                "重试后任务应重新排队或直接开跑，实际=" + retried.status());
        assertEquals(partial, retried.completedTasks(), "重试不得丢弃已经算完的结果");
        assertTrue(retried.results().stream().allMatch(ExperimentDtos.ResultRow::legal),
                "重试期间已算完的结果必须保留且合法");

        ExperimentDtos.Task settled = awaitTerminal(created.id(), 60);
        assertEquals("COMPLETED", settled.status(), "补齐后应回到完成态");
        assertEquals(settled.totalTasks(), settled.completedTasks(), "重试后必须补齐全部格子");
    }

    @Test
    void retryIsRejectedWhileStillRunning() {
        CountDownLatch cellStarted = new CountDownLatch(1);
        service = blockingService(cellStarted);
        ExperimentDtos.Task created = service.create(request("运行中重试", 1, SLOW_BUDGET));
        awaitUntil("求解格子实际开始运行", () -> cellStarted.getCount() == 0, 20);

        ApiException error = assertThrows(ApiException.class, () -> service.retry(created.id()));
        assertEquals("EXPERIMENT_ALREADY_RUNNING", error.code());
        assertEquals(409, error.status().value());
        service.cancel(created.id());
        awaitTerminal(created.id(), 60);
    }

    @Test
    void cancelIsRejectedForTerminalExperiment() {
        service = service();
        ExperimentDtos.Task created = service.create(request("快速完成", 1, 20L));
        ExperimentDtos.Task settled = awaitTerminal(created.id(), 120);
        assertEquals("COMPLETED", settled.status());

        ApiException error = assertThrows(ApiException.class, () -> service.cancel(created.id()));
        assertEquals("EXPERIMENT_NOT_RUNNING", error.code());
        assertEquals(409, error.status().value());
    }

    @Test
    void retryIsRejectedWhenEveryCellAlreadyHasAResult() {
        service = service();
        ExperimentDtos.Task created = service.create(request("无可重试", 1, 20L));
        ExperimentDtos.Task settled = awaitTerminal(created.id(), 120);
        assertEquals("COMPLETED", settled.status());

        ApiException error = assertThrows(ApiException.class, () -> service.retry(created.id()));
        assertEquals("EXPERIMENT_NOTHING_TO_RETRY", error.code());
        assertEquals(409, error.status().value());
    }

    @Test
    void cancelAndRetryReportNotFoundForUnknownTask() {
        service = service();
        assertEquals("EXPERIMENT_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.cancel("no-such-task")).code());
        assertEquals("EXPERIMENT_NOT_FOUND",
                assertThrows(ApiException.class, () -> service.retry("no-such-task")).code());
    }

    /**
     * 进度必须跟上真实的完成情况，而不是被提交顺序拖住。
     *
     * <p>真实算法在 QUICK 配置下全都只要几十毫秒，来不及观察中间状态，所以这里注入一个
     * 耗时可控的求解器：<b>第 0 个格子故意最慢</b>（800ms），其余快得多（80ms）。
     *
     * <p>如果进度仍按 Future 下标顺序收集，它会一直停在 0，直到第 0 个格子算完——
     * 也就是整批里最慢的那个收尾之前，进度数字纹丝不动。按完成顺序收集则会在
     * 慢格子还在跑时就先把后面几个快格子报出来。这正是原来「进度显示滞后」的成因。
     */
    @Test
    void progressAdvancesWhileTheSlowestCellIsStillRunning() {
        InstanceCatalogService instances = new InstanceCatalogService("gcut01");
        service = new ExperimentTaskService((request, cell) -> {
            // index 0 是最慢的那一格；其余快 10 倍，足以在它结束前先完成。
            sleep(cell.index() == 0 ? 800 : 80);
            return ExperimentTaskService.CellOutcome.scalar(row(cell, Math.max(1, 500 - cell.index())));
        }, instances, new ExperimentTaskStore(storeDirectory));

        ExperimentDtos.Task created = service.create(matrixRequest());
        awaitUntil("实验开始运行", () -> !service.require(created.id()).status().equals("QUEUED"), 20);

        boolean advanced = awaitUntilQuietly("进度越过最慢的格子", () -> {
            ExperimentDtos.Task task = service.require(created.id());
            return task.status().equals("RUNNING") && task.completedTasks() > 0;
        }, 60);
        assertTrue(advanced, "最慢的格子尚未结束时，更快的格子就应该已经报出进度");

        ExperimentDtos.Task settled = awaitTerminal(created.id(), 60);
        assertEquals("COMPLETED", settled.status());
        assertEquals(settled.totalTasks(), settled.completedTasks(), "最终必须补齐全部格子");
    }

    /**
     * 取消要快：请求取消后，正在跑的格子必须尽快收尾，而不是把当前这次评价跑完。
     *
     * <p>注入的求解器每个格子要 5 秒；若取消没有真正打断它，取消后仍要等满 5 秒。
     * 这里留 2 秒的宽限，足以区分「被打断」与「跑完了」。
     */
    @Test
    void cancelInterruptsCellsInsteadOfWaitingForThemToFinish() {
        InstanceCatalogService instances = new InstanceCatalogService("gcut01");
        service = new ExperimentTaskService((request, cell) -> {
            sleep(5_000);
            return ExperimentTaskService.CellOutcome.scalar(row(cell, 100));
        }, instances, new ExperimentTaskStore(storeDirectory));

        ExperimentDtos.Task created = service.create(matrixRequest());
        awaitUntil("实验开始运行", () -> !service.require(created.id()).status().equals("QUEUED"), 20);

        long start = System.nanoTime();
        service.cancel(created.id());
        ExperimentDtos.Task settled = awaitTerminal(created.id(), 20);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals("CANCELLED", settled.status());
        assertTrue(elapsedMillis < 2_000,
                "取消应在远短于单格耗时(5000ms)的时间内生效，实际=" + elapsedMillis + "ms");
    }

    @Test
    void previewCountsTheFullMatrixAndWarnsAboutTimeBudget() {
        service = service();
        ExperimentDtos.Preview preview = service.preview(new ExperimentDtos.Request(
                "矩阵预览", List.of("gcut1"), List.of(AlgorithmId.ILS, AlgorithmId.TABU_MOVE),
                List.of(PackingDecoderType.SKYLINE, PackingDecoderType.MAXRECTS), List.of(1L, 2L, 3L),
                true, SolveDtos.BudgetMode.EVALUATIONS, 1_000L, (Long) null, 2));

        assertEquals(12, preview.taskCount(), "1 实例 × 2 搜索器 × 2 解码器 × 3 种子 = 12");
        assertEquals(1, preview.instanceCount());
        assertEquals(2, preview.searchAlgorithmCount());
        assertEquals(2, preview.decoderCount());
        assertEquals(3, preview.seedCount());
        assertTrue(preview.estimatedWork().contains("12,000"), "预计工作量应为 12 × 1000 次评价");
    }

    /**
     * 8 个格子、并行度 2：2 实例 × 1 搜索器 × 1 解码器 × 4 种子。
     * 格子数明显多于并行度，取消时才会留下「一部分算完、一部分根本没提交」的真实缺口。
     */
    private static ExperimentDtos.Request retryRequest() {
        return new ExperimentDtos.Request(
                "重试实验", List.of("gcut1", "gcut5"), List.of(AlgorithmId.GA_TABU),
                List.of(PackingDecoderType.SKYLINE), List.of(1L, 2L, 3L, 4L),
                true, SolveDtos.BudgetMode.EVALUATIONS, Long.valueOf(100L), (Long) null, 2);
    }

    /** 8 个格子、并行度 8：2 实例 × 1 搜索器 × 1 解码器 × 4 种子。 */
    private static ExperimentDtos.Request matrixRequest() {
        return new ExperimentDtos.Request(
                "矩阵实验", List.of("gcut1", "gcut5"), List.of(AlgorithmId.GA_TABU),
                List.of(PackingDecoderType.SKYLINE), List.of(1L, 2L, 3L, 4L),
                true, SolveDtos.BudgetMode.EVALUATIONS, Long.valueOf(100L), (Long) null, 8);
    }

    /** 构造一个结果行；{@code utilization} 取遍不同值，避免快照里出现除零或并列极值。 */
    private static ExperimentDtos.ResultRow row(ExperimentTaskService.Cell cell, long area) {
        return new ExperimentDtos.ResultRow(cell.instanceId(), cell.algorithm().name(),
                cell.decoder().name(), cell.seed(), area > 0, area / 1000.0, area, 1L, area);
    }

    /** 可被中断的等待：模拟「求解器在循环里检查取消标记后立刻收尾」。 */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            // 与真实求解器一致：收到中断就放弃当前这次评价，把中断状态留给上层清理。
            Thread.currentThread().interrupt();
        }
    }

    /** 明确握手的慢求解器：测试线程看到计数归零时，格子一定仍在执行，消除机器快慢带来的竞态。 */
    private ExperimentTaskService blockingService(CountDownLatch cellStarted) {
        InstanceCatalogService instances = new InstanceCatalogService("gcut01");
        return new ExperimentTaskService((request, cell) -> {
            cellStarted.countDown();
            sleep(30_000);
            return ExperimentTaskService.CellOutcome.scalar(row(cell, 100));
        }, instances, new ExperimentTaskStore(storeDirectory));
    }

    private static ExperimentDtos.Request request(String name, int parallelism, long budget) {
        return new ExperimentDtos.Request(
                name, List.of("gcut11"), List.of(AlgorithmId.ILS),
                List.of(PackingDecoderType.SKYLINE), List.of(20261012L),
                true, SolveDtos.BudgetMode.EVALUATIONS, Long.valueOf(budget), (Long) null, parallelism);
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

    /** 轮询等待，返回是否在期限内满足条件。用墙钟轮询，不引入额外同步原语。 */
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
