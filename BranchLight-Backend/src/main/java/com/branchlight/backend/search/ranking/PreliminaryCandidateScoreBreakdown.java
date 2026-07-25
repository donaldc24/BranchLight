package com.branchlight.backend.search.ranking;

import java.util.Objects;

public record PreliminaryCandidateScoreBreakdown(
        ScoreComponent titleLexicalOverlap,
        ScoreComponent snippetLexicalOverlap,
        ScoreComponent providerRankPrior,
        ScoreComponent generatedQueryDiscovery,
        ScoreComponent retrievalPurposeDiversity,
        ScoreComponent titleSpecificity,
        boolean lowQualityTitle,
        boolean lowQualitySnippet,
        ScoreComponent lowQualityMetadataPenalty) {

    public PreliminaryCandidateScoreBreakdown {
        Objects.requireNonNull(
                titleLexicalOverlap,
                "titleLexicalOverlap must not be null");
        Objects.requireNonNull(
                snippetLexicalOverlap,
                "snippetLexicalOverlap must not be null");
        Objects.requireNonNull(
                providerRankPrior,
                "providerRankPrior must not be null");
        Objects.requireNonNull(
                generatedQueryDiscovery,
                "generatedQueryDiscovery must not be null");
        Objects.requireNonNull(
                retrievalPurposeDiversity,
                "retrievalPurposeDiversity must not be null");
        Objects.requireNonNull(
                titleSpecificity,
                "titleSpecificity must not be null");
        Objects.requireNonNull(
                lowQualityMetadataPenalty,
                "lowQualityMetadataPenalty must not be null");
    }

    public int distinctGeneratedQueryCount() {
        return (int) generatedQueryDiscovery.rawValue();
    }

    public int distinctRetrievalPurposeCount() {
        return (int) retrievalPurposeDiversity.rawValue();
    }

    public double totalScore() {
        return titleLexicalOverlap.contribution()
                + snippetLexicalOverlap.contribution()
                + providerRankPrior.contribution()
                + generatedQueryDiscovery.contribution()
                + retrievalPurposeDiversity.contribution()
                + titleSpecificity.contribution()
                + lowQualityMetadataPenalty.contribution();
    }

    public record ScoreComponent(
            double rawValue,
            double weight,
            double contribution) {

        public ScoreComponent {
            if (!Double.isFinite(rawValue) || rawValue < 0.0) {
                throw new IllegalArgumentException(
                        "rawValue must be finite and non-negative");
            }
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException(
                        "weight must be finite and non-negative");
            }
            if (!Double.isFinite(contribution)) {
                throw new IllegalArgumentException(
                        "contribution must be finite");
            }
        }
    }
}
