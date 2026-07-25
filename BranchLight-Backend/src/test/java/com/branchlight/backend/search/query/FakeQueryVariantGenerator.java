package com.branchlight.backend.search.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FakeQueryVariantGenerator
        implements QueryVariantGenerator {

    private final List<GeneratedQuery> configuredQueries;
    private final List<String> invocations = new ArrayList<>();

    public FakeQueryVariantGenerator(
            List<GeneratedQuery> configuredQueries) {
        this.configuredQueries = List.copyOf(
                Objects.requireNonNull(
                        configuredQueries,
                        "configuredQueries must not be null"));
    }

    @Override
    public List<GeneratedQuery> generate(String originalQuery) {
        invocations.add(Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null"));
        return configuredQueries;
    }

    public List<String> invocations() {
        return List.copyOf(invocations);
    }
}
