package com.branchlight.backend.search.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FakeSearchProvider implements SearchProvider {

    private final List<RawSearchResult> configuredResults;
    private final List<SearchInvocation> invocations = new ArrayList<>();

    public FakeSearchProvider(List<RawSearchResult> configuredResults) {
        this.configuredResults = List.copyOf(Objects.requireNonNull(
                configuredResults,
                "configuredResults must not be null"));
    }

    @Override
    public List<RawSearchResult> search(String query, int resultLimit) {
        Objects.requireNonNull(query, "query must not be null");

        if (resultLimit < 0) {
            throw new IllegalArgumentException(
                    "resultLimit must not be negative");
        }

        invocations.add(new SearchInvocation(query, resultLimit));
        int resultCount = Math.min(resultLimit, configuredResults.size());
        return List.copyOf(configuredResults.subList(0, resultCount));
    }

    public List<SearchInvocation> invocations() {
        return List.copyOf(invocations);
    }

    public record SearchInvocation(String query, int resultLimit) {
    }
}
