package com.assessment.packing.web.dto;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ExperimentDtos {
    private ExperimentDtos() {}

    public record Request(
            @NotBlank @Size(max = 80) String name,
            @NotEmpty @Size(max = 12) List<@NotBlank String> instanceIds,
            @NotEmpty @Size(max = 6) List<@NotNull AlgorithmId> searchAlgorithms,
            @NotEmpty @Size(max = 8) List<@NotNull PackingDecoderType> decoders,
            @NotEmpty @Size(max = 20) List<@NotNull Long> seeds,
            boolean rotationAllowed,
            @NotNull SolveDtos.BudgetMode budgetMode,
            @Positive @Max(5_000_000) Long maxEvaluations,
            @Positive @Max(300_000) Long maxMilliseconds,
            @Positive @Max(8) int parallelism) {
        public Request {
            instanceIds = instanceIds == null ? List.of() : List.copyOf(instanceIds);
            searchAlgorithms = searchAlgorithms == null ? List.of() : List.copyOf(searchAlgorithms);
            decoders = decoders == null ? List.of() : List.copyOf(decoders);
            seeds = seeds == null ? List.of() : List.copyOf(seeds);
        }

        @AssertTrue(message = "评价次数预算与时间预算必须二选一")
        public boolean isBudgetValid() {
            if (budgetMode == null) return true;
            return budgetMode == SolveDtos.BudgetMode.EVALUATIONS
                    ? maxEvaluations != null && maxMilliseconds == null
                    : maxMilliseconds != null && maxEvaluations == null;
        }

        @AssertTrue(message = "时间预算实验必须串行运行，以免 CPU 竞争破坏公平性")
        public boolean isParallelismValid() {
            return budgetMode != SolveDtos.BudgetMode.TIME_MS || parallelism == 1;
        }
    }

    public record Preview(
            int taskCount,
            int instanceCount,
            int searchAlgorithmCount,
            int decoderCount,
            int seedCount,
            int parallelism,
            String estimatedWork,
            List<String> warnings) {}

    public record ResultRow(
            String instanceId,
            String searchAlgorithm,
            String decoder,
            long seed,
            boolean legal,
            double utilization,
            long packedArea,
            long elapsedMilliseconds,
            long evaluations) {}

    /**
     * 单格结果的**伴生**布局数据：排样放置与收敛轨迹。
     *
     * <p>它与标量 {@link ResultRow} 分开存放，因此两者的读写契约互不牵连——标量那份是
     * 位置式二进制格式、被「写入端与读取端逐字段对称」的测试钉死，往里面加字段会牵动整条链路。
     * 这一份是产物生成的可选输入：**缺席时只是暂时无法下载产物，读取与恢复照常**，
     * 也可以被安全删除（删了只是不能生成排样图）。
     */
    public record CellLayout(List<SolveDtos.Placement> placements, List<SolveDtos.SearchPoint> convergence) {
        public CellLayout {
            placements = placements == null ? List.of() : List.copyOf(placements);
            convergence = convergence == null ? List.of() : List.copyOf(convergence);
        }
    }

    /** 产物清单里的一项。{@code name} 是相对产物目录的路径，下载端点只接受清单里出现过的名字。 */
    public record Artifact(String name, long sizeBytes, String contentType) {}

    /**
     * 分析视图的单格数据：标量指标 + 该格的收敛轨迹 + 它在结果数组里的下标。
     *
     * <p>为什么轨迹直接挂在这里，而不是让前端再按需去取：热力图、排名、箱线图都只需要标量，
     * 但多算法收敛曲线对比需要轨迹。若拆成两个端点，前端要么在切换视图时重新请求并处理
     * 「两次请求之间任务又推进了」的不一致，要么把两批数据按下标对齐——而这个下标
     * 正是任务展开组合时的位置，错一处整张图就串行了。一次给全，前端只管按需渲染。
     *
     * <p>{@code index} 必须原样回传：前端要用它把分析数据与 {@code task.results} 对齐。
     * 注意 {@code points} 只包含**已完成**的格子，因此它不是稠密的——缺的格子直接不在列表里，
     * 而不是留一条全零的占位。全零占位会被热力图当成「利用率 0%」渲染成一个深色的坏格子。
     */
    public record AnalysisCell(
            int index,
            String instanceId,
            String searchAlgorithm,
            String decoder,
            long seed,
            boolean legal,
            double utilization,
            long packedArea,
            long elapsedMilliseconds,
            long evaluations,
            List<SolveDtos.SearchPoint> convergence,
            boolean curveTruncated) {
        public AnalysisCell {
            convergence = convergence == null ? List.of() : List.copyOf(convergence);
        }
    }

    /** 分析视图需要的实例几何：排样图与热力图都靠它把像素换算回坐标。 */
    public record AnalysisInstance(
            String id, String name, int binWidth, int binHeight, int itemCount) {}

    /**
     * 一个任务的完整分析数据。
     *
     * <p>{@code results} 是与 {@link Task#results()} 同构的标量数组，单独再给一份是为了让
     * 前端在「分析视图」里只依赖这一个响应，不必同时握着 SSE 推来的任务对象与它做对齐。
     */
    public record Analysis(
            String id,
            String name,
            String status,
            int totalTasks,
            int completedTasks,
            boolean rotationAllowed,
            String budgetMode,
            List<ResultRow> results,
            List<AnalysisCell> cells,
            Map<String, AnalysisInstance> instances) {
        public Analysis {
            results = results == null ? List.of() : List.copyOf(results);
            cells = cells == null ? List.of() : List.copyOf(cells);
            instances = instances == null ? Map.of() : Map.copyOf(instances);
        }
    }

    public record Task(
            String id,
            String name,
            String status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            int totalTasks,
            int completedTasks,
            double progressPercent,
            String currentTask,
            int legalResults,
            double averageUtilization,
            Double bestUtilization,
            String error,
            List<ResultRow> results) {}

    /**
     * 一条 SSE 事件。{@code id} 是任务内单调递增的序号，随 SSE 的 {@code id:} 字段下发，
     * 浏览器重连时会以 {@code Last-Event-ID} 回传，服务端据此补发断线期间漏掉的事件。
     *
     * <p>{@code id} 为 0 表示这是订阅瞬间的即时快照，不属于事件流本身——
     * 它不参与补发，否则每次重连都会多出一条重复的进度。
     */
    public record ProgressEvent(long id, String name, Object data) {
        public boolean terminal() { return name.equals("complete"); }
    }
}
