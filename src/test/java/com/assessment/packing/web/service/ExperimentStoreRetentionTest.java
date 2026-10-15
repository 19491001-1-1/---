package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.dto.SolveDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存储目录的保留策略与容量上限。
 *
 * <p>两条约束各自都能独立地搞坏东西，必须一起钉住：
 * <ul>
 *   <li><b>只删终态。</b>在跑或排队的任务目录正在被写入，删它等于一边写一边拆房子；
 *       更何况它承载的是用户正在等的结果。</li>
 *   <li><b>必须在恢复之后清理。</b>顺序反过来，就会把刚要恢复的任务当成「旧的」删掉，
 *       用户重启一次丢一批记录。</li>
 * </ul>
 *
 * <p>用例直接往磁盘上摆 {@code meta.bin}，不真跑实验：保留策略只认磁盘上的状态，
 * 这样构造出的时间顺序与状态组合是确定的，不会因为真实任务跑得太快而变得随机。
 */
final class ExperimentStoreRetentionTest {
    @TempDir
    Path storeDirectory;

    private ExperimentTaskService service;

    @AfterEach
    void stop() {
        if (service != null) service.close();
    }

    /** 超出上限时删掉最旧的终态任务，最新的那批原样保留。 */
    @Test
    void prunesTheOldestTerminalTasksBeyondTheLimit() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(storeDirectory);
        Instant base = Instant.parse("2026-10-14T00:00:00Z");
        for (int index = 1; index <= 5; index++) {
            store.saveMeta(stored("task-" + index, "COMPLETED", base.plusSeconds(index)));
        }

        service = new ExperimentTaskService(noopSolver(), instances(), store, 3);

