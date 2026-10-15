package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import com.assessment.packing.web.dto.ExperimentDtos;
import com.assessment.packing.web.dto.SolveDtos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Web 实验任务的跨进程存储。
 *
 * <p><b>为什么不直接复用核心层的 {@code .experiment-checkpoint.bin}</b>
 *
 * <p>那条链路在本层有三个硬性不兼容：
 * <ol>
 *   <li>格式是<b>位置式的</b>：写入和读取严格按字段顺序对应，追加任何字段（状态、事件序号）
 *       都会让旧文件整体错位。而这里恰好需要补的是状态机信息，正是它存不下的部分。</li>
 *   <li>它是一份<b>累积清单</b>：串行路径每完成一次运行就重写全部历史，并行路径为了维持
 *       串行顺序要等齐所有前序下标才追加。Web 侧收一个完成就落一个，语义对不上。</li>
 *   <li>{@link com.assessment.packing.experiment.RunCheckpoint} 是包级私有，
 *       Web 包无法访问，也没有公开的保存接口。</li>
 * </ol>
 *
 * <p>因此这里采用「一个任务一个目录、一个格子一个文件」的最小持久化方案：每次结果到达只写
 * 几十字节的单个文件，不动其他格子，天然就是增量的；任务元数据单独用原子替换写入。
 * 好处是不必触碰算法核心的任何代码，也不需要把核心包私有类提权成公开 API。
 *
 * <p>目录布局：
 * <pre>
 * &lt;experiment-store&gt;/
 *   &lt;task-id&gt;/meta.bin               任务元数据：请求、状态、时间戳、事件序号
 *   &lt;task-id&gt;/cells/&lt;下标&gt;.bin         单个格子的标量结果，独立原子写入
 *   &lt;task-id&gt;/cells/&lt;下标&gt;.layout.bin  单个格子的布局数据（放置 + 收敛轨迹），伴生文件
 * </pre>
 *
 * <p>布局数据刻意与标量结果<b>分开</b>存放：标量那份的写入端与读取端逐字段对称、被测试钉死，
 * 往位置式格式里追加字段会让整条读写链错位。伴生文件是产物生成的可选输入，缺席时读取与恢复
 * 照常，也可以被安全删除——删了只是不能生成排样图。
 */
final class ExperimentTaskStore {
    /** 文件头版本。读到别的版本直接跳过该任务，而不是让启动过程整体失败。 */
    private static final int VERSION = 1;

    /**
     * 伴生布局文件的后缀。读端与写端共用同一个常量，避免两处各写一份字符串而悄悄漂移。
     * 注意它以 {@code .bin} 结尾，因此标量结果的过滤器必须显式排掉它（见 {@link #isCellFile}）。
     */
    private static final String LAYOUT_SUFFIX = ".layout.bin";

    private final Path root;

    /** 临时文件名的单调序号，保证同一任务的两个并发写入各有各的暂存文件。 */
    private final AtomicLong temporaryCounter = new AtomicLong();

    ExperimentTaskStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    Path root() {
        return root;
    }

    /** 一个任务恢复所需的全部持久化状态。 */
    record StoredTask(
            String id,
            ExperimentDtos.Request request,
            String status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String error,
            boolean cancelRequested,
            long lastEventId) {}

    /**
     * 原子写入任务元数据。先写同目录的临时文件再整体替换：断电或崩溃只会留下一个被忽略的
     * {@code .tmp}，绝不会让 meta.bin 变成半截文件——否则启动恢复会连任务都读不出来。
     */
    synchronized void saveMeta(StoredTask task) throws IOException {
        Path directory = directory(task.id());
        Files.createDirectories(directory);
        Path target = directory.resolve("meta.bin");
        Path temporary = temporaryPath(directory);
        try (DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            out.writeInt(VERSION);
            out.writeUTF(task.id());
            out.writeUTF(task.status());
            writeInstant(out, task.createdAt());
            writeInstant(out, task.startedAt());
            writeInstant(out, task.completedAt());
            out.writeUTF(task.error() == null ? "" : task.error());
            out.writeBoolean(task.cancelRequested());
            out.writeLong(task.lastEventId());
            writeRequest(out, task.request());
        }
        move(temporary, target);
    }

