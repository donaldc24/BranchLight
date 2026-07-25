package com.branchlight.backend.search.ranking;

import java.net.URI;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.aggregation.RetrievalMetadata;
import com.branchlight.backend.search.aggregation.SearchResultAggregator;

import static org.assertj.core.api.Assertions.assertThat;

class PreliminaryCandidateRankerTest {

    @Test
    void rewardsTheBetterProviderRankWhenOtherSignalsMatch() {
        var ranker = new PreliminaryCandidateRanker(weights(
                0.0,
                0.0,
                2.0,
                0.0,
                0.0,
                0.0,
                0.0));
        var lowerRanked = candidate(
                "lower",
                "Same title",
                10,
                List.of("Same useful snippet"),
                List.of());
        var higherRanked = candidate(
                "higher",
                "Same title",
                1,
                List.of("Same useful snippet"),
                List.of());

        var ranked = ranker.rank(
                "unmatched query",
                List.of(lowerRanked, higherRanked));

        assertThat(ranked)
                .extracting(result ->
                        result.candidate().providerRank())
                .containsExactly(1, 10);
        assertThat(ranked.get(0)
                .scoreBreakdown()
                .providerRankPrior()
                .rawValue())
                .isEqualTo(1.0);
        assertThat(ranked.get(1)
                .scoreBreakdown()
                .providerRankPrior()
                .rawValue())
                .isEqualTo(0.1);
    }

    @Test
    void usesTheOriginalQueryForTitleAndSnippetRelevance() {
        var ranker = new PreliminaryCandidateRanker(weights(
                2.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0));
        var retrieval = new RetrievalMetadata(
                "generated-only vocabulary",
                "AUTHORITATIVE");
        var originalMatch = candidate(
                "original-match",
                "DISTRIBUTED systems: latency",
                5,
                List.of("Latency for distributed systems."),
                List.of(retrieval));
        var generatedVariantMatch = candidate(
                "generated-match",
                "Generated only vocabulary",
                5,
                List.of("Generated-only vocabulary."),
                List.of(retrieval));

        var ranked = ranker.rank(
                "distributed systems latency",
                List.of(generatedVariantMatch, originalMatch));

        assertThat(ranked)
                .extracting(result ->
                        result.candidate().url())
                .containsExactly(
                        originalMatch.url(),
                        generatedVariantMatch.url());
        assertThat(ranked.get(0)
                .scoreBreakdown()
                .titleLexicalOverlap()
                .rawValue())
                .isEqualTo(1.0);
        assertThat(ranked.get(0)
                .scoreBreakdown()
                .snippetLexicalOverlap()
                .rawValue())
                .isEqualTo(1.0);
        assertThat(ranked.get(1)
                .scoreBreakdown()
                .titleLexicalOverlap()
                .rawValue())
                .isZero();
        assertThat(ranked.get(1)
                .scoreBreakdown()
                .snippetLexicalOverlap()
                .rawValue())
                .isZero();
    }

    @Test
    void rewardsDistinctRepeatedDiscoveryWithoutCountingDuplicates() {
        var ranker = new PreliminaryCandidateRanker(weights(
                0.0,
                0.0,
                0.0,
                1.0,
                0.5,
                0.0,
                0.0));
        var broadlyDiscovered = candidate(
                "broad",
                "Same useful title",
                5,
                List.of("Same useful snippet"),
                List.of(
                        retrieval(
                                "original query",
                                SearchResultAggregator
                                        .ORIGINAL_QUERY_PURPOSE),
                        retrieval("variant one", "AUTHORITATIVE"),
                        retrieval("variant one", "AUTHORITATIVE"),
                        retrieval("variant two", "AUTHORITATIVE"),
                        retrieval("variant three", "PRACTICAL")));
        var narrowlyDiscovered = candidate(
                "narrow",
                "Same useful title",
                5,
                List.of("Same useful snippet"),
                List.of(retrieval(
                        "variant one",
                        "AUTHORITATIVE")));

        var ranked = ranker.rank(
                "unmatched query",
                List.of(narrowlyDiscovered, broadlyDiscovered));

        assertThat(ranked.get(0).candidate())
                .isEqualTo(broadlyDiscovered);
        var breakdown = ranked.get(0).scoreBreakdown();
        assertThat(breakdown.distinctGeneratedQueryCount())
                .isEqualTo(3);
        assertThat(breakdown.distinctRetrievalPurposeCount())
                .isEqualTo(3);
        assertThat(breakdown
                .generatedQueryDiscovery()
                .contribution())
                .isEqualTo(3.0);
        assertThat(breakdown
                .retrievalPurposeDiversity()
                .contribution())
                .isEqualTo(1.5);
    }

