package com.assessment.packing.web.dto;

import java.util.List;

public record AlgorithmDto(
        String machineValue,
        String chineseName,
        String englishName,
        String description,
        boolean stochastic,
        List<String> supportedBudgetModes) {
    public AlgorithmDto {
        supportedBudgetModes = List.copyOf(supportedBudgetModes);
    }
}
