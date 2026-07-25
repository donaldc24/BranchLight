package com.branchlight.backend.search.aggregation;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.branchlight.backend.search.provider.RawSearchResult;
import com.branchlight.backend.search.provider.SearchProvider;
import com.branchlight.backend.search.query.GeneratedQuery;
import com.branchlight.backend.search.query.QueryPurpose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchResultAggregatorTest {

    private static final int RESULT_LIMIT = 10;

    @Test
    void canonicalizesUrlsWithoutLosingFunctionalQueryDistinctions() {
        var query = new GeneratedQuery(
                "canonicalization query",
                QueryPurpose.AUTHORITATIVE);
        var provider = new ScriptedSearchProvider(Map.of(
                query.queryText(),
                List.of(
                        result(
                                "HTTPS://Example.COM:443/articles/search"
                                        + "?view=full&utm_source=mail"
                                        + "#introduction",
                                "First full result",
                                "First snippet",
                                3,
                                LocalDate.of(2025, 1, 1)),
                        result(
                                "https://example.com/articles/search"
                                        + "?view=full&FBCLID=social",
                                "Best full result",
                                "Second snippet",
                                1,
                                LocalDate.of(2025, 2, 2)),
                        result(
                                "http://EXAMPLE.com:80"
                                        + "?gclid=tracking#fragment",
                                "Root result",
                                "Root snippet",
                                2,
                                null),
                        result(
                                "https://example.com/articles/search"
                                        + "?view=compact&utm_campaign=test",
                                "Compact result",
                                "Compact snippet",
                                4,
                                null))));
        var aggregator =
                new SearchResultAggregator(provider, RESULT_LIMIT);

        var results = aggregator.aggregate(List.of(query));

        assertThat(results)
                .extracting(AggregatedSearchResult::url)
                .containsExactly(
                        URI.create(
                                "https://example.com/articles/search"
                                        + "?view=full"),
                        URI.create("http://example.com/"),
                        URI.create(
                                "https://example.com/articles/search"
                                        + "?view=compact"));
        assertThat(results.get(0).title())
                .isEqualTo("Best full result");
    }

    @Test
    void executesEveryQueryAndMergesCanonicalDuplicates() {
        List<GeneratedQuery> queries = generatedQueries();
        URI articleUrl = URI.create(
                "https://example.com/articles/one");
        URI secondUrl = URI.create(
                "https://example.com/articles/two");
        var provider = new ScriptedSearchProvider(Map.of(
                queries.get(0).queryText(),
                List.of(
                        result(
                                articleUrl + "?utm_source=first#top",
                                "First title",
                                "Shared useful snippet",
                                7,
                                LocalDate.of(2024, 1, 1)),
                        result(
                                secondUrl.toString(),
                                "Second article",
                                "Second article snippet",
                                1,
                                LocalDate.of(2023, 3, 3))),
                queries.get(1).queryText(),
                List.of(result(
                        articleUrl + "?gclid=click",
                        "Explanation title",
                        "Shared useful snippet",
                        5,
                        LocalDate.of(2024, 2, 2))),
                queries.get(2).queryText(),
                List.of(result(
                        articleUrl + "?fbclid=social",
                        "Practical title",
                        "   ",
                        4,
                        LocalDate.of(2024, 3, 3))),
                queries.get(3).queryText(),
                List.of(result(
                        articleUrl + "?dclid=display",
                        "Best-ranked title",
                        "Critical useful snippet",
                        2,
                        LocalDate.of(2025, 4, 4))),
                queries.get(4).queryText(),
                List.of()));
        var aggregator =
                new SearchResultAggregator(provider, RESULT_LIMIT);

        var results = aggregator.aggregate(queries);

        assertThat(provider.invocations())
                .containsExactlyElementsOf(
                        queries.stream()
                                .map(query -> new SearchInvocation(
                                        query.queryText(),
                                        RESULT_LIMIT))
                                .toList());
        assertThat(results)
                .extracting(AggregatedSearchResult::url)
                .containsExactly(articleUrl, secondUrl);

        var merged = results.get(0);
        assertThat(merged.title())
                .isEqualTo("Best-ranked title");
        assertThat(merged.providerRank()).isEqualTo(2);
        assertThat(merged.publicationDate())
                .isEqualTo(LocalDate.of(2025, 4, 4));
        assertThat(merged.snippets())
                .containsExactly(
                        "Shared useful snippet",
                        "Critical useful snippet");
        assertThat(merged.retrievals())
                .extracting(
                        RetrievalMetadata::query,
                        RetrievalMetadata::purpose)
                .containsExactly(
                        tuple(queries.get(0)),
                        tuple(queries.get(1)),
                        tuple(queries.get(2)),
                        tuple(queries.get(3)));

        var second = results.get(1);
        assertThat(second.title()).isEqualTo("Second article");
        assertThat(second.providerRank()).isEqualTo(1);
        assertThat(second.snippets())
                .containsExactly("Second article snippet");
        assertThat(second.retrievals())
                .extracting(
                        RetrievalMetadata::query,
                        RetrievalMetadata::purpose)
                .containsExactly(tuple(queries.get(0)));
    }

    @Test
    void returnsImmutableAggregateCollections() {
        var query = new GeneratedQuery(
                "immutable query",
                QueryPurpose.PRACTICAL);
        var provider = new ScriptedSearchProvider(Map.of(
                query.queryText(),
                List.of(result(
                        "https://example.com/immutable",
                        "Immutable result",
                        "Useful snippet",
                        1,
                        null))));
        var aggregator =
                new SearchResultAggregator(provider, RESULT_LIMIT);

        var results = aggregator.aggregate(List.of(query));
        var result = results.get(0);

        assertThatThrownBy(() -> results.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                () -> result.snippets().add("another snippet"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                () -> result.retrievals().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static List<GeneratedQuery> generatedQueries() {
        return List.of(
                new GeneratedQuery(
                        "authoritative query",
                        QueryPurpose.AUTHORITATIVE),
                new GeneratedQuery(
                        "explanatory query",
                        QueryPurpose.EXPLANATORY),
                new GeneratedQuery(
                        "practical query",
                        QueryPurpose.PRACTICAL),
                new GeneratedQuery(
                        "critical query",
                        QueryPurpose.CRITICAL),
                new GeneratedQuery(
                        "human discussion query",
                        QueryPurpose.HUMAN_DISCUSSION));
    }

    private static org.assertj.core.groups.Tuple tuple(
            GeneratedQuery query) {
        return org.assertj.core.groups.Tuple.tuple(
                query.queryText(),
                query.purpose().name());
    }

    private static RawSearchResult result(
            String url,
            String title,
            String snippet,
            int providerRank,
            LocalDate publicationDate) {
        return new RawSearchResult(
                URI.create(url),
                title,
                snippet,
                providerRank,
                publicationDate,
                "provider placeholder query",
                "provider placeholder purpose");
    }

    private static final class ScriptedSearchProvider
            implements SearchProvider {

        private final Map<String, List<RawSearchResult>> resultsByQuery;
        private final List<SearchInvocation> invocations =
                new ArrayList<>();

        private ScriptedSearchProvider(
                Map<String, List<RawSearchResult>> resultsByQuery) {
            this.resultsByQuery = Map.copyOf(resultsByQuery);
        }

        @Override
        public List<RawSearchResult> search(
                String query,
                int resultLimit) {
            invocations.add(new SearchInvocation(
                    query,
                    resultLimit));
            return resultsByQuery.getOrDefault(query, List.of());
        }

        private List<SearchInvocation> invocations() {
            return List.copyOf(invocations);
        }
    }

    private record SearchInvocation(
            String query,
            int resultLimit) {
    }
}
