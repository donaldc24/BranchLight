package com.branchlight.backend.search.api;

import java.util.List;
import java.util.Objects;

public record SearchResponse(String query, List<CategorizedResult> results) {

    public SearchResponse {
        Objects.requireNonNull(query, "query must not be null");
        results = List.copyOf(Objects.requireNonNull(
                results,
                "results must not be null"));
    }
}
