package com.branchlight.backend.search.query;

import java.util.List;
import java.util.Objects;

public record QueryVariantValidationResult(
        List<QueryVariantValidationFailure> failures) {

    public QueryVariantValidationResult {
        Objects.requireNonNull(failures, "failures must not be null");
        failures = List.copyOf(failures);
    }

    public boolean isValid() {
        return failures.isEmpty();
    }
}
