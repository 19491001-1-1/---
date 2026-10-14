package com.assessment.packing.validation;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {
    public ValidationResult {
        errors = List.copyOf(errors);
    }
}
