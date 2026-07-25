package com.branchlight.backend.search.query;

import java.util.Objects;

public record GeneratedQuery(String queryText, QueryPurpose purpose) {

    public GeneratedQuery {
        Objects.requireNonNull(queryText, "queryText must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");

        if (queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
    }
}
