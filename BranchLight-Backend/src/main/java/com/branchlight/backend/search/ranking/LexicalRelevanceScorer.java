package com.branchlight.backend.search.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.branchlight.backend.search.content.ExtractedBlock;
import com.branchlight.backend.search.content.Passage;
import com.branchlight.backend.search.ranking.RelevanceScoreBreakdown.ScoreComponent;

public final class LexicalRelevanceScorer implements RelevanceScorer {

    public static final int DEFAULT_MAXIMUM_CANDIDATES = 15;
    public static final int DEFAULT_TOP_PASSAGES = 3;

    private static final double SUPPORTING_VARIANT_BOOST = 0.05;
    private static final Pattern TERM_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    private final RelevanceWeights weights;
    private final int maximumCandidates;
    private final int topPassageCount;

    public LexicalRelevanceScorer() {
        this(RelevanceWeights.DEFAULTS);
    }

    public LexicalRelevanceScorer(RelevanceWeights weights) {
        this(
                weights,
                DEFAULT_MAXIMUM_CANDIDATES,
                DEFAULT_TOP_PASSAGES);
    }

    public LexicalRelevanceScorer(
            RelevanceWeights weights,
            int maximumCandidates,
            int topPassageCount) {
        this.weights = Objects.requireNonNull(
                weights,
                "weights must not be null");
        if (maximumCandidates <= 0) {
            throw new IllegalArgumentException(
                    "maximumCandidates must be greater than zero");
        }
        if (topPassageCount <= 0) {
            throw new IllegalArgumentException(
                    "topPassageCount must be greater than zero");
        }
        this.maximumCandidates = maximumCandidates;
        this.topPassageCount = topPassageCount;
    }

