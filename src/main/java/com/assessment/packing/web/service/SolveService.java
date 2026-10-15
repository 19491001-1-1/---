package com.assessment.packing.web.service;

import com.assessment.packing.experiment.AlgorithmCatalog;
import com.assessment.packing.experiment.ExperimentProfile;
import com.assessment.packing.experiment.SolverFactory;
import com.assessment.packing.model.Instance;
import com.assessment.packing.model.Solution;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.ConvergenceTraceProvider;
import com.assessment.packing.solver.DecodingCacheStatistics;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.ItemOrder;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.SearchLimit;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuSearchSolver;
import com.assessment.packing.validation.SolutionValidator;
import com.assessment.packing.web.dto.SolveDtos;
import com.assessment.packing.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public final class SolveService {
    private final InstanceCatalogService instances;
    private final SolutionValidator validator = new SolutionValidator();

    public SolveService(InstanceCatalogService instances) {
        this.instances = instances;
    }

    public SolveDtos.Result solve(SolveDtos.Request request) {
        if (!AlgorithmCatalog.optimizerAlgorithms().contains(request.searchAlgorithm())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SEARCH_ALGORITHM",
                    "单次求解只接受 6 个产品级搜索器：" + request.searchAlgorithm());
        }
        Instance instance = instances.require(request.instanceId());
        SearchLimit limit = request.budgetMode() == SolveDtos.BudgetMode.EVALUATIONS
                ? SearchLimit.evaluations(request.maxEvaluations())
                : SearchLimit.milliseconds(request.maxMilliseconds());
        PackingAlgorithm solver = SolverFactory.create(
                request.searchAlgorithm(), request.rotationAllowed(), ItemOrder.AREA_DESC,
                request.seed(), ExperimentProfile.QUICK, limit, request.decoder());
        Solution solution = solver.solve(instance);
        var validation = validator.validate(instance, solution, request.rotationAllowed());
        Metrics metrics = metrics(solver);
        List<SolveDtos.Placement> placements = solution.placements().stream()
                .map(placement -> new SolveDtos.Placement(
                        placement.item().id(), placement.item().width(), placement.item().height(),
                        placement.x(), placement.y(), placement.width(), placement.height(),
                        placement.rotated(), placement.area()))
                .toList();
        List<SolveDtos.SearchPoint> convergence = solver instanceof ConvergenceTraceProvider provider
                ? provider.lastConvergenceTrace().stream()
                        .map(point -> new SolveDtos.SearchPoint(
                                point.step(), point.evaluations(), point.bestPackedArea()))
                        .toList()
                : List.of(new SolveDtos.SearchPoint(0, metrics.evaluations(), solution.packedArea()));
        int usedHeight = solution.placements().stream()
                .mapToInt(placement -> placement.y() + placement.height()).max().orElse(0);
        long elapsedMilliseconds = solution.elapsedNanos() / 1_000_000L;
        return new SolveDtos.Result(
                UUID.randomUUID().toString(),
                validation.valid() ? "SUCCEEDED" : "INVALID",
                Instant.now(),
                request.instanceId(), request.searchAlgorithm().name(), request.decoder().name(),
                request.seed(), request.rotationAllowed(), validation.valid(), validation.errors(),
                solution.packedArea(), instance.binArea(), solution.utilization(instance), usedHeight,
                elapsedMilliseconds, metrics.evaluations(), metrics.cache().queries(),
                metrics.cache().hits(), metrics.cache().hitRatePercent(),
                terminationReason(request, metrics.evaluations(), elapsedMilliseconds),
                placements, convergence, metrics.statistics());
    }

    private static String terminationReason(
            SolveDtos.Request request, long evaluations, long elapsedMilliseconds) {
        if (request.budgetMode() == SolveDtos.BudgetMode.EVALUATIONS
                && evaluations >= request.maxEvaluations()) {
            return "EVALUATION_BUDGET_REACHED";
        }
        if (request.budgetMode() == SolveDtos.BudgetMode.TIME_MS
                && elapsedMilliseconds >= request.maxMilliseconds()) {
            return "TIME_BUDGET_REACHED";
        }
        return "ALGORITHM_COMPLETED";
    }

    private static Metrics metrics(PackingAlgorithm solver) {
        Map<String, Object> statistics = new LinkedHashMap<>();
        if (solver instanceof TabuSearchSolver tabu) {
            var value = tabu.lastStatistics();
            statistics.put("completedIterations", value.completedIterations());
            statistics.put("tabuHits", value.tabuHits());
            statistics.put("bestImprovements", value.bestImprovements());
            statistics.put("acceptedWorseMoves", value.acceptedWorseMoves());
            statistics.put("aspirationAcceptances", value.aspirationAcceptances());
            return new Metrics(value.evaluatedNeighbors(), value.cache(), statistics);
        }
        if (solver instanceof SimulatedAnnealingSolver annealing) {
            var value = annealing.lastStatistics();
            statistics.put("completedTemperatureLevels", value.completedTemperatureLevels());
            statistics.put("acceptedImprovingMoves", value.acceptedImprovingMoves());
            statistics.put("acceptedEqualMoves", value.acceptedEqualMoves());
            statistics.put("acceptedWorseMoves", value.acceptedWorseMoves());
            statistics.put("acceptanceRate", value.acceptanceRate());
            long evaluations = value.temperatureSampleEvaluations() + value.searchNeighborEvaluations();
            return new Metrics(evaluations, value.cache(), statistics);
        }
        if (solver instanceof AntColonySolver antColony) {
            var value = antColony.lastStatistics();
            statistics.put("completedIterations", value.completedIterations());
            statistics.put("constructedPermutations", value.constructedPermutations());
            statistics.put("pheromoneDeposits", value.pheromoneDeposits());
            statistics.put("bestImprovements", value.bestImprovements());
            return new Metrics(value.fitnessEvaluations(), value.cache(), statistics);
        }
        if (solver instanceof IteratedLocalSearchSolver iterated) {
            var value = iterated.lastStatistics();
            statistics.put("completedIterations", value.completedIterations());
            statistics.put("perturbations", value.perturbations());
            statistics.put("localImprovements", value.localImprovements());
            statistics.put("bestImprovements", value.bestImprovements());
            return new Metrics(value.fitnessEvaluations(), value.cache(), statistics);
        }
        if (solver instanceof GeneticAlgorithmSolver genetic) {
            var value = genetic.lastStatistics();
            statistics.put("completedGenerations", value.completedGenerations());
            statistics.put("effectivePopulationSize", value.effectivePopulationSize());
            statistics.put("crossoverEvents", value.crossoverEvents());
            statistics.put("mutationEvents", value.mutationEvents());
            statistics.put("duplicateRejections", value.duplicateRejections());
            statistics.put("bestImprovements", value.bestImprovements());
            if (genetic.hybridEnabled()) {
                var hybrid = genetic.lastHybridStatistics();
                statistics.put("localSearchCalls", hybrid.localSearchCalls());
                statistics.put("localCandidateEvaluations", hybrid.localCandidateEvaluations());
                statistics.put("localImprovements", hybrid.localImprovements());
            }
            if (genetic.adaptiveEnabled()) {
                var adaptive = genetic.lastAdaptiveStatistics();
                statistics.put("averageCrossoverRate", adaptive.averageCrossoverRate());
                statistics.put("averageMutationRate", adaptive.averageMutationRate());
            }
            return new Metrics(value.fitnessEvaluations(), value.cache(), statistics);
        }
        return new Metrics(1, DecodingCacheStatistics.empty(), statistics);
    }

    private record Metrics(
            long evaluations,
            DecodingCacheStatistics cache,
            Map<String, Object> statistics) {}
}
