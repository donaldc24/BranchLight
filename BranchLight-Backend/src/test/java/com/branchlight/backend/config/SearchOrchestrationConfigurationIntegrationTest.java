package com.branchlight.backend.config;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.branchlight.backend.search.aggregation.RetrievalMetadata;
import com.branchlight.backend.search.provider.FakeSearchProvider;
import com.branchlight.backend.search.provider.RawSearchResult;
import com.branchlight.backend.search.query.FakeQueryVariantGenerator;
import com.branchlight.backend.search.query.GeneratedQuery;
import com.branchlight.backend.search.query.QueryPurpose;
import com.branchlight.backend.search.service.SearchOrchestrationService;
import com.branchlight.backend.search.service.SearchService;
import com.branchlight.backend.search.service.StubSearchService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {
        SearchOrchestrationConfigurationIntegrationTest
                .FakeDependencies.class,
        SearchOrchestrationConfiguration.class
})
class SearchOrchestrationConfigurationIntegrationTest {

    private static final String ORIGINAL_QUERY =
            "  \"virtual threads\" -preview  ";
    private static final List<GeneratedQuery> GENERATED_QUERIES = List.of(
            generated(
                    "virtual threads official sources",
                    QueryPurpose.AUTHORITATIVE),
            generated(
                    "virtual threads overview",
                    QueryPurpose.EXPLANATORY),
            generated(
                    "virtual threads examples",
                    QueryPurpose.PRACTICAL),
            generated(
                    "virtual threads limitations",
                    QueryPurpose.CRITICAL),
            generated(
                    "virtual threads firsthand discussion",
                    QueryPurpose.HUMAN_DISCUSSION));
    private static final List<RawSearchResult> PROVIDER_RESULTS = List.of(
            new RawSearchResult(
                    URI.create(
                            "HTTPS://Example.COM:443/guides/../article"
                                    + "?utm_source=search#section"),
                    "Initial title",
                    "First useful snippet",
                    4,
                    LocalDate.of(2025, 1, 1),
                    "provider query",
                    "provider purpose"),
            new RawSearchResult(
                    URI.create(
                            "https://example.com/article"
                                    + "?fbclid=click"),
                    "Best-ranked title",
                    "Second useful snippet",
                    1,
                    LocalDate.of(2026, 1, 1),
                    "another provider query",
                    "another provider purpose"));

    @Autowired
    private SearchOrchestrationService searchOrchestrationService;

    @Autowired
    private FakeQueryVariantGenerator queryVariantGenerator;

    @Autowired
    private FakeSearchProvider searchProvider;

    @Autowired
    private SearchService activeSearchService;

    @Test
    void injectsAndExecutesTheCompleteRetrievalPipeline() {
        var results =
                searchOrchestrationService.search(ORIGINAL_QUERY);

        assertThat(activeSearchService)
                .isInstanceOf(StubSearchService.class);
        assertThat(queryVariantGenerator.invocations())
                .containsExactly(ORIGINAL_QUERY);
        assertThat(searchProvider.invocations())
                .extracting(
                        FakeSearchProvider.SearchInvocation::query)
                .containsExactlyElementsOf(expectedQueries());
        assertThat(searchProvider.invocations())
                .extracting(
                        FakeSearchProvider.SearchInvocation::resultLimit)
                .containsOnly(10);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.url())
                .isEqualTo(URI.create(
                        "https://example.com/article"));
        assertThat(result.title())
                .isEqualTo("Best-ranked title");
        assertThat(result.providerRank()).isEqualTo(1);
        assertThat(result.snippets())
                .containsExactly(
                        "First useful snippet",
                        "Second useful snippet");
        assertThat(result.retrievals())
                .containsExactlyElementsOf(expectedRetrievals());
    }

    private List<String> expectedQueries() {
        return Stream.concat(
                        Stream.of(ORIGINAL_QUERY),
                        GENERATED_QUERIES.stream()
                                .map(GeneratedQuery::queryText))
                .toList();
    }

    private List<RetrievalMetadata> expectedRetrievals() {
        return Stream.concat(
                        Stream.of(new RetrievalMetadata(
                                ORIGINAL_QUERY,
                                SearchOrchestrationService
                                        .ORIGINAL_QUERY_PURPOSE)),
                        GENERATED_QUERIES.stream()
                                .map(query -> new RetrievalMetadata(
                                        query.queryText(),
                                        query.purpose().name())))
                .toList();
    }

    private static GeneratedQuery generated(
            String queryText,
            QueryPurpose purpose) {
        return new GeneratedQuery(queryText, purpose);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeDependencies {

        @Bean
        FakeQueryVariantGenerator queryVariantGenerator() {
            return new FakeQueryVariantGenerator(
                    GENERATED_QUERIES);
        }

        @Bean
        FakeSearchProvider searchProvider() {
            return new FakeSearchProvider(PROVIDER_RESULTS);
        }

        @Bean
        StubSearchService stubSearchService() {
            return new StubSearchService();
        }
    }
}
