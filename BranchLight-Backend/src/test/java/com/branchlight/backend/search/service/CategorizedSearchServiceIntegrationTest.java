package com.branchlight.backend.search.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.api.SearchController;
import com.branchlight.backend.search.api.SearchRequest;
import com.branchlight.backend.search.api.SearchResponse;
import com.branchlight.backend.search.content.JsoupContentExtractor;
import com.branchlight.backend.search.content.PassageSplitter;
import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.eligibility.DeterministicRoleEligibilityEvaluator;
import com.branchlight.backend.search.features.SourceFeatureExtractor;
import com.branchlight.backend.search.fetch.PageFetchFailure;
import com.branchlight.backend.search.fetch.PageFetchFailureType;
import com.branchlight.backend.search.fetch.PageFetchSuccess;
import com.branchlight.backend.search.fetch.PageFetcher;
import com.branchlight.backend.search.optimization.ResultSetOptimizer;
import com.branchlight.backend.search.provider.FakeSearchProvider;
import com.branchlight.backend.search.provider.RawSearchResult;
import com.branchlight.backend.search.query.FakeQueryVariantGenerator;
import com.branchlight.backend.search.query.GeneratedQuery;
import com.branchlight.backend.search.query.QueryPurpose;
import com.branchlight.backend.search.ranking.LexicalRelevanceScorer;
import com.branchlight.backend.search.ranking.PreliminaryCandidateRanker;
import com.branchlight.backend.search.ranking.PreliminaryCandidateRankingWeights;
import com.branchlight.backend.search.scoring.DeterministicRoleScorer;

import static org.assertj.core.api.Assertions.assertThat;

class CategorizedSearchServiceIntegrationTest {

    private static final String ORIGINAL_QUERY =
            "  general process result  ";
    private static final URI PROVENANCE_URL =
            URI.create("https://fixtures.test/provenance");
    private static final URI EXPLANATION_URL =
            URI.create("https://fixtures.test/explanation");
    private static final URI PRACTICAL_URL =
            URI.create("https://fixtures.test/practical");
    private static final URI FAILED_URL =
            URI.create("https://fixtures.test/unavailable");

