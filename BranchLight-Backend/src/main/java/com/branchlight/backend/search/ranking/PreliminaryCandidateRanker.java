package com.branchlight.backend.search.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.aggregation.RetrievalMetadata;
import com.branchlight.backend.search.aggregation.SearchResultAggregator;
import com.branchlight.backend.search.ranking.PreliminaryCandidateScoreBreakdown.ScoreComponent;

public final class PreliminaryCandidateRanker {

    public static final int DEFAULT_MAXIMUM_CANDIDATES = 25;
    public static final int DEFAULT_MINIMUM_DISTINCT_TITLE_TERMS = 2;
    public static final int DEFAULT_MINIMUM_DISTINCT_SNIPPET_TERMS = 4;

    private static final Pattern TERM_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    private static final Comparator<RankedSearchCandidate> RANKING_ORDER =
            Comparator.comparingDouble(RankedSearchCandidate::score)
                    .reversed()
                    .thenComparingInt(ranked ->
                            sortableProviderRank(
                                    ranked.candidate().providerRank()))
                    .thenComparing(ranked ->
                            ranked.candidate().url().toString());

    private final PreliminaryCandidateRankingWeights weights;
    private final int maximumCandidates;
    private final int minimumDistinctTitleTerms;
    private final int minimumDistinctSnippetTerms;

    public PreliminaryCandidateRanker(
            PreliminaryCandidateRankingWeights weights) {
        this(
                weights,
                DEFAULT_MAXIMUM_CANDIDATES,
                DEFAULT_MINIMUM_DISTINCT_TITLE_TERMS,
                DEFAULT_MINIMUM_DISTINCT_SNIPPET_TERMS);
    }

    public PreliminaryCandidateRanker(
            PreliminaryCandidateRankingWeights weights,
            int maximumCandidates) {
        this(
                weights,
                maximumCandidates,
                DEFAULT_MINIMUM_DISTINCT_TITLE_TERMS,
                DEFAULT_MINIMUM_DISTINCT_SNIPPET_TERMS);
    }

    public PreliminaryCandidateRanker(
            PreliminaryCandidateRankingWeights weights,
            int maximumCandidates,
            int minimumDistinctTitleTerms,
            int minimumDistinctSnippetTerms) {
        this.weights = Objects.requireNonNull(
                weights,
                "weights must not be null");
        if (maximumCandidates <= 0) {
            throw new IllegalArgumentException(
                    "maximumCandidates must be greater than zero");
        }
        if (minimumDistinctTitleTerms <= 0) {
            throw new IllegalArgumentException(
                    "minimumDistinctTitleTerms"
                            + " must be greater than zero");
        }
        if (minimumDistinctSnippetTerms <= 0) {
            throw new IllegalArgumentException(
                    "minimumDistinctSnippetTerms"
                            + " must be greater than zero");
        }
        this.maximumCandidates = maximumCandidates;
        this.minimumDistinctTitleTerms =
                minimumDistinctTitleTerms;
        this.minimumDistinctSnippetTerms =
                minimumDistinctSnippetTerms;
    }

