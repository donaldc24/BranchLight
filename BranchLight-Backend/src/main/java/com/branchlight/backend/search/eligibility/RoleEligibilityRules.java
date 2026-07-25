package com.branchlight.backend.search.eligibility;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.features.SourceFeature;

public record RoleEligibilityRules(
        Map<SearchRole, RoleEligibilityRule> rules) {

    public static final RoleEligibilityRules DEFAULTS = defaultRules();

    public RoleEligibilityRules {
        Objects.requireNonNull(rules, "rules must not be null");
        var copy = new EnumMap<SearchRole, RoleEligibilityRule>(
                SearchRole.class);
        copy.putAll(rules);
        if (copy.size() != SearchRole.values().length) {
            throw new IllegalArgumentException(
                    "rules must contain every search role");
        }
        if (copy.containsValue(null)) {
            throw new IllegalArgumentException(
                    "role rules must not be null");
        }
        rules = Collections.unmodifiableMap(copy);
    }

    public RoleEligibilityRule rule(SearchRole role) {
        return rules.get(Objects.requireNonNull(
                role,
                "role must not be null"));
    }

    private static RoleEligibilityRules defaultRules() {
        var rules = new EnumMap<SearchRole, RoleEligibilityRule>(
                SearchRole.class);
        rules.put(SearchRole.AUTHORITATIVE, rule(
                0.25,
                0.38,
                2,
                support(
                        SourceFeature.IDENTIFIED_AUTHOR,
                        SourceFeature.IDENTIFIED_PUBLISHER,
                        SourceFeature.REFERENCES_OR_CITATIONS_PRESENT,
                        SourceFeature.STRUCTURED_METADATA_PRESENT,
                        SourceFeature.ORIGINAL_OR_FIRST_PARTY_MATERIAL),
                reject(
                        SourceFeature.THIN_CONTENT,
                        SourceFeature.MISSING_ATTRIBUTION,
                        SourceFeature.UNSUPPORTED_CERTAINTY)));
        rules.put(SearchRole.EXPLANATORY, rule(
                0.25,
                0.34,
                2,
                support(
                        SourceFeature.DEFINITIONS_PRESENT,
                        SourceFeature.EXAMPLES_PRESENT,
                        SourceFeature.EXPLANATORY_HEADINGS,
                        SourceFeature.SUMMARY_OR_INTRODUCTION_PRESENT,
                        SourceFeature.CONCEPTUAL_PROGRESSION,
                        SourceFeature.READABILITY_ESTIMATE),
                reject(
                        SourceFeature.THIN_CONTENT,
                        SourceFeature.KEYWORD_STUFFING)));
        rules.put(SearchRole.PRACTICAL, rule(
                0.20,
                0.30,
                1,
                support(
                        SourceFeature.ORDERED_STEPS,
                        SourceFeature.CODE_BLOCKS,
                        SourceFeature.WORKED_EXAMPLES,
                        SourceFeature.CONFIGURATION_EXAMPLES,
                        SourceFeature.CHECKLISTS,
                        SourceFeature.EXPECTED_RESULTS,
                        SourceFeature.DOWNLOADABLE_OR_REPOSITORY_LINKS),
                reject(
                        SourceFeature.THIN_CONTENT,
                        SourceFeature.KEYWORD_STUFFING)));
        rules.put(SearchRole.CRITICAL, rule(
                0.20,
                0.30,
                1,
                support(
                        SourceFeature.LIMITATIONS_SECTIONS,
                        SourceFeature.RISKS,
                        SourceFeature.DRAWBACKS,
                        SourceFeature.COUNTERARGUMENTS,
                        SourceFeature.TRADEOFFS,
                        SourceFeature.FAILURE_CASES,
                        SourceFeature.UNCERTAINTY_LANGUAGE,
                        SourceFeature.METHODOLOGY_LIMITATIONS),
                reject(
                        SourceFeature.THIN_CONTENT,
                        SourceFeature.KEYWORD_STUFFING)));
        rules.put(SearchRole.HUMAN_DISCUSSION, rule(
                0.20,
                0.32,
                2,
                support(
                        SourceFeature.MULTIPLE_PARTICIPANTS,
                        SourceFeature.REPLIES_OR_COMMENTS,
                        SourceFeature.QUESTION_AND_ANSWER_STRUCTURE,
                        SourceFeature.FIRST_PERSON_EXPERIENCE,
                        SourceFeature.CONVERSATION_DEPTH,
                        SourceFeature.DIFFERING_VIEWPOINTS),
                reject(
                        SourceFeature.THIN_CONTENT,
                        SourceFeature.DUPLICATED_TEXT,
                        SourceFeature.KEYWORD_STUFFING)));
        return new RoleEligibilityRules(rules);
    }

    private static RoleEligibilityRule rule(
            double minimumRelevance,
            double minimumConfidence,
            int minimumSupportingFeatures,
            Map<SourceFeature, FeatureThreshold> support,
            Map<SourceFeature, FeatureThreshold> reject) {
        return new RoleEligibilityRule(
                minimumRelevance,
                minimumConfidence,
                minimumSupportingFeatures,
                1.0,
                support,
                reject);
    }

    private static Map<SourceFeature, FeatureThreshold> support(
            SourceFeature... features) {
        var values = new EnumMap<SourceFeature, FeatureThreshold>(
                SourceFeature.class);
        for (SourceFeature feature : features) {
            values.put(feature, new FeatureThreshold(0.20, 1.0));
        }
        return values;
    }

    private static Map<SourceFeature, FeatureThreshold> reject(
            SourceFeature... features) {
        var values = new EnumMap<SourceFeature, FeatureThreshold>(
                SourceFeature.class);
        for (SourceFeature feature : features) {
            values.put(feature, new FeatureThreshold(0.85, 1.0));
        }
        return values;
    }
}