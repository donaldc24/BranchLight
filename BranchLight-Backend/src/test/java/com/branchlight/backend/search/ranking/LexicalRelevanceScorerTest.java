package com.branchlight.backend.search.ranking;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.content.ExtractedBlock;
import com.branchlight.backend.search.content.ExtractedDocument;
import com.branchlight.backend.search.content.Passage;
import com.branchlight.backend.search.content.SourcePosition;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalRelevanceScorerTest {

    private final LexicalRelevanceScorer scorer =
            new LexicalRelevanceScorer();

    @Test
    void returnsNormalizedScoresBreakdownAndTopPassages() {
        CandidateDocument candidate = candidate(
                "relevant",
                "Distributed systems latency guide",
                4,
                List.of("Reducing latency in distributed systems"),
                List.of("Performance", "Latency controls"),
                List.of(
                        "Distributed systems latency depends on network design.",
                        "Latency measurements guide capacity planning.",
                        "This unrelated appendix covers formatting."));

        ScoredCandidateDocument result = scorer.score(
                "distributed systems latency",
                List.of(candidate)).get(0);

        assertThat(result.overallPageRelevance()).isBetween(0.0, 1.0);
        assertThat(result.titleScore()).isEqualTo(1.0);
        assertThat(result.snippetScore()).isEqualTo(0.85);
        assertThat(result.topRelevantPassages())
                .extracting(top -> top.passage().text())
                .containsExactly(
                        "Distributed systems latency depends on network design.",
                        "Latency measurements guide capacity planning.");
        assertThat(result.scoreBreakdown().totalScore())
                .isEqualTo(result.overallPageRelevance());
        assertThat(result.scoreBreakdown().strongestPassage().score())
                .isGreaterThan(result.scoreBreakdown()
                        .secondStrongestPassage()
                        .score());
        assertThat(result.scoreBreakdown().providerRankPrior().score())
                .isEqualTo(0.25);
    }

    @Test
    void keepsTheOriginalQueryAsTheRequiredRelevanceTarget() {
        CandidateDocument originalMatch = candidate(
                "original",
                "Distributed systems latency",
                10,
                List.of(),
                List.of(),
                List.of("Distributed systems reduce latency."));
        CandidateDocument variantOnlyMatch = candidate(
                "variant",
                "Cake decorating techniques",
                1,
                List.of("Cake decorating techniques and tools"),
                List.of("Cake decorating"),
                List.of("Cake decorating techniques create patterns."));
        RelevanceQuery query = new RelevanceQuery(
                "distributed systems latency",
                List.of("cake decorating techniques"));

        List<ScoredCandidateDocument> results = scorer.score(
                query,
                List.of(variantOnlyMatch, originalMatch));

        assertThat(results.get(0).candidate()).isEqualTo(originalMatch);
        assertThat(results.get(1).overallPageRelevance())
                .isLessThanOrEqualTo(0.03);
        assertThat(results.get(1).titleScore()).isZero();
        assertThat(results.get(1).snippetScore()).isZero();
        assertThat(results.get(1).topRelevantPassages()).isEmpty();
    }

    @Test
    void combinesStrongestAndSecondStrongestPassageSignals() {
        CandidateDocument candidate = candidate(
                "two-passages",
                "Neutral title",
                0,
                List.of(),
                List.of(),
                List.of(
                        "resilient cache invalidation patterns",
                        "cache invalidation guidance",
                        "unrelated material"));

        ScoredCandidateDocument result = scorer.score(
                "resilient cache invalidation",
                List.of(candidate)).get(0);

        assertThat(result.scoreBreakdown().strongestPassage().score())
                .isPositive();
        assertThat(result.scoreBreakdown()
                .secondStrongestPassage()
                .score()).isPositive();
        assertThat(result.topRelevantPassages()).hasSize(2);
    }

    @Test
    void honorsConfigurableWeights() {
        RelevanceWeights titleOnly = new RelevanceWeights(
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0);
        var titleScorer = new LexicalRelevanceScorer(titleOnly);
        CandidateDocument titleMatch = candidate(
                "title",
                "resilient cache invalidation",
                20,
                List.of(),
                List.of(),
                List.of("unrelated passage"));
        CandidateDocument passageMatch = candidate(
                "passage",
                "unrelated title",
                1,
                List.of(),
                List.of(),
                List.of("resilient cache invalidation"));

        List<ScoredCandidateDocument> results = titleScorer.score(
                "resilient cache invalidation",
                List.of(passageMatch, titleMatch));

        assertThat(results.get(0).candidate()).isEqualTo(titleMatch);
        assertThat(results.get(0).overallPageRelevance()).isEqualTo(1.0);
        assertThat(results.get(0)
                .scoreBreakdown()
                .title()
                .normalizedWeight()).isEqualTo(1.0);
    }

    @Test
    void keepsTheTopFifteenCandidatesByDefault() {
        List<CandidateDocument> candidates = IntStream.rangeClosed(1, 20)
                .mapToObj(rank -> candidate(
                        "candidate-" + rank,
                        "query",
                        rank,
                        List.of(),
                        List.of(),
                        List.of("query")))
                .toList();

        List<ScoredCandidateDocument> results = scorer.score(
                "query",
                candidates);

        assertThat(scorer.maximumCandidates()).isEqualTo(15);
        assertThat(results).hasSize(15);
        assertThat(results)
                .extracting(result -> result.candidate()
                        .searchResult()
                        .providerRank())
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(1, 15).boxed().toList());
    }

    private static CandidateDocument candidate(
            String id,
            String title,
            int providerRank,
            List<String> snippets,
            List<String> headings,
            List<String> passageTexts) {
        var blocks = new ArrayList<ExtractedBlock>();
        int offset = 0;
        for (String heading : headings) {
            blocks.add(ExtractedBlock.heading(
                    1,
                    heading,
                    new SourcePosition(offset, offset + heading.length())));
            offset += heading.length() + 1;
        }
        var passages = new ArrayList<Passage>();
        for (String text : passageTexts) {
            SourcePosition position = new SourcePosition(
                    offset,
                    offset + text.length());
            blocks.add(ExtractedBlock.paragraph(text, position));
            passages.add(new Passage(
                    id,
                    headings,
                    text,
                    position,
                    text.split("\\s+").length));
            offset += text.length() + 1;
        }
        var searchResult = new AggregatedSearchResult(
                URI.create("https://example.test/" + id),
                title,
                providerRank,
                null,
                snippets,
                List.of());
        return new CandidateDocument(
                searchResult,
                new ExtractedDocument(id, blocks),
                passages);
    }
}