    @Test
    void penalizesEmptyOrPunctuationOnlyMetadata() {
        var ranker = new PreliminaryCandidateRanker(weights(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.5));
        var lowQuality = candidate(
                "low-quality",
                "---",
                1,
                List.of("...", "   "),
                List.of());
        var useful = candidate(
                "useful",
                "Useful title",
                10,
                List.of("Useful snippet"),
                List.of());

        var ranked = ranker.rank(
                "unmatched query",
                List.of(lowQuality, useful));

        assertThat(ranked.get(0).candidate()).isEqualTo(useful);
        var breakdown = ranked.get(1).scoreBreakdown();
        assertThat(breakdown.lowQualityTitle()).isTrue();
        assertThat(breakdown.lowQualitySnippet()).isTrue();
        assertThat(breakdown
                .lowQualityMetadataPenalty()
                .rawValue())
                .isEqualTo(2.0);
        assertThat(breakdown
                .lowQualityMetadataPenalty()
                .contribution())
                .isEqualTo(-3.0);
        assertThat(breakdown.totalScore()).isEqualTo(-3.0);
    }

    @Test
    void penalizesNonEmptyMetadataWithTooFewDistinctTerms() {
        var ranker = new PreliminaryCandidateRanker(weights(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0));
        var lowQuality = candidate(
                "short",
                "Home",
                1,
                List.of("Click here"),
                List.of());

        var ranked = ranker.rank(
                "unmatched query",
                List.of(lowQuality));

        var breakdown = ranked.get(0).scoreBreakdown();
        assertThat(breakdown.lowQualityTitle()).isTrue();
        assertThat(breakdown.lowQualitySnippet()).isTrue();
        assertThat(breakdown.totalScore()).isEqualTo(-2.0);
    }

    @Test
    void rewardsSpecificNonRepetitiveTitles() {
        var ranker = new PreliminaryCandidateRanker(weights(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                0.0));
        var repetitive = candidate(
                "repetitive",
                "Guide guide guide guide guide",
                1,
                List.of("Useful snippet"),
                List.of());
        var specific = candidate(
                "specific",
                "Detailed resilient cache invalidation guide",
                10,
                List.of("Useful snippet"),
                List.of());

        var ranked = ranker.rank(
                "unmatched query",
                List.of(repetitive, specific));

        assertThat(ranked.get(0).candidate()).isEqualTo(specific);
        assertThat(ranked.get(0)
                .scoreBreakdown()
                .titleSpecificity()
                .rawValue())
                .isGreaterThan(ranked.get(1)
                        .scoreBreakdown()
                        .titleSpecificity()
                        .rawValue());
    }

    @Test
    void keepsTheTopTwentyFiveCandidatesByDefault() {
        var ranker = new PreliminaryCandidateRanker(weights(
                0.0,
                0.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0));
        var candidates = IntStream.rangeClosed(1, 30)
                .map(index -> 31 - index)
                .mapToObj(rank -> candidate(
                        "candidate-" + rank,
                        "Useful title",
                        rank,
                        List.of("Useful snippet"),
                        List.of()))
                .toList();

        var ranked = ranker.rank("query", candidates);

        assertThat(ranker.maximumCandidates()).isEqualTo(25);
        assertThat(ranked).hasSize(25);
        assertThat(ranked)
                .extracting(result ->
                        result.candidate().providerRank())
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(1, 25)
                                .boxed()
                                .toList());
    }

    private static PreliminaryCandidateRankingWeights weights(
            double titleLexicalOverlap,
            double snippetLexicalOverlap,
            double providerRankPrior,
            double generatedQueryDiscovery,
            double retrievalPurposeDiversity,
            double titleSpecificity,
            double lowQualityPenalty) {
        return new PreliminaryCandidateRankingWeights(
                titleLexicalOverlap,
                snippetLexicalOverlap,
                providerRankPrior,
                generatedQueryDiscovery,
                retrievalPurposeDiversity,
                titleSpecificity,
                lowQualityPenalty);
    }

    private static AggregatedSearchResult candidate(
            String id,
            String title,
            int providerRank,
            List<String> snippets,
            List<RetrievalMetadata> retrievals) {
        return new AggregatedSearchResult(
                URI.create("https://example.com/" + id),
                title,
                providerRank,
                null,
                snippets,
                retrievals);
    }

    private static RetrievalMetadata retrieval(
            String query,
            String purpose) {
        return new RetrievalMetadata(query, purpose);
    }
}
