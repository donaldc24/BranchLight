package com.branchlight.backend.search.query;

import java.util.List;
import java.util.Objects;

public final class DeterministicQueryVariantGenerator
        implements QueryVariantGenerator {

    @Override
    public List<GeneratedQuery> generate(String originalQuery) {
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");

        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }

        String query = originalQuery.strip();

        return List.of(
                variant(
                        query,
                        "original official primary direct sources",
                        QueryPurpose.AUTHORITATIVE),
                variant(
                        query,
                        "clear explanation overview",
                        QueryPurpose.EXPLANATORY),
                variant(
                        query,
                        "examples procedures guides practical application",
                        QueryPurpose.PRACTICAL),
                variant(
                        query,
                        "limitations risks counterarguments tradeoffs",
                        QueryPurpose.CRITICAL),
                variant(
                        query,
                        "firsthand experiences substantive discussion",
                        QueryPurpose.HUMAN_DISCUSSION));
    }

    private GeneratedQuery variant(
            String originalQuery,
            String orientation,
            QueryPurpose purpose) {
        return new GeneratedQuery(
                originalQuery + " " + orientation,
                purpose);
    }
}
