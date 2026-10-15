package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmCatalog;
import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.dto.SolveDtos;
import com.assessment.packing.web.error.ApiException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 后台实验任务的调度与生命周期。
 *
 * <p>并发模型是两层固定的：{@code dispatcher} 只有固定几个线程，每个正在运行的实验独占一个；
 * {@code workers} 是所有实验共享的唯一工作池。任务提交给共享池、由每个实验自己的信号量限制在
 * 途数量。这样「同时在跑的求解线程数」上界是工作池大小，而不是「实验数 × 并行度」——
 * 后者会随同时提交的实验数线性膨胀，把机器打满。
 */
@Service
public final class ExperimentTaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExperimentTaskService.class);

    private static final int MAX_TASKS = 500;

    /**
     * 存储根目录下最多保留多少个任务目录。超过后按创建时间删掉**最旧的终态任务**，
     * 正在排队或运行的永远不删。默认值与「一次实验的矩阵上限」无关，只用来防止目录只增不减。
     */
    static final int DEFAULT_STORE_MAX_TASKS = 200;

    /** 同时运行的实验数上限。每个运行的实验占用一个 dispatcher 线程。 */
    private static final int RUNNING_JOBS = 2;

    /** 全局求解线程数上限。请求更高的并行度只会排队，不会再开线程。 */
    private static final int MAX_WORKER_THREADS = 8;

    /** SSE 心跳间隔：空闲连接也要定期写一点东西，否则中间代理会单方面断开。 */
    private static final long HEARTBEAT_SECONDS = 15;

    /**
     * 停机时等待在途线程收尾的上限。中断只能打到还没进临界区的工作线程；一个已经
     * 走到「写临时文件 → 原子替换」的收集循环是打断不了的，必须等它把这一次写完。
     * 不等的话，调用方会以为服务已经停了，而存储目录仍在被写——测试里表现为临时目录
     * 删不掉，生产上表现为停机后目录还在被改动。
     */
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    /**
     * 每个任务保留的 SSE 事件上限。一次实验最多产生 {@code 500 进度 + 1 完成} 个事件，
     * 所以这个上限在真实规模下不会触发裁剪，重连补发因此总能拿全。
     */
    private static final int EVENT_BUFFER_LIMIT = 600;

    /**
     * 单个格子的求解入口。抽成接口只为让测试能注入一个「可控耗时」的实现，
     * 从而稳定地验证取消与完成序进度这类依赖时序的行为——真实算法在 QUICK 配置下
     * 全都只要几十毫秒，快到根本来不及观察中间状态。生产环境始终是
     * {@link #solveService} 这条路径。
     */
    interface CellSolver {
        CellOutcome solve(ExperimentDtos.Request request, Cell cell);
    }

    /**
     * 单格的完整产物：标量结果，外加**可选的**布局数据。
     *
     * <p>布局数据是生成排样 SVG / 收敛图的原料，但标量结果才是任务恢复与记录页的必需项。
     * 因此 {@code layout} 允许为 {@code null}——测试注入的轻量求解器只造标量结果，
     * 真实求解器则顺带把放置列表与收敛轨迹带回来。
     */
    record CellOutcome(ExperimentDtos.ResultRow row, ExperimentDtos.CellLayout layout) {
        CellOutcome {
            Objects.requireNonNull(row, "标量结果不能为空");
        }

        static CellOutcome scalar(ExperimentDtos.ResultRow row) {
            return new CellOutcome(row, null);
        }
    }

    private final CellSolver cellSolver;
    private final InstanceCatalogService instances;
    private final ExperimentTaskStore store;
    private final int storeMaxTasks;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService dispatcher;
    private final ExecutorService workers;
    private final ScheduledExecutorService heartbeat;
    /** 是否正在停机。停机期间的任务中断不写回磁盘，交由下次启动统一恢复。 */
    private volatile boolean shuttingDown;

    /**
     * 生产构造器。显式标注 {@link Autowired}：类里还有一个供测试注入 {@link CellSolver}
     * 的构造器，不标的话 Spring 无法在两者间做选择。
     *
     * <p>存储根目录由 {@code packing.experiment-store} 配置，默认落在被 git 忽略的
     * {@code results/web-experiments} 下。客户端无法指定它，因此不存在「让服务写到任意路径」的入口。
     */
    @Autowired
    public ExperimentTaskService(
            SolveService solveService,
            InstanceCatalogService instances,
            @Value("${packing.experiment-store:results/web-experiments}") String storeDirectory,
            @Value("${packing.experiment-store-max-tasks:200}") int storeMaxTasks) {
        this(new ServiceCellSolver(solveService), instances,
                new ExperimentTaskStore(Path.of(storeDirectory)), storeMaxTasks);
    }

    ExperimentTaskService(CellSolver cellSolver, InstanceCatalogService instances) {
        this(cellSolver, instances, new ExperimentTaskStore(Path.of("results/web-experiments")));
    }

    ExperimentTaskService(
            CellSolver cellSolver, InstanceCatalogService instances, ExperimentTaskStore store) {
        this(cellSolver, instances, store, DEFAULT_STORE_MAX_TASKS);
    }

    ExperimentTaskService(
            CellSolver cellSolver, InstanceCatalogService instances, ExperimentTaskStore store,
            int storeMaxTasks) {
        this.storeMaxTasks = storeMaxTasks;
        this.cellSolver = Objects.requireNonNull(cellSolver, "求解入口不能为空");
        this.instances = instances;
        this.store = Objects.requireNonNull(store, "任务存储不能为空");
        this.dispatcher = Executors.newFixedThreadPool(
                RUNNING_JOBS, threadFactory("packing-experiment-dispatcher"));
        this.workers = Executors.newFixedThreadPool(
                workerThreads(), threadFactory("packing-experiment-worker"));
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(
                threadFactory("packing-experiment-heartbeat"));
        this.heartbeat.scheduleWithFixedDelay(
                this::ping, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        restore();
    }

    /**
     * 共享工作池的大小：不把机器打满，也留出调度与 Web 请求的余量。
     * 上限 {@link #MAX_WORKER_THREADS} 与单次实验允许的最大并行度一致。
     */
    private static int workerThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(cores, MAX_WORKER_THREADS));
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 启动时恢复磁盘上的任务。
     *
     * <p>中间状态在重启后的转换规则只有两条，且都朝「保守」的方向走：
     * <ul>
     *   <li>{@code RUNNING}：进程已经没了，跑过的格子结果都在磁盘上，
     *       但没有任何线程还在推进它。直接落成 {@code CANCELLED} 而不是自动重跑——
     *       重启往往正是运维在干预，此时静默地把一批重活重新拉起来是最不该做的事。
     *       用户看到「已取消 · 已完成 37/48」后按一次重试即可接着算。</li>
     *   <li>{@code QUEUED}：本来就还没开跑（或上一轮还在排队），结果一个不少，
     *       自动重新排队，重启对用户完全透明。</li>
     * </ul>
     *
     * <p>{@code COMPLETED / FAILED / CANCELLED} 原样恢复，只恢复记录不恢复执行。
     * 终态任务不重放 {@code complete} 事件：事件序号是从磁盘续上的，若在此重放，
     * 带着旧序号的客户端重连时会收到一条「比它已知的序号还新」的重复终止事件。
     * 客户端若在任务结束后才连上，{@link #subscribe} 里「终态必发终止事件」那条路径
     * 会补一份快照，连接不会被挂住。
     */
    private void restore() {
        for (ExperimentTaskStore.StoredTask stored : store.loadAll()) {
            ExperimentDtos.ResultRow[] cells;
            try {
                cells = store.loadCells(stored.id(), taskCount(stored.request()))
                        .orElse(new ExperimentDtos.ResultRow[taskCount(stored.request())]);
            } catch (RuntimeException exception) {
                continue;
            }
            Job job = new Job(stored.id(), stored.request().name().trim(), stored.request(),
                    tasks(stored.request()));
            job.createdAt = stored.createdAt() == null ? job.createdAt : stored.createdAt();
            job.startedAt = stored.startedAt();
            job.completedAt = stored.completedAt();
            job.error = stored.error();
            job.cancelRequested = stored.cancelRequested();
            for (int index = 0; index < cells.length; index++) job.results.set(index, cells[index]);
            // 布局数据是可选的：旧任务目录里本来就没有伴生文件，读不到也只是「暂不可下载产物」，
            // 不影响任务恢复，更不该让整个任务被跳过。
            try {
                ExperimentDtos.CellLayout[] layouts = store.loadLayouts(stored.id(), cells.length)
                        .orElse(new ExperimentDtos.CellLayout[cells.length]);
                for (int index = 0; index < layouts.length; index++) job.layouts.set(index, layouts[index]);
            } catch (RuntimeException exception) {
                LOGGER.warn("实验任务 {} 的布局数据读取失败，产物生成将不可用", stored.id(), exception);
            }
            job.events.resumeAt(stored.lastEventId());
            // 恢复出来的格子已经躺在磁盘上，不必再写一遍。
            for (int index = 0; index < cells.length; index++) {
                if (cells[index] != null) job.persisted[index] = true;
            }
            boolean resumable = stored.status().equals("QUEUED") && !job.finished();
            if (stored.status().equals("RUNNING")) {
                job.status = "CANCELLED";
                job.completedAt = Instant.now();
            } else {
                job.status = stored.status();
            }
            jobs.put(job.id, job);
            if (resumable) {
                dispatcher.submit(() -> run(job));
            } else if (stored.status().equals("RUNNING")) {
                // 重启时正在跑的实验：进程没了，但已算完的格子都在磁盘上。
                // 不自动续跑——重启往往正是运维在干预，此时静默拉起重活最不该做；
                // 用户看到「已取消 · 37/48」后按一次重试即可接着算。
                job.error = "服务重启时该实验正在运行，已转为已取消；已完成的结果均已保留，可重试补齐。";
                persist(job);
            }
        }
        pruneStore();
    }

    /**
     * 存储目录保留策略：超过上限时删掉**最旧的终态任务目录**。
     *
     * <p>两条约束缺一不可：
     * <ul>
     *   <li>必须在 {@link #restore()} 的恢复循环<b>之后</b>调用。放在之前会把刚要恢复的任务
     *       当成「旧的」删掉，用户重启一次就丢一批记录。</li>
     *   <li>只删终态。{@code RUNNING}/{@code QUEUED} 的任务还在被写，删它的目录等于一边写一边拆房子；
     *       而且它们承载的是用户正在等的结果。</li>
     * </ul>
     *
     * <p>删除失败（半删状态、权限不足）只记日志：下一次启动会重新扫到它，跳过即可，不值得让服务起不来。
     */
    private void pruneStore() {
        if (storeMaxTasks <= 0) return;
        List<ExperimentTaskStore.StoredTask> tasks = store.loadAll();
        if (tasks.size() <= storeMaxTasks) return;
        // loadAll() 已按 createdAt 倒序：前 storeMaxTasks 个是「要留下的窗口」，
        // 其后的才是候选。候选按「最旧优先」从尾部开始删。
        //
        // 注意候选必须从窗口<b>之后</b>取，不能简单地把终态任务按顺序删到不超限为止：
        // 那样会在「最新的那个恰好是终态、其余都是排队中」时，把最新的任务删掉去凑数——
        // 既没腾出该腾的空间，还丢了一条用户刚跑完的记录。
        List<ExperimentTaskStore.StoredTask> candidates = tasks.subList(storeMaxTasks, tasks.size());
        int unremovable = 0;
        for (int index = candidates.size() - 1; index >= 0; index--) {
            String id = candidates.get(index).id();
            if (!prunable(candidates.get(index))) {
                // 排队中或运行中的任务正在被写入，删它的目录等于一边写一边拆房子。
                unremovable++;
                continue;
            }
            if (!store.removeTask(id)) {
                LOGGER.warn("实验任务 {} 的目录未能完全清理，下次启动会重试", id);
                unremovable++;
                continue;
            }
            // 磁盘上的目录没了，内存里的 Job 也必须一起走：留下来会让列表接口报出
            // 一条目录已经不存在的记录，而且它一旦重启就再也读不回来。
            jobs.remove(id);
        }
        if (unremovable > 0) {
            LOGGER.warn("存储目录超出上限 {}，其中 {} 个任务仍在排队或运行（或其目录未能清理），本次不回收",
                    storeMaxTasks, unremovable);
        }
    }

    /** 只有终态任务可以被保留策略回收；在跑或排队的任务目录正在被写入。 */
    private static boolean prunable(ExperimentTaskStore.StoredTask task) {
        String status = task.status();
        return status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED");
    }

    public ExperimentDtos.Preview preview(ExperimentDtos.Request request) {
        validateCatalog(request);
        int count = taskCount(request);
        if (count > MAX_TASKS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_TOO_LARGE",
                    "实验任务数最多为 " + MAX_TASKS + "，当前为 " + count);
        }
        List<String> warnings = new ArrayList<>();
        if (count > 100) warnings.add("任务较多，建议先用较小预算试跑");
        if (request.budgetMode() == SolveDtos.BudgetMode.TIME_MS) {
            warnings.add("时间预算强制串行，以保持墙钟时间公平");
        }
        if (request.parallelism() > workerThreads()) {
            warnings.add(String.format(
                    "当前机器共享工作池有 %d 个线程，并行度 %d 只会排队，不会开更多线程",
                    workerThreads(), request.parallelism()));
        }
        String work = request.budgetMode() == SolveDtos.BudgetMode.EVALUATIONS
                ? String.format("最多 %,d 次评价", count * request.maxEvaluations())
                : String.format("最多 %.1f 秒墙钟预算", count * request.maxMilliseconds() / 1000.0);
        return new ExperimentDtos.Preview(count, request.instanceIds().size(),
                request.searchAlgorithms().size(), request.decoders().size(), request.seeds().size(),
                request.parallelism(), work, List.copyOf(warnings));
    }

    public ExperimentDtos.Task create(ExperimentDtos.Request request) {
        preview(request);
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, request.name().trim(), request, tasks(request));
        jobs.put(id, job);
        // 先落盘再交给调度线程：否则进程在两者之间挂掉，这个任务就从记录里彻底消失了。
        persist(job);
        dispatcher.submit(() -> run(job));
        return snapshot(job, false);
    }

    /**
     * 请求取消。正在跑的求解会在下一个循环检查点收尾（返回当前历史最优解），
     * 已经算完的结果全部保留——这正是「取消后重试」能接着跑的原因。
     */
    public ExperimentDtos.Task cancel(String id) {
        Job job = requireJob(id);
        synchronized (job.lifecycle) {
            if (job.terminal()) {
                throw new ApiException(HttpStatus.CONFLICT, "EXPERIMENT_NOT_RUNNING",
                        "实验已经结束，无法取消：" + job.status);
            }
            job.cancelRequested = true;
            // 中断在跑的求解，让它们尽快放弃当前这次评价。
            for (Future<?> future : job.inFlight) future.cancel(true);
            // 落盘取消标记：若进程在 run() 收尾前退出，恢复时才能把它认成「用户已经叫停」，
            // 而不是「莫名其妙重启了」。写入放在锁内，与 run() 收尾的终态写入互斥——
            // 同一个任务的 meta 只有一个写者在任一时点，磁盘上不会出现两个版本互相覆盖。
            persist(job);
        }
        return snapshot(job, false);
    }

    /**
     * 重试：只重跑还没有结果的任务。取消、失败、以及两者混杂的场景都用这一条路径恢复，
     * 因此语义只有一句——「把没算出来的格子补上」。
     */
    public ExperimentDtos.Task retry(String id) {
        Job job = requireJob(id);
        synchronized (job.lifecycle) {
            if (!job.terminal()) {
                throw new ApiException(HttpStatus.CONFLICT, "EXPERIMENT_ALREADY_RUNNING",
                        "实验仍在运行，无需重试：" + job.status);
            }
            if (job.finished()) {
                throw new ApiException(HttpStatus.CONFLICT, "EXPERIMENT_NOTHING_TO_RETRY",
                        "实验已全部完成，没有需要重试的任务");
            }
            job.status = "QUEUED";
            job.error = null;
            job.cancelRequested = false;
            job.startedAt = null;
            job.completedAt = null;
            job.epoch++;
            job.inFlight.clear();
            // 与取消同理：排队状态的写入和上一轮的收尾写入必须互斥。
            persist(job);
        }
        dispatcher.submit(() -> run(job));
        return snapshot(job, false);
    }

    public List<ExperimentDtos.Task> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparing((Job job) -> job.createdAt).reversed())
                .map(job -> snapshot(job, false)).toList();
    }

    public ExperimentDtos.Task require(String id) {
        return snapshot(requireJob(id), true);
    }

    /**
     * 任务的原始请求。产物生成要从中取实例、算法、预算口径与并行度。
     * 包级可见，只给同包的 {@link ExperimentArtifactService} 用；对外接口不暴露这个视图。
     */
    ExperimentDtos.Request requestOf(String id) {
        return requireJob(id).request;
    }

    /**
     * 单格布局数据的快照，下标与结果行一一对应；没有伴生布局的格子为 {@code null}。
     *
     * <p>返回的是浅拷贝：调用方拿不到 {@code Job} 内部那个会被工作线程并发写的数组，
     * 因此产物生成期间不必担心读到「写了一半」的格子。
     */
    ExperimentDtos.CellLayout[] layoutsOf(String id) {
        Job job = requireJob(id);
        ExperimentDtos.CellLayout[] copy = new ExperimentDtos.CellLayout[job.total];
        for (int index = 0; index < job.total; index++) copy[index] = job.layouts.get(index);
        return copy;
    }

    /** 存储根目录。产物生成与保留策略共用同一个受控位置。 */
    Path storeRoot() {
        return store.root();
    }

    /**
     * 订阅任务事件。
     *
     * <p>{@code lastEventId} 由浏览器在 EventSource 自动重连时通过 {@code Last-Event-ID}
     * 请求头带回来。带上它意味着「我已经收到第 N 号事件」，服务端据此补发其后的所有事件；
     * 不带（首次订阅）则只发一份当前快照，避免新页面被历史进度刷屏。
     */
    public SseEmitter events(String id, String lastEventId) {
        Job job = requireJob(id);
        SseEmitter emitter = new SseEmitter(0L);
        job.emitters.add(emitter);
        emitter.onCompletion(() -> job.emitters.remove(emitter));
        emitter.onTimeout(() -> job.emitters.remove(emitter));
        subscribe(job, emitter, parseEventId(lastEventId));
        return emitter;
    }

    /**
     * 把已经算出结果、但还没落盘的格子写到磁盘。
     *
     * <p>调用点在收集循环里（每个完成事件一次），因此每个格子最多被写一次——写完就置位
     * {@code persisted}，重试后重跑出来的格子因为结果对象是全新的，会重新写一次。
     * 这里不做全量重写：500 个格子每个都重写一遍会把一次实验的落盘量从「完成数」放大到
     * 「完成数 × 格子数」。
     */
    private void persistCompletedCells(Job job) {
        for (int index = 0; index < job.total; index++) {
            if (job.persisted[index]) continue;
            ExperimentDtos.ResultRow row = job.results.get(index);
            if (row == null) continue;
            try {
                store.saveCell(job.id, index, row);
                // 伴生布局写不进标量文件（格式是位置式的，容不下长度不定的放置列表），
                // 因此单独落一份。它缺席只是「暂不可下载产物」，恢复与重试照常。
                ExperimentDtos.CellLayout layout = job.layouts.get(index);
                if (layout != null) store.saveLayout(job.id, index, layout);
                job.persisted[index] = true;
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("实验任务 {} 的格子 {} 落盘失败（存储根 {}），将在下次收集时重试",
                        job.id, index, store.root(), exception);
            }
        }
    }

    /**
     * 把任务的元数据写到磁盘。存储故障只记日志、不抛给调用方：任务本身还在内存里正常推进，
     * 因为一次磁盘写入失败就把用户的实验判死，损失的比保住的多。
     *
     * <p>写入在任务锁内串行。调用点里 {@code run()} 的收集循环在锁外、{@code cancel()} 与
     * {@code retry()} 在锁内，两边本来就可能同时进来；而元数据是「读-改-写」式的一份快照，
     * 两个写者交错时后落地的那个会把前者的字段带回去——最坏的例子是取消写入被收集写入
     * 覆盖，磁盘上的 {@code cancelRequested} 退回 false，重启后一个用户已经叫停的实验
     * 被重新排进队列。把这些约束收进这里，比要求每个调用点自己记得加锁可靠。
     */
    private void persist(Job job) {
        // 停机过程中不再改写磁盘：此刻中断的是「运维在停服务」，不是实验本身失败。
        // 若在这里把状态写成 FAILED，恢复时就会把一个被运维打断的实验报成「执行失败」，
        // 而它其实只是没跑完。保留 RUNNING，交给下次启动统一转成 CANCELLED，
        // 于是「优雅停机」和「进程被杀」在恢复语义上完全一致。
        if (shuttingDown) return;
        synchronized (job.lifecycle) {
            try {
                store.saveMeta(new ExperimentTaskStore.StoredTask(
                        job.id, job.request, job.status, job.createdAt, job.startedAt, job.completedAt,
                        job.error, job.cancelRequested, job.events.lastSequence()));
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("实验任务 {} 的元数据落盘失败（存储根 {}），本次运行仍会继续",
                        job.id, store.root(), exception);
            }
        }
    }

    private void run(Job job) {
        long epoch;
        // 排队期间就被取消掉的实验直接落地，不要白占一个 dispatcher 线程。
        synchronized (job.lifecycle) {
            epoch = job.epoch;
            if (job.cancelRequested && !job.finished()) {
                job.status = "CANCELLED";
                job.completedAt = Instant.now();
                persist(job);
                publish(job, "complete");
                closeEmitters(job);
                return;
            }
            job.status = "RUNNING";
            job.startedAt = Instant.now();
            persist(job);
        }
        publish(job, "progress");

        // 提交与收取必须交错：许可只在「收到一个完成」时归还，如果先把任务全部提交完
        // 再去收，超过并行度的第一批提交就会永远等不到许可。用非阻塞 tryAcquire，
        // 拿不到许可就先去收一个完成。
        Semaphore permits = new Semaphore(job.request.parallelism());
        ExecutorCompletionService<Void> completion = new ExecutorCompletionService<>(workers);
        int outstanding = 0;
        int next = 0;
        try {
            while (next < job.tasks.size()) {
                if (job.cancelRequested) break;
                Cell task = job.tasks.get(next);
                // 续跑：已经有结果的格子不再重算。
                if (job.results.get(task.index) != null) { next++; continue; }
                if (permits.tryAcquire()) {
                    submit(job, completion, task);
                    outstanding++;
                    next++;
                } else {
                    collect(completion, job, permits);
                    outstanding--;
                }
            }
            // 收尾：把在途的跑完（被取消的任务也会立刻投递完成，不会卡住）。
            while (outstanding > 0) {
                collect(completion, job, permits);
                outstanding--;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail(job, "实验任务被中断");
        } catch (Exception exception) {
            fail(job, exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage());
        } finally {
            synchronized (job.lifecycle) {
                if (job.status.equals("RUNNING")) {
                    if (job.cancelRequested) {
                        // 取消也要把已经算完的进度落盘成终态，客户端才知道停在哪。
                        job.status = "CANCELLED";
                    } else if (job.failures.get() > 0) {
                        job.status = "FAILED";
                        job.error = job.failures.get() + " 个任务执行失败，可重试补齐";
                    } else {
                        job.status = "COMPLETED";
                    }
                }
                job.completedAt = Instant.now();
                // 终态必须落盘：否则重启后这个任务会以 RUNNING 的样子被读到，
                // 而它其实已经全部算完了。
                persist(job);
            }
            // 收尾动作只在「我仍是当前轮次」时执行。retry() 会在旧轮次刚把状态改成终态、
            // 但还没走完收尾的瞬间插入新一轮；若不加这层判断，旧轮次会把新一轮刚建立的
            // 订阅连接一并关掉，新一轮的 SSE 进度就再也推不出去了。
            if (job.epoch == epoch) {
                job.inFlight.clear();
                publish(job, "complete");
                closeEmitters(job);
            }
        }
    }

    /** 提交一个格子到共享工作池。取消标记会在真正开跑前再查一次，避免白跑。 */
    private void submit(Job job, ExecutorCompletionService<Void> completion, Cell cell) {
        Future<?> future = completion.submit(() -> {
            try {
                if (!job.cancelRequested) {
                    job.currentTask = cell.label;
                    try {
                        CellOutcome outcome = cellSolver.solve(job.request, cell);
                        // 布局数据先于结果落位：结果数组一有值，记录页与重试逻辑就把它当成
                        // 「这一格已完成」；若布局还没写进去就置位结果，会留下一个
                        // 「有结果、没布局」的格子，产物生成时才发现缺图。
                        job.layouts.set(cell.index, outcome.layout());
                        job.results.set(cell.index, outcome.row());
                    } catch (RuntimeException exception) {
                        // 失败不落结果，格子保持「未完成」：进度因此诚实地停在 100% 以下，
                        // 重试也才能认出它需要重算。若在这里塞一个占位行，
                        // 「已完成」和「待重算」就变得无法区分，重试会永远跳过它。
                        job.failures.incrementAndGet();
                    }
                }
            } finally {
                // 中断标记不清会跟着线程回到池里，污染下一个任务。
                Thread.interrupted();
            }
            return null;
        });
        job.inFlight.add(future);
    }

    /**
     * 收下一个完成的任务并归还许可。
     *
     * <p>这里不向 Future 取返回值：任务结果已经写进结果数组，完成顺序不携带额外信息；
     * 而被取消掉的 Future 一旦取值就会抛 {@code CancellationException}，
     * 反倒把「已算完的结果」一起带崩。完成事件本身才是这里唯一需要的东西。
     */
    private void collect(
            ExecutorCompletionService<Void> completion, Job job, Semaphore permits)
            throws InterruptedException {
        Future<?> done = completion.take();
        permits.release();
        job.inFlight.remove(done);
        // 落盘发生在提交线程（本循环所在线程）而不是工作线程上，因此同一时刻只有一个写者，
        // 不需要给存储加锁；每个格子各写各的文件，也不会互相覆盖。
        persistCompletedCells(job);
        publish(job, "progress");
        // 元数据的落盘必须放在中断检查之后：被中断时再写一次，既留不住什么
        // （重启时 RUNNING 一律转成 CANCELLED，磁盘上的序号本就不会被当作续跑依据），
        // 又可能在「写临时文件」和「原子替换」中间被打断，留下一个残留的临时文件。
        if (Thread.interrupted()) throw new InterruptedException("实验任务在收集结果时被中断");
        persist(job);
    }

    /**
     * 生产路径：把格子交给 {@link SolveService}，与单次求解共用同一条求解链路。
     * 包级可见，便于测试在注入临时存储目录的同时仍走真实求解。
     */
    static final class ServiceCellSolver implements CellSolver {
        private final SolveService solveService;

        ServiceCellSolver(SolveService solveService) {
            this.solveService = solveService;
        }

        @Override
        public CellOutcome solve(ExperimentDtos.Request request, Cell cell) {
            SolveDtos.Result result = solveService.solve(new SolveDtos.Request(
                    cell.instanceId, cell.algorithm, cell.decoder, cell.seed,
                    request.rotationAllowed(), request.budgetMode(),
                    request.maxEvaluations(), request.maxMilliseconds()));
            ExperimentDtos.ResultRow row = new ExperimentDtos.ResultRow(
                    cell.instanceId, cell.algorithm.name(), cell.decoder.name(), cell.seed,
                    result.legal(), result.utilization(), result.packedArea(),
                    result.elapsedMilliseconds(), result.evaluations());
            // 标量结果之外多带一份布局数据。这一次多写几十 KB 的文件，换的是「实验跑完之后
            // 还能回头生成 CSV/HTML/SVG 产物」——没有它，产物生成只能得到一堆标量表格。
            return new CellOutcome(row, new ExperimentDtos.CellLayout(
                    result.placements(), result.convergence()));
        }
    }

    private void ping() {
        for (Job job : jobs.values()) {
            for (SseEmitter emitter : job.emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException exception) {
                    job.emitters.remove(emitter);
                }
            }
        }
    }

    /** 把订阅者接上：首次订阅给快照，重连按事件序号补发。 */
    private static void subscribe(Job job, SseEmitter emitter, Long lastEventId) {
        boolean terminalSent;
        if (lastEventId == null) {
            // 首次订阅：状态由快照表达，不进事件缓冲，免得多一个订阅者就多占一格补发额度。
            terminalSent = false;
            if (!sendRaw(emitter, new ExperimentDtos.ProgressEvent(
                    0, "progress", snapshot(job, false)))) {
                return;
            }
        } else {
            terminalSent = job.terminalAlreadySent(lastEventId);
            for (ExperimentDtos.ProgressEvent event : job.replayAfter(lastEventId)) {
                if (!sendRaw(emitter, event)) return;
            }
        }
        // 任务已经结束时，订阅者必须收到一个名为 complete 的终止事件。
        // 否则「快到来不及订阅」的实验会让前端一直停在运行中的观感里，连接也一直挂着。
        if (!terminalSent && job.terminal()) {
            sendRaw(emitter, new ExperimentDtos.ProgressEvent(
                    0, "complete", snapshot(job, false)));
        }
    }

    private static void closeEmitters(Job job) {
        job.emitters.forEach(SseEmitter::complete);
        job.emitters.clear();
    }

    /** 广播一次进度：先记进环形缓冲（供重连补发），再发给所有在线订阅者。 */
    private static void publish(Job job, String event) {
        ExperimentDtos.ProgressEvent record =
                job.record(event, snapshot(job, false));
        job.emitters.removeIf(emitter -> !sendRaw(emitter, record));
    }

    private static boolean sendRaw(SseEmitter emitter, ExperimentDtos.ProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(event.id()))
                    .name(event.name())
                    .data(event.data()));
            return true;
        } catch (IOException | IllegalStateException exception) {
            emitter.complete();
            return false;
        }
    }

    private static Long parseEventId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void validateCatalog(ExperimentDtos.Request request) {
        request.instanceIds().forEach(instances::require);
        if (!AlgorithmCatalog.optimizerAlgorithms().containsAll(request.searchAlgorithms())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SEARCH_ALGORITHM", "实验包含非产品级搜索器");
        }
    }

    private Job requireJob(String id) {
        Job job = jobs.get(id);
        if (job == null) throw new ApiException(HttpStatus.NOT_FOUND, "EXPERIMENT_NOT_FOUND", "不存在的实验任务：" + id);
        return job;
    }

    private static int taskCount(ExperimentDtos.Request request) {
        long count = (long) request.instanceIds().size() * request.searchAlgorithms().size()
                * request.decoders().size() * request.seeds().size();
        return Math.toIntExact(count);
    }

    /**
     * 展开任务清单，顺序必须与 {@link #preview} 的计数口径一致：
     * 实例 × 搜索器 × 解码器 × 种子。下标即结果数组的位置，
     * 因此并行完成顺序不会影响记录页看到的明细顺序。
     */
    private static List<Cell> tasks(ExperimentDtos.Request request) {
        List<Cell> tasks = new ArrayList<>();
        int index = 0;
        for (String instanceId : request.instanceIds()) {
            for (AlgorithmId algorithm : request.searchAlgorithms()) {
                for (PackingDecoderType decoder : request.decoders()) {
                    for (long seed : request.seeds()) {
                        tasks.add(new Cell(index++, instanceId, algorithm, decoder, seed,
                                instanceId + " · " + algorithm.name() + " × " + decoder.name()));
                    }
                }
            }
        }
        return List.copyOf(tasks);
    }

    private static void fail(Job job, String message) {
        synchronized (job.lifecycle) {
            job.status = "FAILED";
            job.error = message == null || message.isBlank() ? "实验执行失败" : message;
        }
    }

    private static ExperimentDtos.Task snapshot(Job job, boolean includeResults) {
        List<ExperimentDtos.ResultRow> rows = new ArrayList<>();
        for (int index = 0; index < job.total; index++) {
            ExperimentDtos.ResultRow row = job.results.get(index);
            if (row != null) rows.add(row);
        }
        int completed = rows.size();
        double average = rows.stream().mapToDouble(ExperimentDtos.ResultRow::utilization).average().orElse(0);
        Double best = rows.stream().mapToDouble(ExperimentDtos.ResultRow::utilization)
                .max().stream().boxed().findFirst().orElse(null);
        int legal = (int) rows.stream().filter(ExperimentDtos.ResultRow::legal).count();
        return new ExperimentDtos.Task(job.id, job.name, job.status, job.createdAt, job.startedAt,
                job.completedAt, job.total, completed, job.total == 0 ? 0 : completed * 100.0 / job.total,
                job.currentTask, legal, average, best, job.error, includeResults ? List.copyOf(rows) : List.of());
    }

    /**
     * 停机。顺序和等待都是有意的：
     *
     * <ol>
     *   <li>先置停机标记，让在途任务不再改写磁盘（见 {@link #persist}），
     *       于是「优雅停机」与「进程被杀」在恢复语义上完全一致；</li>
     *   <li>再中断并<b>等待</b>在途线程真正收尾。只 {@code shutdownNow()} 不等，
     *       会留下一个「以为已经停了、其实还在写」的窗口——测试里是临时目录删不掉，
     *       生产里是停机后存储目录仍在被改动；</li>
     *   <li>心跳最后停。它是唯一还会往连接里写东西的线程，提前停掉只会让收尾连接
     *       白等一次超时。</li>
     * </ol>
     */
    @PreDestroy
    void close() {
        shuttingDown = true;
        try {
            dispatcher.shutdownNow();
            workers.shutdownNow();
            awaitTermination();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            heartbeat.shutdownNow();
            try {
                heartbeat.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void awaitTermination() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_WAIT_SECONDS);
        for (ExecutorService pool : List.of(dispatcher, workers)) {
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) {
                pool.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            }
        }
    }

    /**
     * 一个待求解的结果格：实例 × 搜索器 × 解码器 × 种子的一个组合。
     * {@code index} 固定结果在数组中的位置，与完成顺序无关，因此并行打乱完成次序
     * 也不会改变记录页看到的明细顺序。
     */
    record Cell(
            int index, String instanceId, AlgorithmId algorithm,
            PackingDecoderType decoder, long seed, String label) {}

    /**
     * 任务的状态、时间戳与结果。产物生成需要按「任务」的粒度读取，而 {@code Task} 快照
     * 刻意只带明细、不带布局，因此这里把状态字段连同 {@link #layoutsOf} 一起开放给同包。
     */
    record JobView(
            String id,
            String name,
            String status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String error,
            int total,
            ExperimentDtos.ResultRow[] results) {

        boolean terminal() {
            return status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED");
        }
    }

    /** 任务状态与全部标量结果的快照，供产物生成做「是否终态、有哪些格子」的判断。 */
    JobView jobView(String id) {
        Job job = requireJob(id);
        ExperimentDtos.ResultRow[] rows = new ExperimentDtos.ResultRow[job.total];
        for (int index = 0; index < job.total; index++) rows[index] = job.results.get(index);
        return new JobView(job.id, job.name, job.status, job.createdAt, job.startedAt,
                job.completedAt, job.error, job.total, rows);
    }

    private static final class Job {
        private final String id;
        private final String name;
        private final ExperimentDtos.Request request;
        private final List<Cell> tasks;
        private final int total;
        /** 创建时间。恢复时会被磁盘上的值覆盖，因此不是 final。 */
        private Instant createdAt = Instant.now();
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicReferenceArray<ExperimentDtos.ResultRow> results;
        /** 单格布局数据（伴生文件的来源）。缺一格就是「这一格没有可绘图的布局」。 */
        private final AtomicReferenceArray<ExperimentDtos.CellLayout> layouts;
        /** 每个格子是否已落盘。只在提交线程（收集循环）上读写，因此用普通布尔数组即可。 */
        private final boolean[] persisted;
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Future<?>> inFlight = new CopyOnWriteArrayList<>();
        private final ExperimentEventLog events = new ExperimentEventLog(EVENT_BUFFER_LIMIT);
        private final Object lifecycle = new Object();
        private volatile String status = "QUEUED";
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String currentTask;
        private volatile String error;
        private volatile boolean cancelRequested;
        /** 轮次号：每次重试自增，用来让上一轮的收尾动作认出「我已经过时了」。 */
        private long epoch;

        private Job(String id, String name, ExperimentDtos.Request request, List<Cell> tasks) {
            this.id = id;
            this.name = name;
            this.request = request;
            this.tasks = tasks;
            this.total = tasks.size();
            this.results = new AtomicReferenceArray<>(tasks.size());
            this.layouts = new AtomicReferenceArray<>(tasks.size());
            this.persisted = new boolean[tasks.size()];
        }

        private synchronized ExperimentDtos.ProgressEvent record(String event, Object value) {
            return events.append(event, value);
        }

        private synchronized List<ExperimentDtos.ProgressEvent> replayAfter(Long after) {
            return events.replayAfter(after);
        }

        private synchronized boolean terminalAlreadySent(Long after) {
            return events.hasTerminalAfter(after);
        }

        /**
         * 是否所有格子都已有结果。直接数结果数组，让「进度」「明细」「能否重试」共享
         * 唯一真相；一个并行的计数器迟早会和它对不上。
         */
        private boolean finished() {
            for (int index = 0; index < total; index++) {
                if (results.get(index) == null) return false;
            }
            return true;
        }

        private boolean terminal() {
            return status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED");
        }
    }
}
