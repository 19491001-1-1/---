package com.assessment.packing.web.dto;

import java.util.List;

public record MetaDto(
        String projectName,
        String version,
        String javaVersion,
        int productSearchAlgorithms,
        int productDecoders,
        int productCombinations,
        int implementedAlgorithmEntries,
        int searchDisplayWeightPercent,
        int packingDisplayWeightPercent,
        List<String> capabilities) {
    public MetaDto {
        capabilities = List.copyOf(capabilities);
    }
}