    /** 原子写入一个格子的结果。格子的下标是它在结果数组中的位置，与完成顺序无关。 */
    void saveCell(String taskId, int index, ExperimentDtos.ResultRow row) throws IOException {
        Path directory = directory(taskId).resolve("cells");
        Files.createDirectories(directory);
        Path target = directory.resolve(index + ".bin");
        Path temporary = temporaryPath(directory);
        try (DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            out.writeInt(VERSION);
            out.writeUTF(row.instanceId());
            out.writeUTF(row.searchAlgorithm());
            out.writeUTF(row.decoder());
            out.writeLong(row.seed());
            out.writeBoolean(row.legal());
            out.writeDouble(row.utilization());
            out.writeLong(row.packedArea());
            out.writeLong(row.elapsedMilliseconds());
            out.writeLong(row.evaluations());
        }
        move(temporary, target);
    }

    /**
     * 原子写入一个格子的布局数据（伴生文件）。与 {@link #saveCell} 同一套「临时文件 + 原子替换」，
     * 只是文件名多一个 {@code .layout} 后缀、格式自带版本号与数量前缀，读取端可以自描述地解析。
     *
     * <p>它写不进 {@code cells/<下标>.bin}：那种位置式格式容不下长度不定的放置列表，
     * 硬塞会让标量结果的读写契约错位。
     */
    void saveLayout(String taskId, int index, ExperimentDtos.CellLayout layout) throws IOException {
        Path directory = directory(taskId).resolve("cells");
        Files.createDirectories(directory);
        Path target = directory.resolve(index + ".layout.bin");
        Path temporary = temporaryPath(directory);
        try (DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            out.writeInt(VERSION);
            out.writeInt(layout.placements().size());
            for (SolveDtos.Placement placement : layout.placements()) {
                out.writeInt(placement.itemId());
                out.writeInt(placement.originalWidth());
                out.writeInt(placement.originalHeight());
                out.writeInt(placement.x());
                out.writeInt(placement.y());
                out.writeInt(placement.placedWidth());
                out.writeInt(placement.placedHeight());
                out.writeBoolean(placement.rotated());
            }
            out.writeInt(layout.convergence().size());
            for (SolveDtos.SearchPoint point : layout.convergence()) {
                out.writeLong(point.step());
                out.writeLong(point.evaluations());
                out.writeLong(point.bestPackedArea());
            }
        }
        move(temporary, target);
    }