    public List<RankedSearchCandidate> rank(
            String originalQuery,
            List<AggregatedSearchResult> candidates) {
        Objects.requireNonNull(
                originalQuery,
                "originalQuery must not be null");
        Objects.requireNonNull(
                candidates,
                "candidates must not be null");
        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "originalQuery must not be blank");
        }

        Set<String> originalQueryTerms =
                distinctTerms(originalQuery);
        var rankedCandidates =
                new ArrayList<RankedSearchCandidate>(
                        candidates.size());

        for (AggregatedSearchResult candidate : candidates) {
            rankedCandidates.add(score(
                    originalQueryTerms,
                    Objects.requireNonNull(
                            candidate,
                            "candidate must not be null")));
        }

        return rankedCandidates.stream()
                .sorted(RANKING_ORDER)
                .limit(maximumCandidates)
                .toList();
    }

    public PreliminaryCandidateRankingWeights weights() {
        return weights;
    }

    public int maximumCandidates() {
        return maximumCandidates;
    }

    public int minimumDistinctTitleTerms() {
        return minimumDistinctTitleTerms;
    }

    public int minimumDistinctSnippetTerms() {
        return minimumDistinctSnippetTerms;
    }

    private RankedSearchCandidate score(
            Set<String> originalQueryTerms,
            AggregatedSearchResult candidate) {
        List<String> titleTerms = terms(candidate.title());
        Set<String> distinctTitleTerms =
                new LinkedHashSet<>(titleTerms);
        Set<String> snippetTerms = candidate.snippets().stream()
                .flatMap(snippet -> terms(snippet).stream())
                .collect(
                        LinkedHashSet::new,
                        Set::add,
                        Set::addAll);

        double titleOverlap = lexicalOverlap(
                originalQueryTerms,
                distinctTitleTerms);
        double snippetOverlap = lexicalOverlap(
                originalQueryTerms,
                snippetTerms);
        double rankPrior = providerRankPrior(
                candidate.providerRank());
        long generatedQueryCount =
                distinctGeneratedQueryCount(candidate);
        long retrievalPurposeCount =
                distinctRetrievalPurposeCount(candidate);
        double specificity = titleSpecificity(
                titleTerms,
                distinctTitleTerms);
        boolean lowQualityTitle =
                distinctTitleTerms.size()
                        < minimumDistinctTitleTerms;
        boolean lowQualitySnippet =
                snippetTerms.size()
                        < minimumDistinctSnippetTerms;
        int lowQualityFieldCount =
                (lowQualityTitle ? 1 : 0)
                        + (lowQualitySnippet ? 1 : 0);

        var breakdown = new PreliminaryCandidateScoreBreakdown(
                reward(
                        titleOverlap,
                        weights.titleLexicalOverlap()),
                reward(
                        snippetOverlap,
                        weights.snippetLexicalOverlap()),
                reward(
                        rankPrior,
                        weights.providerRankPrior()),
                reward(
                        generatedQueryCount,
                        weights.generatedQueryDiscovery()),
                reward(
                        retrievalPurposeCount,
                        weights.retrievalPurposeDiversity()),
                reward(
                        specificity,
                        weights.titleSpecificity()),
                lowQualityTitle,
                lowQualitySnippet,
                penalty(
                        lowQualityFieldCount,
                        weights.lowQualityPenalty()));
        return new RankedSearchCandidate(candidate, breakdown);
    }

    private static List<String> terms(String text) {
        var matcher = TERM_PATTERN.matcher(
                text.toLowerCase(Locale.ROOT));
        var terms = new ArrayList<String>();
        while (matcher.find()) {
            terms.add(matcher.group());
        }
        return terms;
    }

    private static Set<String> distinctTerms(String text) {
        return new LinkedHashSet<>(terms(text));
    }

    private static double lexicalOverlap(
            Set<String> queryTerms,
            Set<String> candidateTerms) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        long matchingTerms = queryTerms.stream()
                .filter(candidateTerms::contains)
                .count();
        return (double) matchingTerms / queryTerms.size();
    }

    private static double providerRankPrior(int providerRank) {
        if (providerRank <= 0) {
            return 0.0;
        }
        return 1.0 / providerRank;
    }

    private static long distinctGeneratedQueryCount(
            AggregatedSearchResult candidate) {
        return candidate.retrievals().stream()
                .filter(retrieval -> !isOriginalQuery(retrieval))
                .map(RetrievalMetadata::query)
                .distinct()
                .count();
    }

    private static long distinctRetrievalPurposeCount(
            AggregatedSearchResult candidate) {
        return candidate.retrievals().stream()
                .map(RetrievalMetadata::purpose)
                .distinct()
                .count();
    }

    private static boolean isOriginalQuery(
            RetrievalMetadata retrieval) {
        return SearchResultAggregator.ORIGINAL_QUERY_PURPOSE.equals(
                retrieval.purpose());
    }

    private static double titleSpecificity(
            List<String> titleTerms,
            Set<String> distinctTitleTerms) {
        if (titleTerms.isEmpty()) {
            return 0.0;
        }
        double uniqueness =
                (double) distinctTitleTerms.size()
                        / titleTerms.size();
        double descriptiveDetail =
                (double) distinctTitleTerms.size()
                        / (distinctTitleTerms.size() + 1.0);
        return uniqueness * descriptiveDetail;
    }

    private static ScoreComponent reward(
            double rawValue,
            double weight) {
        return new ScoreComponent(
                rawValue,
                weight,
                rawValue * weight);
    }

    private static ScoreComponent penalty(
            double rawValue,
            double weight) {
        return new ScoreComponent(
                rawValue,
                weight,
                -rawValue * weight);
    }

    private static int sortableProviderRank(int providerRank) {
        return providerRank > 0
                ? providerRank
                : Integer.MAX_VALUE;
    }
}
