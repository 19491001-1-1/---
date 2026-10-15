package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.dto.SolveDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持久化格式的往返与健壮性。
 *
 * <p>这一层的价值不在覆盖率，而在于把「写入端与读取端必须逐字段对称」这条约定钉死：
 * 位置式二进制格式里，任何一处的条件读写不一致都会让后续所有字段整体错位，
 * 而错位的症状往往表现为「任务莫名其妙不见了」，极难从上层排查。
 */
final class ExperimentTaskStoreTest {
    @TempDir
    Path directory;

    @Test
    void metaRoundTripsEveryField() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        ExperimentDtos.Request request = request();
        Instant created = Instant.parse("2026-10-14T08:30:15.123456789Z");
        Instant started = Instant.parse("2026-10-14T08:30:16.987654321Z");
        ExperimentTaskStore.StoredTask original = new ExperimentTaskStore.StoredTask(
                "task-1", request, "RUNNING", created, started, null, null, true, 42L);

        store.saveMeta(original);
        List<ExperimentTaskStore.StoredTask> loaded = store.loadAll();

        assertEquals(1, loaded.size());
        ExperimentTaskStore.StoredTask restored = loaded.get(0);
        assertEquals(original.id(), restored.id());
        assertEquals("RUNNING", restored.status());
        // 时间戳必须精确到最后一位：毫秒截断会让客户端的运行时长凭空空出几毫秒，
        // 也会让「恢复后状态应当与重启前一致」这条断言直接失败。
        assertEquals(created, restored.createdAt());
        assertEquals(started, restored.startedAt());
        assertNull(restored.completedAt());
        assertNull(restored.error());
        assertTrue(restored.cancelRequested());
        assertEquals(42L, restored.lastEventId());
        assertEquals(request, restored.request(), "请求的每个字段都必须原样往返");
    }

    @Test
    void nullBudgetFieldsRoundTripWithoutShiftingTheStream() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        // 时间预算：maxEvaluations 为 null。可空字段最容易在读取端被「短路跳过」，
        // 从而让后面的字段整体错位 8 字节。
        ExperimentDtos.Request timed = new ExperimentDtos.Request(
                "时间预算", List.of("gcut1"), List.of(AlgorithmId.ILS),
                List.of(PackingDecoderType.SKYLINE), List.of(7L),
                false, SolveDtos.BudgetMode.TIME_MS, null, 1_500L, 1);
        store.saveMeta(new ExperimentTaskStore.StoredTask(
                "task-timed", timed, "QUEUED", Instant.now(), null, null, null, false, 0L));

        ExperimentTaskStore.StoredTask restored = store.loadAll().get(0);
        assertEquals(timed, restored.request());
        assertNull(restored.request().maxEvaluations());
        assertEquals(1_500L, restored.request().maxMilliseconds());
    }

    @Test
    void cellResultsKeepTheirIndexRegardlessOfWriteOrder() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        // 刻意乱序写入：并行完成顺序与下标顺序无关，读取时必须按下标各归各位。
        store.saveCell("task-1", 2, row("gcut5", 300));
        store.saveCell("task-1", 0, row("gcut1", 100));
        store.saveCell("task-1", 1, row("gcut3", 200));

        ExperimentDtos.ResultRow[] rows = store.loadCells("task-1", 4).orElseThrow();

        assertEquals("gcut1", rows[0].instanceId());
        assertEquals("gcut3", rows[1].instanceId());
        assertEquals("gcut5", rows[2].instanceId());
        assertNull(rows[3], "没写过的格子必须是 null，它就是「待补的缺口」");
    }

    @Test
    void missingCellDirectoryYieldsAnEmptyResultSet() {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        ExperimentDtos.ResultRow[] rows = store.loadCells("never-written", 3).orElseThrow();
        assertEquals(3, rows.length);
        for (ExperimentDtos.ResultRow row : rows) assertNull(row);
    }

    @Test
    void taskIdCannotEscapeTheStoreRoot() {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        assertThrows(IllegalArgumentException.class,
                () -> store.saveCell("../../etc/passwd", 0, row("gcut1", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> store.saveCell("a/b", 0, row("gcut1", 1)));
    }

    @Test
    void temporaryFilesAreIgnored() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        store.saveCell("task-1", 0, row("gcut1", 100));
        // 写盘写到一半被打断的残骸：不能被当成结果读进来，否则进度会凭空多一格。
        Files.write(directory.resolve("task-1").resolve("cells").resolve("1.bin.tmp"),
                new byte[] {0, 0, 0, 1, 9, 9});

        ExperimentDtos.ResultRow[] rows = store.loadCells("task-1", 2).orElseThrow();
        assertEquals("gcut1", rows[0].instanceId());
        assertNull(rows[1]);
    }

    @Test
    void corruptedTaskDirectoryIsSkippedNotFatal() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        store.saveMeta(new ExperimentTaskStore.StoredTask(
                "healthy", request(), "COMPLETED", Instant.now(), null, Instant.now(), null, false, 3L));
        Path broken = directory.resolve("broken");
        Files.createDirectories(broken);
        Files.write(broken.resolve("meta.bin"), new byte[] {9, 9, 9});

        List<ExperimentTaskStore.StoredTask> loaded = store.loadAll();

        assertEquals(1, loaded.size(), "坏任务跳过，好任务必须还在");
        assertEquals("healthy", loaded.get(0).id());
    }

    @Test
    void emptyStoreLoadsNothing() {
        assertTrue(new ExperimentTaskStore(directory.resolve("does-not-exist")).loadAll().isEmpty());
    }

    /** 保存同一条元数据两次必须覆盖而不是追加，否则重启后会读到两份互相矛盾的状态。 */
    @Test
    void savingMetaTwiceOverwritesInPlace() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        ExperimentTaskStore.StoredTask first = new ExperimentTaskStore.StoredTask(
                "task-1", request(), "RUNNING", Instant.now(), null, null, null, false, 1L);
        store.saveMeta(first);
        store.saveMeta(new ExperimentTaskStore.StoredTask(
                "task-1", request(), "COMPLETED", first.createdAt(), first.startedAt(),
                Instant.now(), null, false, 9L));

        List<ExperimentTaskStore.StoredTask> loaded = store.loadAll();
        assertEquals(1, loaded.size(), "同一个任务只应有一条记录");
        assertEquals("COMPLETED", loaded.get(0).status());
        assertEquals(9L, loaded.get(0).lastEventId());
        assertEquals(List.of("meta.bin"), listing(directory.resolve("task-1")),
                "临时文件必须已被改名，目录里只该剩下 meta.bin");
    }

    /**
     * 同一个任务的元数据可能被两个调用者同时写入——服务里 {@code cancel()} 打取消标记、
     * 收集循环推进进度，两者本来就会并发。这条测试盯住的是「写入不能共用一个固定名字的
     * 暂存文件」：若共用了，后者的 {@code newOutputStream} 会先把前者正在写的文件截断，
     * 先 {@code move} 走的一方会让另一方撞上 {@code NoSuchFileException}；更坏的情况是
     * 先写的那个落地，后写的把状态覆盖回去，磁盘上就此留下一个过期的状态。
     *
     * <p>断言有三条：不许有异常逃出来、不许留下无人认领的暂存文件、最终文件必须能完整读回。
     */
    @Test
    void concurrentMetaWritesForTheSameTaskDoNotCollide() throws Exception {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        int writers = 4;
        int writesPerWriter = 60;
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            for (int writer = 0; writer < writers; writer++) {
                int id = writer;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int round = 0; round < writesPerWriter; round++) {
                            store.saveMeta(new ExperimentTaskStore.StoredTask(
                                    "task-1", request(), "RUNNING", Instant.now(), null, null,
                                    null, id % 2 == 0, id * 1_000L + round));
                        }
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发写入未在预期时间内结束");
        }

        assertTrue(failures.isEmpty(), "并发写入不应抛异常，实际=" + failures);
        assertEquals(List.of("meta.bin"), listing(directory.resolve("task-1")),
                "并发写入不应在目录里留下任何暂存文件");
        List<ExperimentTaskStore.StoredTask> loaded = store.loadAll();
        assertEquals(1, loaded.size(), "最终状态必须是一份完整可读的元数据");
        assertNotNull(loaded.get(0).request(), "读回来的请求不能是半截数据");
        assertTrue(loaded.get(0).lastEventId() >= 0, "读回来的序号必须是某个写者写下的值");
    }

    /**
     * 伴生布局文件与标量结果互不干扰：读标量时不许把 {@code <下标>.layout.bin} 读进来。
     *
     * <p>这是伴生方案里最容易踩的一处。过滤条件原本只排 {@code .tmp}，而
     * {@code 0.layout.bin} 同样以 {@code .bin} 结尾，且 {@code indexOf} 能从
     * {@code 0.layout.bin} 里抠出下标 0——于是一份布局数据会被当成标量结果解析。
     * 字节凑巧对得上时甚至不会抛异常，只留下一条「看起来正常、数值全错」的结果行，
     * 这种错误在记录页上完全看不出来。
     */
    @Test
    void layoutCompanionDoesNotPolluteTheScalarReader() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        ExperimentDtos.ResultRow original = row("gcut5", 300);
        store.saveCell("task-1", 0, original);
        store.saveLayout("task-1", 0, layout());

        ExperimentDtos.ResultRow[] rows = store.loadCells("task-1", 1).orElseThrow();
        assertEquals(original, rows[0], "标量结果必须原样读回，不能被伴生布局污染");
    }

    /** 伴生布局本身要能完整往返：放置列表与收敛轨迹逐字段对称。 */
    @Test
    void layoutCompanionRoundTripsEveryField() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        ExperimentDtos.CellLayout original = layout();

        store.saveLayout("task-1", 2, original);
        ExperimentDtos.CellLayout[] layouts = store.loadLayouts("task-1", 4).orElseThrow();

        assertEquals(original, layouts[2]);
        assertNull(layouts[0], "没写过布局的格子必须是 null，下游据此跳过这一格");
        assertNull(layouts[3]);
    }

    /**
     * 伴生布局是**可选**输入：没有它时标量结果照常读，恢复不受影响。
     * 旧任务目录里本来就没有这个文件，不能因此让任务读不出来。
     */
    @Test
    void layoutCompanionIsOptionalForRestoration() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        store.saveCell("task-1", 0, row("gcut1", 100));

        ExperimentDtos.ResultRow[] rows = store.loadCells("task-1", 1).orElseThrow();
        assertEquals("gcut1", rows[0].instanceId(), "没有布局文件不影响标量结果");
        assertNull(store.loadLayouts("task-1", 1).orElseThrow()[0], "缺席时布局就是 null");
    }

    /** 只剩下一个伴生布局、没有标量结果时，它不能被当成「这一格算完了」。 */
    @Test
    void layoutWithoutScalarResultIsNotCountedAsACompletedCell() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        store.saveLayout("task-1", 0, layout());

        ExperimentDtos.ResultRow[] rows = store.loadCells("task-1", 1).orElseThrow();
        assertNull(rows[0], "只有布局、没有标量结果的格子仍是「待补」");
    }

    /** 布局文件损坏只丢那一格的布局，标量结果与其余格子照常。 */
    @Test
    void corruptLayoutOnlyLosesThatLayout() throws IOException {
        ExperimentTaskStore store = new ExperimentTaskStore(directory);
        store.saveCell("task-1", 0, row("gcut1", 100));
        store.saveLayout("task-1", 0, layout());
        Files.write(directory.resolve("task-1").resolve("cells").resolve("0.layout.bin"),
                new byte[] {0, 0, 0, 1, 9, 9});

        assertNull(store.loadLayouts("task-1", 1).orElseThrow()[0], "损坏的布局读不出来是允许的");
        assertEquals("gcut1", store.loadCells("task-1", 1).orElseThrow()[0].instanceId(),
                "布局损坏不得影响标量结果的读取");
    }

    /** 目录下的文件名清单，用来断言「只该剩什么」和「不该留下什么」。 */
    private static List<String> listing(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static ExperimentDtos.Request request() {
        return new ExperimentDtos.Request(
                "往返实验", List.of("gcut1", "gcut5"), List.of(AlgorithmId.GA_TABU, AlgorithmId.ILS),
                List.of(PackingDecoderType.SKYLINE, PackingDecoderType.MAXRECTS),
                List.of(20261012L, 20261013L), true,
                SolveDtos.BudgetMode.EVALUATIONS, 20_000L, null, 2);
    }

    private static ExperimentDtos.ResultRow row(String instanceId, long area) {
        return new ExperimentDtos.ResultRow(instanceId, "GA_TABU", "SKYLINE", 20261012L,
                true, area / 1000.0, area, 12L, area);
    }

    /** 一份带两个放置与三个收敛点的布局数据，字段取值互不相同，便于发现错位。 */
    private static ExperimentDtos.CellLayout layout() {
        return new ExperimentDtos.CellLayout(
                List.of(new SolveDtos.Placement(3, 100, 40, 7, 11, 100, 40, false, 4_000L),
                        new SolveDtos.Placement(9, 70, 30, 0, 88, 30, 70, true, 2_100L)),
                List.of(new SolveDtos.SearchPoint(0, 1, 4_000L),
                        new SolveDtos.SearchPoint(2, 15, 6_100L),
                        new SolveDtos.SearchPoint(7, 90, 9_999L)));
    }
}