    @Override
    public List<ScoredCandidateDocument> score(
            RelevanceQuery query,
            List<CandidateDocument> candidates) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        List<String> originalTerms = terms(query.originalQuery());
        List<List<String>> supportingTerms =
                query.supportingQueryVariants().stream()
                        .map(LexicalRelevanceScorer::terms)
                        .toList();
        var scored = new ArrayList<ScoredCandidateDocument>(
                candidates.size());
        for (CandidateDocument candidate : candidates) {
            scored.add(scoreCandidate(
                    originalTerms,
                    supportingTerms,
                    Objects.requireNonNull(
                            candidate,
                            "candidate must not be null")));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(
                        ScoredCandidateDocument::overallPageRelevance)
                        .reversed()
                        .thenComparingInt(result -> sortableProviderRank(
                                result.candidate()
                                        .searchResult()
                                        .providerRank()))
                        .thenComparing(result ->
                                result.candidate()
                                        .document()
                                        .documentId()))
                .limit(maximumCandidates)
                .toList();
    }

    public RelevanceWeights weights() {
        return weights;
    }

    public int maximumCandidates() {
        return maximumCandidates;
    }

    public int topPassageCount() {
        return topPassageCount;
    }

    private ScoredCandidateDocument scoreCandidate(
            List<String> originalTerms,
            List<List<String>> supportingTerms,
            CandidateDocument candidate) {
        double titleScore = relevance(
                originalTerms,
                supportingTerms,
                terms(candidate.searchResult().title()));
        double snippetScore = candidate.searchResult().snippets().stream()
                .map(LexicalRelevanceScorer::terms)
                .mapToDouble(text -> relevance(
                        originalTerms,
                        supportingTerms,
                        text))
                .max()
                .orElse(0.0);
        double headingScore = candidate.document().blocks().stream()
                .filter(block -> block.type()
                        == ExtractedBlock.Type.HEADING)
                .map(ExtractedBlock::text)
                .map(LexicalRelevanceScorer::terms)
                .mapToDouble(text -> relevance(
                        originalTerms,
                        supportingTerms,
                        text))
                .max()
                .orElse(0.0);

        List<TopRelevantPassage> scoredPassages = candidate.passages().stream()
                .map(passage -> new TopRelevantPassage(
                        passage,
                        relevance(
                                originalTerms,
                                supportingTerms,
                                terms(passage.text()))))
                .sorted(Comparator.comparingDouble(
                        TopRelevantPassage::relevance)
                        .reversed()
                        .thenComparingInt(scoredPassage ->
                                scoredPassage.passage()
                                        .position()
                                        .startOffset()))
                .toList();
        double strongestPassage = passageScore(scoredPassages, 0);
        double secondStrongestPassage = passageScore(scoredPassages, 1);
        List<TopRelevantPassage> topRelevantPassages = scoredPassages.stream()
                .filter(passage -> passage.relevance() > 0.0)
                .limit(topPassageCount)
                .toList();

        List<List<String>> documentFields = documentFields(candidate);
        double keywordCoverage = keywordCoverage(
                originalTerms,
                union(documentFields));
        double phraseCoverage = phraseCoverage(
                originalTerms,
                documentFields);
        double providerRankPrior = providerRankPrior(
                candidate.searchResult().providerRank());

        var breakdown = new RelevanceScoreBreakdown(
                component(titleScore, weights.title()),
                component(snippetScore, weights.snippet()),
                component(headingScore, weights.heading()),
                component(
                        strongestPassage,
                        weights.strongestPassage()),
                component(
                        secondStrongestPassage,
                        weights.secondStrongestPassage()),
                component(
                        keywordCoverage,
                        weights.keywordCoverage()),
                component(phraseCoverage, weights.phraseCoverage()),
                component(
                        providerRankPrior,
                        weights.providerRankPrior()));
        return new ScoredCandidateDocument(
                candidate,
                breakdown.totalScore(),
                titleScore,
                snippetScore,
                topRelevantPassages,
                breakdown);
    }

    private List<List<String>> documentFields(CandidateDocument candidate) {
        var fields = new ArrayList<List<String>>();
        fields.add(terms(candidate.searchResult().title()));
        candidate.searchResult().snippets().stream()
                .map(LexicalRelevanceScorer::terms)
                .forEach(fields::add);
        candidate.document().blocks().stream()
                .filter(block -> block.type()
                        == ExtractedBlock.Type.HEADING)
                .map(ExtractedBlock::text)
                .map(LexicalRelevanceScorer::terms)
                .forEach(fields::add);
        candidate.passages().stream()
                .map(Passage::text)
                .map(LexicalRelevanceScorer::terms)
                .forEach(fields::add);
        return List.copyOf(fields);
    }

    private ScoreComponent component(double score, double weight) {
        double normalizedWeight = weight / weights.total();
        return new ScoreComponent(
                score,
                normalizedWeight,
                score * normalizedWeight);
    }

    private static double relevance(
            List<String> originalTerms,
            List<List<String>> supportingTerms,
            List<String> candidateTerms) {
        double originalRelevance = lexicalRelevance(
                originalTerms,
                candidateTerms);
        if (originalRelevance == 0.0) {
            return 0.0;
        }
        double supportingRelevance = supportingTerms.stream()
                .mapToDouble(variant -> lexicalRelevance(
                        variant,
                        candidateTerms))
                .max()
                .orElse(0.0);
        return Math.min(
                1.0,
                originalRelevance
                        + originalRelevance
                                * (1.0 - originalRelevance)
                                * SUPPORTING_VARIANT_BOOST
                                * supportingRelevance);
    }

    private static double lexicalRelevance(
            List<String> queryTerms,
            List<String> candidateTerms) {
        return 0.7 * keywordCoverage(
                queryTerms,
                new LinkedHashSet<>(candidateTerms))
                + 0.3 * phraseCoverage(
                        queryTerms,
                        List.of(candidateTerms));
    }

    private static double keywordCoverage(
            List<String> queryTerms,
            Set<String> candidateTerms) {
        Set<String> distinctQueryTerms =
                new LinkedHashSet<>(queryTerms);
        if (distinctQueryTerms.isEmpty()) {
            return 0.0;
        }
        long matches = distinctQueryTerms.stream()
                .filter(candidateTerms::contains)
                .count();
        return (double) matches / distinctQueryTerms.size();
    }

    private static double phraseCoverage(
            List<String> queryTerms,
            List<List<String>> candidateFields) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        if (queryTerms.size() == 1) {
            return candidateFields.stream()
                    .flatMap(List::stream)
                    .anyMatch(queryTerms.get(0)::equals)
                            ? 1.0
                            : 0.0;
        }

        int matchingPairs = 0;
        for (int index = 0; index < queryTerms.size() - 1; index++) {
            String first = queryTerms.get(index);
            String second = queryTerms.get(index + 1);
            boolean matched = candidateFields.stream()
                    .anyMatch(field -> containsPair(
                            field,
                            first,
                            second));
            if (matched) {
                matchingPairs++;
            }
        }
        return (double) matchingPairs / (queryTerms.size() - 1);
    }

    private static boolean containsPair(
            List<String> terms,
            String first,
            String second) {
        for (int index = 0; index < terms.size() - 1; index++) {
            if (terms.get(index).equals(first)
                    && terms.get(index + 1).equals(second)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> union(List<List<String>> fields) {
        var terms = new LinkedHashSet<String>();
        fields.forEach(terms::addAll);
        return terms;
    }

    private static List<String> terms(String text) {
        var matcher = TERM_PATTERN.matcher(
                text.toLowerCase(Locale.ROOT));
        var terms = new ArrayList<String>();
        while (matcher.find()) {
            terms.add(matcher.group());
        }
        return List.copyOf(terms);
    }

    private static double providerRankPrior(int providerRank) {
        return providerRank > 0 ? 1.0 / providerRank : 0.0;
    }

    private static double passageScore(
            List<TopRelevantPassage> passages,
            int index) {
        return passages.size() > index
                ? passages.get(index).relevance()
                : 0.0;
    }

    private static int sortableProviderRank(int providerRank) {
        return providerRank > 0
                ? providerRank
                : Integer.MAX_VALUE;
    }
}