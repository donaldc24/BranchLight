package com.branchlight.backend.search.ranking;

import java.util.List;
import java.util.Objects;

public record RelevanceQuery(
        String originalQuery,
        List<String> supportingQueryVariants) {

    public RelevanceQuery {
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");
        supportingQueryVariants = List.copyOf(Objects.requireNonNull(
                supportingQueryVariants,
                "supportingQueryVariants must not be null"));
        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }
        if (supportingQueryVariants.stream().anyMatch(
                variant -> variant == null || variant.isBlank())) {
            throw new IllegalArgumentException(
                    "supportingQueryVariants must contain only non-blank values");
        }
    }
}