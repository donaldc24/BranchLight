package com.branchlight.backend.search.eligibility;

import java.util.List;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;

public record RoleEligibilityResult(
        SearchRole role,
        boolean eligible,
        double confidence,
        List<String> supportingFeatureNames,
        List<String> rejectingFeatureNames,
        List<String> diagnostics) {

    public RoleEligibilityResult {
        Objects.requireNonNull(role, "role must not be null");
        if (!Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be between 0.0 and 1.0");
        }
        supportingFeatureNames = immutableNames(
                supportingFeatureNames,
                "supportingFeatureNames");
        rejectingFeatureNames = immutableNames(
                rejectingFeatureNames,
                "rejectingFeatureNames");
        diagnostics = immutableNames(diagnostics, "diagnostics");
    }

    private static List<String> immutableNames(
            List<String> values,
            String name) {
        values = List.copyOf(Objects.requireNonNull(
                values,
                name + " must not be null"));
        if (values.stream().anyMatch(value -> value.isBlank())) {
            throw new IllegalArgumentException(
                    name + " must contain only non-blank values");
        }
        return values;
    }
}