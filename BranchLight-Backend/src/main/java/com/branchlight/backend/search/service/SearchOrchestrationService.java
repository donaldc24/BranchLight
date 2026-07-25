package com.branchlight.backend.search.service;

import java.util.List;
import java.util.Objects;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.aggregation.SearchResultAggregator;
import com.branchlight.backend.search.provider.SearchProvider;
import com.branchlight.backend.search.query.GeneratedQuery;
import com.branchlight.backend.search.query.QueryVariantGenerator;

public final class SearchOrchestrationService {

    public static final String ORIGINAL_QUERY_PURPOSE =
            SearchResultAggregator.ORIGINAL_QUERY_PURPOSE;

    private final QueryVariantGenerator queryVariantGenerator;
    private final SearchResultAggregator searchResultAggregator;

    public SearchOrchestrationService(
            QueryVariantGenerator queryVariantGenerator,
            SearchProvider searchProvider,
            int resultLimit) {
        this(
                queryVariantGenerator,
                searchProvider,
                resultLimit,
                SearchExecutionCoordinator.sequential());
    }

    public SearchOrchestrationService(
            QueryVariantGenerator queryVariantGenerator,
            SearchProvider searchProvider,
            int resultLimit,
            SearchExecutionCoordinator executionCoordinator) {
        this(
                queryVariantGenerator,
                new SearchResultAggregator(
                        searchProvider,
                        resultLimit,
                        executionCoordinator));
    }

    public SearchOrchestrationService(
            QueryVariantGenerator queryVariantGenerator,
            SearchResultAggregator searchResultAggregator) {
        this.queryVariantGenerator = Objects.requireNonNull(
                queryVariantGenerator,
                "queryVariantGenerator must not be null");
        this.searchResultAggregator = Objects.requireNonNull(
                searchResultAggregator,
                "searchResultAggregator must not be null");
    }

    public List<AggregatedSearchResult> search(String originalQuery) {
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");

        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }

        List<GeneratedQuery> generatedQueries =
                Objects.requireNonNull(
                        queryVariantGenerator.generate(originalQuery),
                        "generatedQueries must not be null");
        return searchResultAggregator.aggregate(
                originalQuery,
                generatedQueries);
    }
}
