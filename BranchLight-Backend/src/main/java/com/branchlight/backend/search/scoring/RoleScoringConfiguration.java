package com.branchlight.backend.search.scoring;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.features.SourceFeature;

public record RoleScoringConfiguration(
        double relevanceWeight,
        double roleFeaturesWeight,
        double provenanceQualityWeight,
        double providerRankPriorWeight,
        double freshnessWeight,
        double retrievalPurposeMatchWeight,
        double riskPenaltyWeight,
        long freshnessHorizonDays,
        Map<SearchRole, Map<SourceFeature, Double>> roleFeatureWeights,
        Map<SourceFeature, Double> provenanceFeatureWeights,
        Map<SourceFeature, Double> qualityFeatureWeights,
        Map<SourceFeature, Double> riskFeatureWeights) {

    public static final RoleScoringConfiguration DEFAULTS = defaults();

    public RoleScoringConfiguration {
        requireNonNegative(relevanceWeight, "relevanceWeight");
        requireNonNegative(roleFeaturesWeight, "roleFeaturesWeight");
        requireNonNegative(
                provenanceQualityWeight,
                "provenanceQualityWeight");
        requireNonNegative(
                providerRankPriorWeight,
                "providerRankPriorWeight");
        requireNonNegative(freshnessWeight, "freshnessWeight");
        requireNonNegative(
                retrievalPurposeMatchWeight,
                "retrievalPurposeMatchWeight");
        if (!Double.isFinite(riskPenaltyWeight)
                || riskPenaltyWeight < 0.0
                || riskPenaltyWeight > 1.0) {
            throw new IllegalArgumentException(
                    "riskPenaltyWeight must be between 0.0 and 1.0");
        }
        double positiveWeight = relevanceWeight
                + roleFeaturesWeight
                + provenanceQualityWeight
                + providerRankPriorWeight
                + freshnessWeight
                + retrievalPurposeMatchWeight;
        if (positiveWeight <= 0.0) {
            throw new IllegalArgumentException(
                    "at least one positive component weight is required");
        }
        if (retrievalPurposeMatchWeight > positiveWeight * 0.10) {
            throw new IllegalArgumentException(
                    "retrievalPurposeMatchWeight must not exceed 10% of positive weight");
        }
        if (freshnessHorizonDays <= 0) {
            throw new IllegalArgumentException(
                    "freshnessHorizonDays must be greater than zero");
        }
        roleFeatureWeights = immutableRoleFeatureWeights(
                roleFeatureWeights);
        provenanceFeatureWeights = immutableFeatureWeights(
                provenanceFeatureWeights,
                "provenanceFeatureWeights");
        qualityFeatureWeights = immutableFeatureWeights(
                qualityFeatureWeights,
                "qualityFeatureWeights");
        riskFeatureWeights = immutableFeatureWeights(
                riskFeatureWeights,
                "riskFeatureWeights");
    }

    public Map<SourceFeature, Double> featuresFor(SearchRole role) {
        return roleFeatureWeights.get(Objects.requireNonNull(
                role,
                "role must not be null"));
    }

    private static RoleScoringConfiguration defaults() {
        var roleFeatures = new EnumMap<SearchRole,
                Map<SourceFeature, Double>>(SearchRole.class);
        roleFeatures.put(SearchRole.AUTHORITATIVE, weights(
                SourceFeature.IDENTIFIED_AUTHOR,
                SourceFeature.IDENTIFIED_PUBLISHER,
                SourceFeature.PUBLICATION_DATE_PRESENT,
                SourceFeature.MODIFIED_DATE_PRESENT,
                SourceFeature.REFERENCES_OR_CITATIONS_PRESENT,
                SourceFeature.STRUCTURED_METADATA_PRESENT,
                SourceFeature.ORIGINAL_OR_FIRST_PARTY_MATERIAL));
        roleFeatures.put(SearchRole.EXPLANATORY, weights(
                SourceFeature.DEFINITIONS_PRESENT,
                SourceFeature.EXAMPLES_PRESENT,
                SourceFeature.EXPLANATORY_HEADINGS,
                SourceFeature.SUMMARY_OR_INTRODUCTION_PRESENT,
                SourceFeature.CONCEPTUAL_PROGRESSION,
                SourceFeature.READABILITY_ESTIMATE));
        roleFeatures.put(SearchRole.PRACTICAL, weights(
                SourceFeature.ORDERED_STEPS,
                SourceFeature.CODE_BLOCKS,
                SourceFeature.WORKED_EXAMPLES,
                SourceFeature.CONFIGURATION_EXAMPLES,
                SourceFeature.CHECKLISTS,
                SourceFeature.EXPECTED_RESULTS,
                SourceFeature.DOWNLOADABLE_OR_REPOSITORY_LINKS));
        roleFeatures.put(SearchRole.CRITICAL, weights(
                SourceFeature.LIMITATIONS_SECTIONS,
                SourceFeature.RISKS,
                SourceFeature.DRAWBACKS,
                SourceFeature.COUNTERARGUMENTS,
                SourceFeature.TRADEOFFS,
                SourceFeature.FAILURE_CASES,
                SourceFeature.UNCERTAINTY_LANGUAGE,
                SourceFeature.METHODOLOGY_LIMITATIONS));
        roleFeatures.put(SearchRole.HUMAN_DISCUSSION, weights(
                SourceFeature.MULTIPLE_PARTICIPANTS,
                SourceFeature.REPLIES_OR_COMMENTS,
                SourceFeature.QUESTION_AND_ANSWER_STRUCTURE,
                SourceFeature.FIRST_PERSON_EXPERIENCE,
                SourceFeature.CONVERSATION_DEPTH,
                SourceFeature.DIFFERING_VIEWPOINTS));

        return new RoleScoringConfiguration(
                0.35,
                0.35,
                0.13,
                0.05,
                0.10,
                0.10,
                0.30,
                1_825,
                roleFeatures,
                weights(
                        SourceFeature.IDENTIFIED_AUTHOR,
                        SourceFeature.IDENTIFIED_PUBLISHER,
                        SourceFeature.PUBLICATION_DATE_PRESENT,
                        SourceFeature.MODIFIED_DATE_PRESENT,
                        SourceFeature.REFERENCES_OR_CITATIONS_PRESENT,
                        SourceFeature.STRUCTURED_METADATA_PRESENT,
                        SourceFeature.ORIGINAL_OR_FIRST_PARTY_MATERIAL),
                weights(
                        SourceFeature.THIN_CONTENT,
                        SourceFeature.DUPLICATED_TEXT,
                        SourceFeature.EXCESSIVE_AFFILIATE_LINKS,
                        SourceFeature.EXCESSIVE_ADVERTISEMENTS,
                        SourceFeature.MISSING_ATTRIBUTION,
                        SourceFeature.SENSATIONAL_TITLE,
                        SourceFeature.UNSUPPORTED_CERTAINTY,
                        SourceFeature.KEYWORD_STUFFING),
                weights(
                        SourceFeature.THIN_CONTENT,
                        SourceFeature.DUPLICATED_TEXT,
                        SourceFeature.EXCESSIVE_AFFILIATE_LINKS,
                        SourceFeature.EXCESSIVE_ADVERTISEMENTS,
                        SourceFeature.MISSING_ATTRIBUTION,
                        SourceFeature.SENSATIONAL_TITLE,
                        SourceFeature.UNSUPPORTED_CERTAINTY,
                        SourceFeature.KEYWORD_STUFFING));
    }

    private static Map<SourceFeature, Double> weights(
            SourceFeature... features) {
        var weights = new EnumMap<SourceFeature, Double>(
                SourceFeature.class);
        for (SourceFeature feature : features) {
            weights.put(feature, 1.0);
        }
        return weights;
    }

    private static Map<SearchRole, Map<SourceFeature, Double>>
            immutableRoleFeatureWeights(
                    Map<SearchRole, Map<SourceFeature, Double>> values) {
        Objects.requireNonNull(
                values,
                "roleFeatureWeights must not be null");
        var copy = new EnumMap<SearchRole,
                Map<SourceFeature, Double>>(SearchRole.class);
        values.forEach((role, weights) -> copy.put(
                role,
                immutableFeatureWeights(
                        weights,
                        "roleFeatureWeights[" + role + "]")));
        if (copy.size() != SearchRole.values().length) {
            throw new IllegalArgumentException(
                    "roleFeatureWeights must contain every search role");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<SourceFeature, Double> immutableFeatureWeights(
            Map<SourceFeature, Double> values,
            String name) {
        Objects.requireNonNull(values, name + " must not be null");
        var copy = new EnumMap<SourceFeature, Double>(SourceFeature.class);
        values.forEach((feature, weight) -> {
            Objects.requireNonNull(feature, name + " feature must not be null");
            Objects.requireNonNull(weight, name + " weight must not be null");
            requireNonNegative(weight, name + " weight");
            copy.put(feature, weight);
        });
        if (copy.isEmpty() || copy.values().stream()
                .allMatch(weight -> weight == 0.0)) {
            throw new IllegalArgumentException(
                    name + " must contain a positive weight");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireNonNegative(double weight, String name) {
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}