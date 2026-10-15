package com.assessment.packing.web.dto;

import java.time.Instant;
import java.util.Map;

public record ApiErrorDto(
        String code,
        String message,
        Map<String, String> fieldErrors,
        String requestId,
        Instant timestamp) {
    public ApiErrorDto {
        fieldErrors = Map.copyOf(fieldErrors);
    }
}
