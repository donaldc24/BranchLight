package com.branchlight.backend.search.service;

import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.aggregation.RetrievalMetadata;
import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.provider.FakeSearchProvider;
import com.branchlight.backend.search.provider.RawSearchResult;
import com.branchlight.backend.search.query.FakeQueryVariantGenerator;
import com.branchlight.backend.search.query.GeneratedQuery;
import com.branchlight.backend.search.query.QueryPurpose;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchOrchestrationServiceIntegrationTest {

    private static final String ORIGINAL_QUERY =
            "  \"virtual threads\" -preview filetype:pdf  ";
    private static final int RESULT_LIMIT = 2;
    private static final List<GeneratedQuery> GENERATED_QUERIES = List.of(
            new GeneratedQuery(
                    "\"virtual threads\" primary sources -preview",
                    QueryPurpose.AUTHORITATIVE),
            new GeneratedQuery(
                    "\"virtual threads\" clear overview -preview",
                    QueryPurpose.EXPLANATORY),
            new GeneratedQuery(
                    "\"virtual threads\" practical examples -preview",
                    QueryPurpose.PRACTICAL),
            new GeneratedQuery(
                    "\"virtual threads\" limitations tradeoffs -preview",
                    QueryPurpose.CRITICAL),
            new GeneratedQuery(
                    "\"virtual threads\" firsthand discussion -preview",
                    QueryPurpose.HUMAN_DISCUSSION));
    private static final List<RawSearchResult> PROVIDER_RESULTS = List.of(
            new RawSearchResult(
                    URI.create("https://example.com/first"),
                    "First provider result",
                    "First provider snippet",
                    1,
                    LocalDate.of(2026, 7, 24),
                    "placeholder query",
                    "placeholder purpose"),
            new RawSearchResult(
                    URI.create("https://example.com/second"),
                    "Second provider result",
                    "Second provider snippet",
                    2,
                    null,
                    "different placeholder query",
                    "different placeholder purpose"));

    @Test
    void searchesOriginalAndGeneratedQueriesWhileRetainingRetrievalMetadata() {
        var queryVariantGenerator =
                new FakeQueryVariantGenerator(GENERATED_QUERIES);
        var searchProvider =
                new FakeSearchProvider(PROVIDER_RESULTS);
        var service = new SearchOrchestrationService(
                queryVariantGenerator,
                searchProvider,
                RESULT_LIMIT);

        var results = service.search(ORIGINAL_QUERY);

        assertEquals(
                List.of(ORIGINAL_QUERY),
                queryVariantGenerator.invocations());
        assertEquals(
                expectedProviderInvocations(),
                searchProvider.invocations());
        assertAggregatedResults(results);
        assertNoFinalSearchRoleIsAssigned();
    }

    private List<FakeSearchProvider.SearchInvocation>
            expectedProviderInvocations() {
        var generatedInvocations = GENERATED_QUERIES.stream()
                .map(query -> new FakeSearchProvider.SearchInvocation(
                        query.queryText(),
                        RESULT_LIMIT))
                .toList();

        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                new FakeSearchProvider.SearchInvocation(
                                        ORIGINAL_QUERY,
                                        RESULT_LIMIT)),
                        generatedInvocations.stream())
                .toList();
    }

    private void assertAggregatedResults(
            List<AggregatedSearchResult> results) {
        var retrievalQueries = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(ORIGINAL_QUERY),
                        GENERATED_QUERIES.stream()
                                .map(GeneratedQuery::queryText))
                .toList();
        var retrievalPurposes = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                SearchOrchestrationService
                                        .ORIGINAL_QUERY_PURPOSE),
                        GENERATED_QUERIES.stream()
                                .map(query -> query.purpose().name()))
                .toList();

        assertEquals(PROVIDER_RESULTS.size(), results.size());

        for (int resultIndex = 0;
                resultIndex < PROVIDER_RESULTS.size();
                resultIndex++) {
            var expectedProviderResult =
                    PROVIDER_RESULTS.get(resultIndex);
            var actualResult = results.get(resultIndex);

            assertAll(
                    () -> assertEquals(
                            expectedProviderResult.url(),
                            actualResult.url()),
                    () -> assertEquals(
                            expectedProviderResult.title(),
                            actualResult.title()),
                    () -> assertEquals(
                            expectedProviderResult.providerRank(),
                            actualResult.providerRank()),
                    () -> assertEquals(
                            expectedProviderResult.publicationDate(),
                            actualResult.publicationDate()),
                    () -> assertEquals(
                            List.of(expectedProviderResult.snippet()),
                            actualResult.snippets()),
                    () -> assertEquals(
                            expectedRetrievals(
                                    retrievalQueries,
                                    retrievalPurposes),
                            actualResult.retrievals()));
        }

        assertEquals(
                new RetrievalMetadata(
                        ORIGINAL_QUERY,
                        SearchOrchestrationService
                                .ORIGINAL_QUERY_PURPOSE),
                results.get(0).retrievals().get(0));
        assertFalse(results.get(0).retrievals().stream()
                .skip(1)
                .anyMatch(retrieval -> SearchOrchestrationService
                        .ORIGINAL_QUERY_PURPOSE
                        .equals(retrieval.purpose())));
    }

    private List<RetrievalMetadata> expectedRetrievals(
            List<String> retrievalQueries,
            List<String> retrievalPurposes) {
        return java.util.stream.IntStream
                .range(0, retrievalQueries.size())
                .mapToObj(index -> new RetrievalMetadata(
                        retrievalQueries.get(index),
                        retrievalPurposes.get(index)))
                .toList();
    }

    private void assertNoFinalSearchRoleIsAssigned() {
        assertFalse(Arrays.stream(
                        AggregatedSearchResult.class
                                .getRecordComponents())
                .anyMatch(component ->
                        component.getType().equals(SearchRole.class)));
    }
}
