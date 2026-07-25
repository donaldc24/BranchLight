package com.branchlight.backend.search.ranking;

import java.util.List;
import java.util.Objects;

public record RelevanceScoreBreakdown(
        ScoreComponent title,
        ScoreComponent snippet,
        ScoreComponent heading,
        ScoreComponent strongestPassage,
        ScoreComponent secondStrongestPassage,
        ScoreComponent keywordCoverage,
        ScoreComponent phraseCoverage,
        ScoreComponent providerRankPrior) {

    public RelevanceScoreBreakdown {
        for (ScoreComponent component : List.of(
                title,
                snippet,
                heading,
                strongestPassage,
                secondStrongestPassage,
                keywordCoverage,
                phraseCoverage,
                providerRankPrior)) {
            Objects.requireNonNull(
                    component,
                    "score components must not be null");
        }
    }

    public double totalScore() {
        return title.contribution()
                + snippet.contribution()
                + heading.contribution()
                + strongestPassage.contribution()
                + secondStrongestPassage.contribution()
                + keywordCoverage.contribution()
                + phraseCoverage.contribution()
                + providerRankPrior.contribution();
    }

    public record ScoreComponent(
            double score,
            double normalizedWeight,
            double contribution) {

        public ScoreComponent {
            TopRelevantPassage.requireNormalized(score, "score");
            TopRelevantPassage.requireNormalized(
                    normalizedWeight,
                    "normalizedWeight");
            TopRelevantPassage.requireNormalized(
                    contribution,
                    "contribution");
        }
    }
}