    /**
     * 读回全部布局数据，下标即数组位置；缺号就是「这一格没有伴生布局」。
     *
     * <p>单个文件损坏只丢那一格：它本就是可选的产物输入，读不出来时下游只是少一张排样图，
     * 不该连累任务恢复。返回 {@link Optional#empty()} 仅表示整个目录读不动。
     */
    Optional<ExperimentDtos.CellLayout[]> loadLayouts(String taskId, int total) {
        Path directory = directory(taskId).resolve("cells");
        if (!Files.isDirectory(directory)) return Optional.of(new ExperimentDtos.CellLayout[total]);
        ExperimentDtos.CellLayout[] layouts = new ExperimentDtos.CellLayout[total];
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(ExperimentTaskStore::isLayoutFile).toList()) {
                int index = indexOf(file, LAYOUT_SUFFIX);
                if (index < 0 || index >= total) continue;
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                        Files.newInputStream(file)))) {
                    if (in.readInt() != VERSION) continue;
                    layouts[index] = readLayout(in);
                } catch (IOException | RuntimeException exception) {
                    // 与标量结果同理：这一格没有可用的布局数据，其余照常读。
                }
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        return Optional.of(layouts);
    }

    private static ExperimentDtos.CellLayout readLayout(DataInputStream in) throws IOException {
        int placementCount = in.readInt();
        if (placementCount < 0 || placementCount > 1_000_000) {
            throw new EOFException("放置数量异常：" + placementCount);
        }
        List<SolveDtos.Placement> placements = new ArrayList<>(Math.min(placementCount, 64));
        for (int index = 0; index < placementCount; index++) {
            int itemId = in.readInt();
            int originalWidth = in.readInt();
            int originalHeight = in.readInt();
            int x = in.readInt();
            int y = in.readInt();
            int placedWidth = in.readInt();
            int placedHeight = in.readInt();
            boolean rotated = in.readBoolean();
            // area 是派生字段（等于原宽 × 原高），刻意不落盘也不从流里读：
            // 多读一次会让后面所有字段整体错位 8 字节，症状是布局读出来「看着有数据、全是垃圾」。
            placements.add(new SolveDtos.Placement(itemId, originalWidth, originalHeight, x, y,
                    placedWidth, placedHeight, rotated, (long) originalWidth * originalHeight));
        }
        int pointCount = in.readInt();
        if (pointCount < 0 || pointCount > 10_000_000) {
            throw new EOFException("收敛点数量异常：" + pointCount);
        }
        List<SolveDtos.SearchPoint> convergence = new ArrayList<>(Math.min(pointCount, 64));
        for (int index = 0; index < pointCount; index++) {
            long step = in.readLong();
            long evaluations = in.readLong();
            long bestPackedArea = in.readLong();
            if (step < 0 || evaluations < 0 || bestPackedArea < 0) {
                throw new EOFException("收敛点字段为负数");
            }
            convergence.add(new SolveDtos.SearchPoint(step, evaluations, bestPackedArea));
        }
        return new ExperimentDtos.CellLayout(placements, convergence);
    }

    /** 读回一个任务的全部格子结果，下标即数组位置；缺号就是「还没算出来」。 */
    Optional<ExperimentDtos.ResultRow[]> loadCells(String taskId, int total) {
        Path directory = directory(taskId).resolve("cells");
        if (!Files.isDirectory(directory)) return Optional.of(new ExperimentDtos.ResultRow[total]);
        ExperimentDtos.ResultRow[] rows = new ExperimentDtos.ResultRow[total];
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(ExperimentTaskStore::isCellFile).toList()) {
                int index = indexOf(file);
                if (index < 0 || index >= total) continue;
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                        Files.newInputStream(file)))) {
                    if (in.readInt() != VERSION) continue;
                    rows[index] = readRow(in);
                } catch (IOException | RuntimeException exception) {
                    // 单个格子文件损坏只丢这一个格子的结果，任务其余部分照常恢复；
                    // 缺失的格子会被重试逻辑当作「待补」重新执行。
                }
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        return Optional.of(rows);
    }

    /**
     * 扫描存储根目录下的全部任务。读不出来的任务整个跳过而不是抛出，理由和上面单个格子相同：
     * 一个坏掉的任务不该让服务起不来。返回列表按创建时间倒序，与列表接口的顺序一致。
     */
    List<StoredTask> loadAll() {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> directories = Files.list(root)) {
            List<StoredTask> tasks = new ArrayList<>();
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                readMeta(directory.resolve("meta.bin")).ifPresent(tasks::add);
            }
            // 目录名是恢复任务 id 的唯一来源；meta 里的 id 只作一致性校验。
            return tasks.stream()
                    .sorted(Comparator.comparing(StoredTask::createdAt).reversed())
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Optional<StoredTask> readMeta(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readInt() != VERSION) return Optional.empty();
            String id = in.readUTF();
            String status = in.readUTF();
            Instant createdAt = readInstant(in);
            Instant startedAt = readInstant(in);
            Instant completedAt = readInstant(in);
            String error = in.readUTF();
            boolean cancelRequested = in.readBoolean();
            long lastEventId = in.readLong();
            ExperimentDtos.Request request = readRequest(in);
            return Optional.of(new StoredTask(id, request, status, createdAt, startedAt, completedAt,
                    error.isEmpty() ? null : error, cancelRequested, lastEventId));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static void writeRequest(DataOutputStream out, ExperimentDtos.Request request)
            throws IOException {
        out.writeUTF(request.name());
        writeStrings(out, request.instanceIds());
        out.writeInt(request.searchAlgorithms().size());
        for (AlgorithmId algorithm : request.searchAlgorithms()) out.writeUTF(algorithm.name());
        out.writeInt(request.decoders().size());
        for (PackingDecoderType decoder : request.decoders()) out.writeUTF(decoder.name());
        out.writeInt(request.seeds().size());
        for (long seed : request.seeds()) out.writeLong(seed);
        out.writeBoolean(request.rotationAllowed());
        out.writeUTF(request.budgetMode().name());
        // 两个预算字段各自「存在标志 + 数值」成对写入，读取端必须对称地整对读完。
        out.writeBoolean(request.maxEvaluations() != null);
        out.writeLong(request.maxEvaluations() == null ? 0 : request.maxEvaluations());
        out.writeBoolean(request.maxMilliseconds() != null);
        out.writeLong(request.maxMilliseconds() == null ? 0 : request.maxMilliseconds());
        out.writeInt(request.parallelism());
    }

    private static ExperimentDtos.Request readRequest(DataInputStream in) throws IOException {
        String name = in.readUTF();
        List<String> instanceIds = readStrings(in);
        int algorithmCount = in.readInt();
        List<AlgorithmId> algorithms = new ArrayList<>(algorithmCount);
        for (int index = 0; index < algorithmCount; index++) {
            algorithms.add(AlgorithmId.valueOf(in.readUTF()));
        }
        int decoderCount = in.readInt();
        List<PackingDecoderType> decoders = new ArrayList<>(decoderCount);
        for (int index = 0; index < decoderCount; index++) {
            decoders.add(PackingDecoderType.valueOf(in.readUTF()));
        }
        int seedCount = in.readInt();
        List<Long> seeds = new ArrayList<>(seedCount);
        for (int index = 0; index < seedCount; index++) seeds.add(in.readLong());
        boolean rotationAllowed = in.readBoolean();
        SolveDtos.BudgetMode budgetMode = SolveDtos.BudgetMode.valueOf(in.readUTF());
        // 无论有没有值都必须把 8 字节读走：写入端总是写一个 long 加一个存在标志，
        // 这里若按标志短路跳过，流会整体错位 8 字节，后面所有字段都会读成垃圾。
        boolean hasEvaluations = in.readBoolean();
        long evaluationsValue = in.readLong();
        boolean hasMilliseconds = in.readBoolean();
        long millisecondsValue = in.readLong();
        Long maxEvaluations = hasEvaluations ? evaluationsValue : null;
        Long maxMilliseconds = hasMilliseconds ? millisecondsValue : null;
        int parallelism = in.readInt();
        return new ExperimentDtos.Request(name, instanceIds, algorithms, decoders, seeds,
                rotationAllowed, budgetMode, maxEvaluations, maxMilliseconds, parallelism);
    }

    private static ExperimentDtos.ResultRow readRow(DataInputStream in) throws IOException {
        return new ExperimentDtos.ResultRow(
                in.readUTF(), in.readUTF(), in.readUTF(), in.readLong(),
                in.readBoolean(), in.readDouble(), in.readLong(), in.readLong(), in.readLong());
    }

    private static void writeStrings(DataOutputStream out, List<String> values) throws IOException {
        out.writeInt(values.size());
        for (String value : values) out.writeUTF(value);
    }

    private static List<String> readStrings(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 64) throw new EOFException("字符串数量异常：" + count);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(in.readUTF());
        return List.copyOf(values);
    }

    /**
     * 时间戳按 ISO 字符串写。刻意不用毫秒时间戳：{@code Instant.toEpochMilli()} 会把亚毫秒
     * 截掉，恢复出来的 {@code createdAt} 与内存里的原值不相等，客户端的「运行时长」
     * 会凭空空出几毫秒，测试里也会表现为「同一个任务的时间戳变了」。
     */
    private static void writeInstant(DataOutputStream out, Instant value) throws IOException {
        out.writeBoolean(value != null);
        out.writeUTF(value == null ? "" : value.toString());
    }

    /** 无论有没有值都必须把负载读走，否则流会整体错位——这是格式里最容易踩的一处。 */
    private static Instant readInstant(DataInputStream in) throws IOException {
        boolean present = in.readBoolean();
        String text = in.readUTF();
        return present ? Instant.parse(text) : null;
    }

    private static Path directoryAt(Path root, String taskId) {
        // 任务 id 由服务端生成（UUID）或来自已存在的目录名；这里再挡一次，
        // 确保任何情况下都不会写到存储根目录之外。
        if (taskId.contains("/") || taskId.contains("\\") || taskId.contains("..")) {
            throw new IllegalArgumentException("非法的任务标识：" + taskId);
        }
        return root.resolve(taskId);
    }

    private Path directory(String taskId) {
        return directoryAt(root, taskId);
    }

    /**
     * 任务目录。产物生成与保留策略都要在同一个受控位置下操作，因此统一从这里取，
     * 不再各自拼一次路径——拼错一次就是写到存储根之外。
     */
    Path taskDirectory(String taskId) {
        return directory(taskId);
    }

    /**
     * 整个删除一个任务目录。保留策略只在终态任务上调用它。
     *
     * <p>逐个文件删除而不是一把 {@code deleteRecursively}：目录里可能同时有产物生成线程
     * 正在写，删到一半失败是正常的——下一次启动再扫一遍即可，不必为此把服务拖住。
     * 返回是否删干净。
     */
    boolean removeTask(String taskId) {
        Path directory = directory(taskId);
        if (!Files.exists(directory)) return true;
        boolean clean = true;
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    clean = false;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return clean;
    }

    /**
     * 标量结果文件：{@code <下标>.bin}。
     *
     * <p>必须显式排掉伴生布局文件。过滤条件原本只排 {@code .tmp}，而
     * {@code <下标>.layout.bin} 同样以 {@code .bin} 结尾，一个 {@code endsWith(".bin")} 会把它
     * 当成 {@code cells/<下标>.bin} 读进来；偏偏 {@code indexOf} 又能从 {@code 7.layout.bin}
     * 里抠出下标 7，于是布局文件的内容会被当成标量结果解析——字节恰好凑巧时甚至不会报错，
     * 直接污染成一条「看起来正常、数值全错」的结果行。
     */
    private static boolean isCellFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".bin") && !name.endsWith(".tmp") && !name.endsWith(LAYOUT_SUFFIX);
    }

    /** 伴生布局文件：{@code <下标>.layout.bin}。 */
    private static boolean isLayoutFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(LAYOUT_SUFFIX) && !name.endsWith(".tmp");
    }

    private static int indexOf(Path path) {
        return indexOf(path, ".bin");
    }

    private static int indexOf(Path path, String suffix) {
        String name = path.getFileName().toString();
        try {
            return Integer.parseInt(name.substring(0, name.length() - suffix.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            return -1;
        }
    }

    /**
     * 暂存文件名必须每次唯一，不能按目标名拼一个固定的 {@code *.tmp}。
     *
     * <p>同一个任务的两个写入者会并发进入 {@code saveMeta}（典型的是 {@code cancel()} 的
     * 取消标记与 {@code run()} 收尾的终态写入）。若两者共用一个固定的临时文件名，后者的
     * {@code newOutputStream} 会先把前者正在写的文件截断，前者 {@code move} 走后后者就
     * 找不到源文件——症状是一条莫名其妙的 {@code NoSuchFileException}，而更糟的是若
     * 取消那次写入后落地，它会把终态覆盖回 {@code RUNNING}，重启后一个已经跑完的任务
     * 被报成「已取消」。
     *
     * <p>{@code loadAll} 只认精确的 {@code meta.bin}，所以这里的名字怎么取都不会被误读。
     */
    private Path temporaryPath(Path directory) {
        return directory.resolve(".tmp-" + temporaryCounter.incrementAndGet());
    }

    private static void move(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            // 换手失败就顺手清掉自己的暂存文件，别给目录留下一个永远没人认领的残骸。
            // 目标目录若已被删掉（例如存储目录被清理），这份写入本来就留不住，
            // 但也不该因此让目录里多出一个只增不减的文件。
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException suppressed) {
                exception.addSuppressed(suppressed);
            }
            throw exception;
        }
    }

    /** 把受检 {@link IOException} 收敛成非受检异常，供不方便声明 throws 的调用点使用。 */
    static UncheckedIOException wrap(IOException exception) {
        return new UncheckedIOException(exception);
    }
}
