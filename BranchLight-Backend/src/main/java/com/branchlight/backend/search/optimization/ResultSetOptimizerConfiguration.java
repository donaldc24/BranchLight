package com.branchlight.backend.search.optimization;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;

public record ResultSetOptimizerConfiguration(
        Map<SearchRole, Double> minimumRoleScores,
        double repeatedRootDomainPenalty,
        double similarTitlePenalty,
        double similarSnippetPenalty,
        double identicalRetrievalPathPenalty,
        double titleSimilarityThreshold,
        double snippetSimilarityThreshold) {

    public static final ResultSetOptimizerConfiguration DEFAULTS =
            new ResultSetOptimizerConfiguration(
                    uniformThresholds(0.35),
                    0.08,
                    0.06,
                    0.05,
                    0.03,
                    0.80,
                    0.80);

    public ResultSetOptimizerConfiguration {
        Objects.requireNonNull(
                minimumRoleScores,
                "minimumRoleScores must not be null");
        var thresholds = new EnumMap<SearchRole, Double>(SearchRole.class);
        thresholds.putAll(minimumRoleScores);
        if (thresholds.size() != SearchRole.values().length) {
            throw new IllegalArgumentException(
                    "minimumRoleScores must contain every search role");
        }
        thresholds.forEach((role, threshold) -> requireNormalized(
                threshold,
                "minimumRoleScores[" + role + "]"));
        minimumRoleScores = Collections.unmodifiableMap(thresholds);
        requireNonNegative(
                repeatedRootDomainPenalty,
                "repeatedRootDomainPenalty");
        requireNonNegative(similarTitlePenalty, "similarTitlePenalty");
        requireNonNegative(
                similarSnippetPenalty,
                "similarSnippetPenalty");
        requireNonNegative(
                identicalRetrievalPathPenalty,
                "identicalRetrievalPathPenalty");
        requireNormalized(
                titleSimilarityThreshold,
                "titleSimilarityThreshold");
        requireNormalized(
                snippetSimilarityThreshold,
                "snippetSimilarityThreshold");
    }

    public double minimumScore(SearchRole role) {
        return minimumRoleScores.get(Objects.requireNonNull(
                role,
                "role must not be null"));
    }

    public static Map<SearchRole, Double> uniformThresholds(
            double threshold) {
        requireNormalized(threshold, "threshold");
        var thresholds = new EnumMap<SearchRole, Double>(SearchRole.class);
        for (SearchRole role : SearchRole.values()) {
            thresholds.put(role, threshold);
        }
        return thresholds;
    }

    private static void requireNormalized(Double value, String name) {
        if (value == null
                || !Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0.0 and 1.0");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}