    @Test
    void executesCompletePipelineAndPreservesPartialResults()
            throws IOException {
        List<GeneratedQuery> generatedQueries = List.of(
                generated("general process original evidence",
                        QueryPurpose.AUTHORITATIVE),
                generated("general process definitions examples",
                        QueryPurpose.EXPLANATORY),
                generated("general process procedure result",
                        QueryPurpose.PRACTICAL),
                generated("general process limitations risks",
                        QueryPurpose.CRITICAL),
                generated("general process experiences discussion",
                        QueryPurpose.HUMAN_DISCUSSION));
        var queryGenerator = new FakeQueryVariantGenerator(
                generatedQueries);
        var searchProvider = new FakeSearchProvider(providerResults());
        var requestedPages = new java.util.ArrayList<String>();
        PageFetcher pageFetcher = requestedUrl -> {
            requestedPages.add(requestedUrl);
            URI uri = URI.create(requestedUrl);
            if (uri.equals(FAILED_URL)) {
                return new PageFetchFailure(
                        requestedUrl,
                        uri,
                        PageFetchFailureType.CONNECTION_FAILURE,
                        null,
                        "Fixture page intentionally unavailable",
                        List.of(uri));
            }
            String fixtureName = switch (uri.getPath()) {
                case "/provenance" -> "provenance.html";
                case "/explanation" -> "explanation.html";
                case "/practical" -> "practical.html";
                default -> throw new IllegalArgumentException(
                        "Unknown fixture URL: " + uri);
            };
            try {
                String html = fixture(fixtureName);
                return new PageFetchSuccess(
                        requestedUrl,
                        uri,
                        200,
                        "text/html",
                        StandardCharsets.UTF_8,
                        html,
                        html.getBytes(StandardCharsets.UTF_8).length,
                        List.of(uri));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        };
        var orchestration = new SearchOrchestrationService(
                queryGenerator,
                searchProvider,
                10);
        var preliminaryRanker = new PreliminaryCandidateRanker(
                new PreliminaryCandidateRankingWeights(
                        3.0,
                        2.0,
                        1.0,
                        0.25,
                        0.25,
                        0.5,
                        1.0),
                10,
                1,
                1);
        var service = new CategorizedSearchService(
                orchestration,
                preliminaryRanker,
                pageFetcher,
                new JsoupContentExtractor(JsonMapper.builder().build()),
                new PassageSplitter(80, 120, 10),
                new LexicalRelevanceScorer(),
                new SourceFeatureExtractor(),
                new DeterministicRoleEligibilityEvaluator(),
                new DeterministicRoleScorer(),
                new ResultSetOptimizer());
        var controller = new SearchController(service);

        var responseEntity = controller.search(
                new SearchRequest(ORIGINAL_QUERY));

        assertThat(responseEntity.getBody())
                .isInstanceOf(SearchResponse.class);
        SearchResponse response =
                (SearchResponse) responseEntity.getBody();
        assertThat(response.query()).isEqualTo(ORIGINAL_QUERY);
        assertThat(queryGenerator.invocations())
                .containsExactly(ORIGINAL_QUERY);
        assertThat(searchProvider.invocations())
                .extracting(FakeSearchProvider.SearchInvocation::query)
                .containsExactlyElementsOf(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(ORIGINAL_QUERY),
                        generatedQueries.stream()
                                .map(GeneratedQuery::queryText))
                        .toList());
        assertThat(requestedPages)
                .contains(
                        PROVENANCE_URL.toString(),
                        EXPLANATION_URL.toString(),
                        PRACTICAL_URL.toString(),
                        FAILED_URL.toString());
        assertThat(response.results())
                .extracting(result -> result.role())
                .contains(
                        SearchRole.AUTHORITATIVE,
                        SearchRole.EXPLANATORY,
                        SearchRole.PRACTICAL)
                .doesNotHaveDuplicates();
        assertThat(response.results())
                .allSatisfy(result -> {
                    assertThat(result.url())
                            .isNotEqualTo(FAILED_URL.toString());
                    assertThat(result.score()).isBetween(0.0, 1.0);
                    assertThat(result.selectionReason())
                            .contains("globally optimal");
                });
        assertThat(response.results())
                .extracting(result -> result.url())
                .doesNotHaveDuplicates();
    }

    private static List<RawSearchResult> providerResults() {
        var snippets = new LinkedHashMap<URI, String>();
        snippets.put(
                PROVENANCE_URL,
                "General process result with measured original evidence.");
        snippets.put(
                EXPLANATION_URL,
                "General process explanation with definitions and examples.");
        snippets.put(
                PRACTICAL_URL,
                "General process procedure with expected result and steps.");
        snippets.put(
                FAILED_URL,
                "General process result from an unavailable page.");
        var results = new java.util.ArrayList<RawSearchResult>();
        int rank = 1;
        for (var entry : snippets.entrySet()) {
            results.add(new RawSearchResult(
                    entry.getKey(),
                    switch (entry.getKey().getPath()) {
                        case "/provenance" -> "Measured Original Evidence";
                        case "/explanation" -> "Understanding a General Process";
                        case "/practical" -> "Practical Process Procedure";
                        default -> "Unavailable General Process Result";
                    },
                    entry.getValue(),
                    rank++,
                    LocalDate.of(2026, 7, 1),
                    "provider placeholder",
                    "provider placeholder"));
        }
        return List.copyOf(results);
    }

    private static GeneratedQuery generated(
            String query,
            QueryPurpose purpose) {
        return new GeneratedQuery(query, purpose);
    }

    private static String fixture(String fixtureName)
            throws IOException {
        String path = "/fixtures/features/" + fixtureName;
        try (var input = CategorizedSearchServiceIntegrationTest.class
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Fixture not found: " + path);
            }
            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}