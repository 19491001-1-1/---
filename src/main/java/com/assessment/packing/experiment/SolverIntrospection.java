package com.assessment.packing.experiment;

import com.assessment.packing.solver.ConvergencePoint;
import com.assessment.packing.solver.ConvergenceTraceProvider;
import com.assessment.packing.solver.MaxRectsBestSolver;
import com.assessment.packing.solver.MaxRectsBestStatistics;
import com.assessment.packing.solver.PackingAlgorithm;
import com.assessment.packing.solver.AntColonySolver;
import com.assessment.packing.solver.GeneticAlgorithmSolver;
import com.assessment.packing.solver.IteratedLocalSearchSolver;
import com.assessment.packing.solver.SimulatedAnnealingSolver;
import com.assessment.packing.solver.TabuSearchSolver;

import java.util.List;

final class SolverIntrospection {
    private SolverIntrospection() {}

    static long evaluations(PackingAlgorithm solver) {
        if (solver instanceof MaxRectsBestSolver best) {
            return best.lastStatistics().entries().size();
        }
        if (solver instanceof TabuSearchSolver tabu) {
            return tabu.lastStatistics().evaluatedNeighbors();
        }
        if (solver instanceof SimulatedAnnealingSolver sa) {
            return sa.lastStatistics().temperatureSampleEvaluations()
                    + sa.lastStatistics().searchNeighborEvaluations();
        }
        if (solver instanceof GeneticAlgorithmSolver ga) {
            return ga.lastStatistics().fitnessEvaluations();
        }
        if (solver instanceof AntColonySolver aco) {
            return aco.lastStatistics().fitnessEvaluations();
        }
        if (solver instanceof IteratedLocalSearchSolver ils) {
            return ils.lastStatistics().fitnessEvaluations();
        }
        return 1;
    }

    static long iterations(PackingAlgorithm solver) {
        if (solver instanceof MaxRectsBestSolver best) {
            return best.lastStatistics().entries().size();
        }
        if (solver instanceof TabuSearchSolver tabu) {
            return tabu.lastStatistics().completedIterations();
        }
        if (solver instanceof SimulatedAnnealingSolver sa) {
            return sa.lastStatistics().completedTemperatureLevels();
        }
        if (solver instanceof GeneticAlgorithmSolver ga) {
            return ga.lastStatistics().completedGenerations();
        }
        if (solver instanceof AntColonySolver aco) {
            return aco.lastStatistics().completedIterations();
        }
        if (solver instanceof IteratedLocalSearchSolver ils) {
            return ils.lastStatistics().completedIterations();
        }
        return 1;
    }

    static List<ConvergencePoint> trace(PackingAlgorithm solver, long area) {
        if (solver instanceof ConvergenceTraceProvider provider) {
            return provider.lastConvergenceTrace();
        }
        return List.of(new ConvergencePoint(0, evaluations(solver), area));
    }

    static MaxRectsBestStatistics maxRectsBestStatistics(PackingAlgorithm solver) {
        if (solver instanceof MaxRectsBestSolver best) {
            return best.lastStatistics();
        }
        return MaxRectsBestStatistics.empty();
    }
}