        List<String> remaining = service.list().stream().map(ExperimentDtos.Task::id).toList();
        assertEquals(3, remaining.size(), "上限为 3 时应只剩 3 个任务，实际=" + remaining);
        assertFalse(remaining.contains("task-1"), "最旧的终态任务应当被删掉");
        assertFalse(remaining.contains("task-2"));
        assertTrue(remaining.containsAll(List.of("task-3", "task-4", "task-5")),
                "最新的任务必须保留，实际=" + remaining);
        assertFalse(Files.exists(storeDirectory.resolve("task-1")), "被回收的任务目录要真的从磁盘上消失");
        assertFalse(Files.exists(storeDirectory.resolve("task-2")));
    }

    /**
     * 排队中与运行中的任务永远不删——哪怕它们是最旧的、哪怕这样腾不出空间。
     *
     * <p>用阻塞的求解器把恢复出来的任务钉在「非终态」上，否则它们会在构造过程中就
     * 跑完并落盘成终态，测试变成随机的：清理恰好读到 QUEUED 就保住，读到 COMPLETED 就删掉。
     *
     * <p>另一层用意是钉住「候选只从保留窗口之后取」这条规则。这里最新的 task-5 是唯一的
     * 终态任务，若实现按「删到不超限为止」的思路去做，被删的就会是 task-5——
     * 既没腾出该腾的空间，还丢了一条刚跑完的记录。
     */
    @Test
    void neverPrunesQueuedOrRunningTasksEvenWhenTheyAreTheOldest() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(storeDirectory);
        Instant base = Instant.parse("2026-10-14T00:00:00Z");
        for (int index = 1; index <= 4; index++) {
            store.saveMeta(stored("task-" + index, "QUEUED", base.plusSeconds(index)));
        }
        store.saveMeta(stored("task-5", "COMPLETED", base.plusSeconds(5)));

        service = new ExperimentTaskService(blockingSolver(), instances(), store, 1);

        for (int index = 1; index <= 5; index++) {
            assertTrue(Files.exists(storeDirectory.resolve("task-" + index)),
                    "排队中或运行中的任务目录绝不能被保留策略删掉：task-" + index);
        }
        assertTrue(service.list().stream().anyMatch(task -> task.id().equals("task-5")),
                "最新的终态任务属于保留窗口，不该被删");
    }

    /** 上限以内的存储目录一个都不动，保留策略不该有副作用。 */
    @Test
    void leavesEverythingAloneWhenWithinTheLimit() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(storeDirectory);
        Instant base = Instant.parse("2026-10-14T00:00:00Z");
        for (int index = 1; index <= 3; index++) {
            store.saveMeta(stored("task-" + index, "CANCELLED", base.plusSeconds(index)));
        }

        service = new ExperimentTaskService(noopSolver(), instances(), store, 10);

        assertEquals(3, service.list().size());
        for (int index = 1; index <= 3; index++) {
            assertTrue(Files.exists(storeDirectory.resolve("task-" + index)));
        }
    }

    /** 上限为 0 或负数表示关闭保留策略，任何任务都不删。 */
    @Test
    void aNonPositiveLimitDisablesPruning() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(storeDirectory);
        Instant base = Instant.parse("2026-10-14T00:00:00Z");
        for (int index = 1; index <= 4; index++) {
            store.saveMeta(stored("task-" + index, "FAILED", base.plusSeconds(index)));
        }

        service = new ExperimentTaskService(noopSolver(), instances(), store, 0);

        assertEquals(4, service.list().size(), "上限为 0 应当关闭保留策略");
        for (int index = 1; index <= 4; index++) {
            assertTrue(Files.exists(storeDirectory.resolve("task-" + index)));
        }
    }

    /** 删除要容忍半删状态：目录不存在时也算删干净，不该抛异常。 */
    @Test
    void removingAMissingTaskDirectoryIsNotAnError() {
        ExperimentTaskStore store = new ExperimentTaskStore(storeDirectory);
        assertTrue(store.removeTask("never-existed"), "删一个不存在的目录应当算成功");
    }

    /** 递归删除：任务目录下的 meta、cells 与残留暂存文件都要一并清掉，不能只删顶层。 */
    @Test
    void removalDeletesTheWholeTree() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(storeDirectory);
        store.saveMeta(stored("task-1", "COMPLETED", Instant.now()));
        store.saveCell("task-1", 0, new ExperimentDtos.ResultRow(
                "gcut1", "ILS", "SKYLINE", 1L, true, 0.5, 100L, 1L, 1L));
        Path leftover = storeDirectory.resolve("task-1").resolve(".tmp-99");
        Files.write(leftover, new byte[] {1});

        assertTrue(store.removeTask("task-1"));
        assertFalse(Files.exists(storeDirectory.resolve("task-1")), "整棵目录树都必须被清掉");
    }

    private static ExperimentTaskStore.StoredTask stored(String id, String status, Instant createdAt) {
        return new ExperimentTaskStore.StoredTask(id, request(), status, createdAt,
                status.equals("QUEUED") ? null : createdAt, null, null, false, 1L);
    }

    /** 保留策略不关心求解，用一个立即返回的实现即可；任务本身仍会正常跑完。 */
    private static ExperimentTaskService.CellSolver noopSolver() {
        return (request, cell) -> ExperimentTaskService.CellOutcome.scalar(
                new ExperimentDtos.ResultRow(cell.instanceId(), cell.algorithm().name(),
                        cell.decoder().name(), cell.seed(), true, 0.5, 100L, 1L, 1L));
    }

    /**
     * 一个永远不返回的求解器：它把恢复出来的任务钉在 RUNNING 上，让「非终态不被回收」
     * 这条规则可以被确定地验证。收到中断就立刻退出，这样 {@code close()} 不会卡住。
     */
    private static ExperimentTaskService.CellSolver blockingSolver() {
        return (request, cell) -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return ExperimentTaskService.CellOutcome.scalar(
                    new ExperimentDtos.ResultRow(cell.instanceId(), cell.algorithm().name(),
                            cell.decoder().name(), cell.seed(), true, 0.5, 100L, 1L, 1L));
        };
    }

    private static InstanceCatalogService instances() {
        return new InstanceCatalogService("gcut01");
    }

    private static ExperimentDtos.Request request() {
        return new ExperimentDtos.Request(
                "保留策略实验", List.of("gcut1"), List.of(AlgorithmId.ILS),
                List.of(PackingDecoderType.SKYLINE), List.of(1L),
                true, SolveDtos.BudgetMode.EVALUATIONS, 10L, null, 1);
    }
}
