package com.branchlight.backend.search.scoring;

import java.util.List;
import java.util.Objects;

public record RoleScoreBreakdown(
        ScoreComponent originalQueryRelevance,
        ScoreComponent roleFeatures,
        ScoreComponent provenanceAndQuality,
        ScoreComponent providerRankPrior,
        ScoreComponent freshness,
        ScoreComponent retrievalPurposeMatch,
        RiskPenalty riskPenalty) {

    public RoleScoreBreakdown {
        for (ScoreComponent component : List.of(
                originalQueryRelevance,
                roleFeatures,
                provenanceAndQuality,
                providerRankPrior,
                freshness,
                retrievalPurposeMatch)) {
            Objects.requireNonNull(
                    component,
                    "score components must not be null");
        }
        Objects.requireNonNull(
                riskPenalty,
                "riskPenalty must not be null");
    }

    public double finalScore() {
        double positiveScore = originalQueryRelevance.contribution()
                + roleFeatures.contribution()
                + provenanceAndQuality.contribution()
                + providerRankPrior.contribution()
                + freshness.contribution()
                + retrievalPurposeMatch.contribution();
        return clamp(positiveScore - riskPenalty.contribution());
    }

    public record ScoreComponent(
            double score,
            double normalizedWeight,
            double contribution,
            boolean available) {

        public ScoreComponent {
            requireNormalized(score, "score");
            requireNormalized(normalizedWeight, "normalizedWeight");
            requireNormalized(contribution, "contribution");
            if (!available
                    && (score != 0.0
                            || normalizedWeight != 0.0
                            || contribution != 0.0)) {
                throw new IllegalArgumentException(
                        "unavailable components must contain zero values");
            }
        }
    }

    public record RiskPenalty(
            double score,
            double weight,
            double contribution) {

        public RiskPenalty {
            requireNormalized(score, "score");
            requireNormalized(weight, "weight");
            requireNormalized(contribution, "contribution");
        }
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0.0 and 1.0");
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}