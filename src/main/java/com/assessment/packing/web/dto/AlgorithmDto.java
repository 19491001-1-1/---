package com.assessment.packing.web.dto;

import java.util.List;

public record AlgorithmDto(
        String machineValue,
        String chineseName,
        String englishName,
        String description,
        String guide,
        boolean stochastic,
        List<String> supportedBudgetModes) {
    public AlgorithmDto {
        supportedBudgetModes = List.copyOf(supportedBudgetModes);
    }
}
