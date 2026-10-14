package com.assessment.packing.experiment;

public record SummaryObservation(
        AlgorithmId algorithm,
        String instance,
        int runs,
        int validRuns,
        double bestUtilization,
        double worstUtilization,
        double meanUtilization,
        double medianUtilization,
        double standardDeviation,
        double meanMilliseconds,
        long meanEvaluations,
        long bestPackedArea) {}
