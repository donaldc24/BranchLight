package com.branchlight.backend.search.aggregation;

import java.util.Objects;

public record RetrievalMetadata(
        String query,
        String purpose) {

    public RetrievalMetadata {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");

        if (query.isBlank()) {
            throw new IllegalArgumentException(
                    "query must not be blank");
        }
        if (purpose.isBlank()) {
            throw new IllegalArgumentException(
                    "purpose must not be blank");
        }
    }
}
