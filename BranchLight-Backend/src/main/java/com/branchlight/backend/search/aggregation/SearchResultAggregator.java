package com.branchlight.backend.search.aggregation;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.branchlight.backend.search.provider.RawSearchResult;
import com.branchlight.backend.search.provider.SearchProvider;
import com.branchlight.backend.search.query.GeneratedQuery;
import com.branchlight.backend.search.service.SearchExecutionCoordinator;

public final class SearchResultAggregator {

    public static final String ORIGINAL_QUERY_PURPOSE = "ORIGINAL_QUERY";

        private static final Logger LOGGER = LoggerFactory.getLogger(
                        SearchResultAggregator.class);

    private final SearchProvider searchProvider;
    private final int resultLimit;
    private final UrlCanonicalizer urlCanonicalizer;
        private final SearchExecutionCoordinator executionCoordinator;

    public SearchResultAggregator(
            SearchProvider searchProvider,
            int resultLimit) {
        this(
                searchProvider,
                resultLimit,
                new UrlCanonicalizer(),
                SearchExecutionCoordinator.sequential());
    }

    public SearchResultAggregator(
            SearchProvider searchProvider,
            int resultLimit,
            SearchExecutionCoordinator executionCoordinator) {
        this(
                searchProvider,
                resultLimit,
                new UrlCanonicalizer(),
                executionCoordinator);
    }

    SearchResultAggregator(
            SearchProvider searchProvider,
            int resultLimit,
            UrlCanonicalizer urlCanonicalizer) {
        this(
                searchProvider,
                resultLimit,
                urlCanonicalizer,
                SearchExecutionCoordinator.sequential());
    }

    SearchResultAggregator(
            SearchProvider searchProvider,
            int resultLimit,
            UrlCanonicalizer urlCanonicalizer,
            SearchExecutionCoordinator executionCoordinator) {
        this.searchProvider = Objects.requireNonNull(
                searchProvider,
                "searchProvider must not be null");
        this.urlCanonicalizer = Objects.requireNonNull(
                urlCanonicalizer,
                "urlCanonicalizer must not be null");
        this.executionCoordinator = Objects.requireNonNull(
                executionCoordinator,
                "executionCoordinator must not be null");

        if (resultLimit <= 0) {
            throw new IllegalArgumentException(
                    "resultLimit must be greater than zero");
        }

        this.resultLimit = resultLimit;
    }

    public List<AggregatedSearchResult> aggregate(
            List<GeneratedQuery> generatedQueries) {
        return aggregateRetrievals(
                toRetrievalMetadata(generatedQueries));
    }

    public List<AggregatedSearchResult> aggregate(
            String originalQuery,
            List<GeneratedQuery> generatedQueries) {
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");

        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }

        var retrievals = new ArrayList<RetrievalMetadata>();
        retrievals.add(new RetrievalMetadata(
                originalQuery,
                ORIGINAL_QUERY_PURPOSE));
        retrievals.addAll(toRetrievalMetadata(generatedQueries));
        return aggregateRetrievals(retrievals);
    }

    private List<RetrievalMetadata> toRetrievalMetadata(
            List<GeneratedQuery> generatedQueries) {
        Objects.requireNonNull(
                generatedQueries,
                "generatedQueries must not be null");

        var retrievals = new ArrayList<RetrievalMetadata>(
                generatedQueries.size());
        for (GeneratedQuery generatedQuery : generatedQueries) {
            GeneratedQuery query = Objects.requireNonNull(
                    generatedQuery,
                    "generatedQuery must not be null");
            retrievals.add(new RetrievalMetadata(
                    query.queryText(),
                    query.purpose().name()));
        }
        return retrievals;
    }

    private List<AggregatedSearchResult> aggregateRetrievals(
            List<RetrievalMetadata> retrievals) {
        Map<URI, ResultAccumulator> resultsByUrl =
                new LinkedHashMap<>();

        List<ProviderResults> retrievalResults =
                executionCoordinator.mapProviderQueries(
                        retrievals,
                        this::retrieve);
        for (ProviderResults retrievalResult : retrievalResults) {
            for (RawSearchResult providerResult
                    : retrievalResult.results()) {
                RawSearchResult result = Objects.requireNonNull(
                        providerResult,
                        "searchProvider result must not be null");
                URI canonicalUrl =
                        urlCanonicalizer.canonicalize(result.url());
                resultsByUrl
                        .computeIfAbsent(
                                canonicalUrl,
                                ResultAccumulator::new)
                        .merge(result, retrievalResult.retrieval());
            }
        }

        return resultsByUrl.values().stream()
                .map(ResultAccumulator::toResult)
                .toList();
    }

    private ProviderResults retrieve(RetrievalMetadata retrieval) {
        long started = System.nanoTime();
        try {
            List<RawSearchResult> results = Objects.requireNonNull(
                    searchProvider.search(
                            retrieval.query(),
                            resultLimit),
                    "searchProvider results must not be null");
            LOGGER.info(
                    "search.provider completed purpose={} resultCount={} durationMs={}",
                    retrieval.purpose(),
                    results.size(),
                    elapsedMillis(started));
            return new ProviderResults(retrieval, results);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "search.provider failed purpose={} durationMs={}",
                    retrieval.purpose(),
                    elapsedMillis(started),
                    exception);
            throw exception;
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private record ProviderResults(
            RetrievalMetadata retrieval,
            List<RawSearchResult> results) {
    }

    private static final class ResultAccumulator {

        private final URI canonicalUrl;
        private final Set<String> snippets = new LinkedHashSet<>();
        private final Set<RetrievalMetadata> retrievals =
                new LinkedHashSet<>();

        private RawSearchResult representative;

        private ResultAccumulator(URI canonicalUrl) {
            this.canonicalUrl = canonicalUrl;
        }

        private void merge(
                RawSearchResult result,
                RetrievalMetadata retrieval) {
            if (representative == null
                    || result.providerRank()
                    < representative.providerRank()) {
                representative = result;
            }

            if (!result.snippet().isBlank()) {
                snippets.add(result.snippet());
            }
            retrievals.add(retrieval);
        }

        private AggregatedSearchResult toResult() {
            return new AggregatedSearchResult(
                    canonicalUrl,
                    representative.title(),
                    representative.providerRank(),
                    representative.publicationDate(),
                    List.copyOf(snippets),
                    List.copyOf(retrievals));
        }
    }
}
