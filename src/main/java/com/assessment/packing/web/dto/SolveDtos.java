package com.assessment.packing.web.dto;

import com.assessment.packing.experiment.AlgorithmId;
import com.assessment.packing.solver.PackingDecoderType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SolveDtos {
    private SolveDtos() {}

    public enum BudgetMode {
        EVALUATIONS,
        TIME_MS
    }

    public record Request(
            @NotBlank String instanceId,
            @NotNull AlgorithmId searchAlgorithm,
            @NotNull PackingDecoderType decoder,
            long seed,
            boolean rotationAllowed,
            @NotNull BudgetMode budgetMode,
            @Positive @Max(5_000_000) Long maxEvaluations,
            @Positive @Max(300_000) Long maxMilliseconds) {

        @AssertTrue(message = "评价次数预算与时间预算必须二选一，且只能提供当前模式对应的值")
        public boolean isBudgetValid() {
            if (budgetMode == null) return true;
            return budgetMode == BudgetMode.EVALUATIONS
                    ? maxEvaluations != null && maxMilliseconds == null
                    : maxMilliseconds != null && maxEvaluations == null;
        }
    }

    public record Result(
            String runId,
            String status,
            Instant completedAt,
            String instanceId,
            String searchAlgorithm,
            String decoder,
            long seed,
            boolean rotationAllowed,
            boolean legal,
            List<String> validationErrors,
            long packedArea,
            long binArea,
            double utilization,
            int usedHeight,
            long elapsedMilliseconds,
            long evaluations,
            Long cacheQueries,
            Long cacheHits,
            Double cacheHitRatePercent,
            String terminationReason,
            List<Placement> placements,
            List<SearchPoint> convergence,
            Map<String, Object> algorithmStatistics) {
        public Result {
            validationErrors = List.copyOf(validationErrors);
            placements = List.copyOf(placements);
            convergence = List.copyOf(convergence);
            algorithmStatistics = Map.copyOf(algorithmStatistics);
        }
    }

    public record Placement(
            int itemId,
            int originalWidth,
            int originalHeight,
            int x,
            int y,
            int placedWidth,
            int placedHeight,
            boolean rotated,
            long area) {}

    public record SearchPoint(long step, long evaluations, long bestPackedArea) {}
}
