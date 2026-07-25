package com.branchlight.backend.search.eligibility;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.features.SourceFeature;
import com.branchlight.backend.search.features.SourceFeatureSet;

public final class DeterministicRoleEligibilityEvaluator {

    public static final String RELEVANCE_SIGNAL =
            "OVERALL_PAGE_RELEVANCE";

    private final RoleEligibilityRules rules;

    public DeterministicRoleEligibilityEvaluator() {
        this(RoleEligibilityRules.DEFAULTS);
    }

    public DeterministicRoleEligibilityEvaluator(
            RoleEligibilityRules rules) {
        this.rules = Objects.requireNonNull(
                rules,
                "rules must not be null");
    }

    public CandidateRoleEligibility evaluate(
            RoleEligibilityInput input) {
        Objects.requireNonNull(input, "input must not be null");
        var results = new EnumMap<SearchRole, RoleEligibilityResult>(
                SearchRole.class);
        for (SearchRole role : SearchRole.values()) {
            results.put(role, evaluateRole(
                    role,
                    input.candidate().overallPageRelevance(),
                    input.features()));
        }
        return new CandidateRoleEligibility(
                input.features().documentId(),
                results);
    }

    public List<CandidateRoleEligibility> evaluateAll(
            List<RoleEligibilityInput> candidates) {
        Objects.requireNonNull(
                candidates,
                "candidates must not be null");
        return candidates.stream()
                .map(candidate -> evaluate(Objects.requireNonNull(
                        candidate,
                        "candidate must not be null")))
                .toList();
    }

    public RoleEligibilityRules rules() {
        return rules;
    }

    private RoleEligibilityResult evaluateRole(
            SearchRole role,
            double relevance,
            SourceFeatureSet features) {
        RoleEligibilityRule rule = rules.rule(role);
        var supportingNames = new LinkedHashSet<String>();
        var rejectingNames = new LinkedHashSet<String>();
        var diagnostics = new ArrayList<String>();

        boolean relevant = relevance >= rule.minimumRelevance();
        if (relevant) {
            supportingNames.add(RELEVANCE_SIGNAL);
            diagnostics.add(diagnostic(
                    RELEVANCE_SIGNAL,
                    relevance,
                    "meets minimum",
                    rule.minimumRelevance()));
        } else {
            rejectingNames.add(RELEVANCE_SIGNAL);
            diagnostics.add(diagnostic(
                    RELEVANCE_SIGNAL,
                    relevance,
                    "is below minimum",
                    rule.minimumRelevance()));
        }

        double weightedSupport = relevance * rule.relevanceWeight();
        double activatedWeight = rule.relevanceWeight();
        int activatedFeatureCount = 0;
        for (Map.Entry<SourceFeature, FeatureThreshold> entry
                : rule.supportingFeatures().entrySet()) {
            double value = features.value(entry.getKey())
                    .normalizedValue();
            FeatureThreshold threshold = entry.getValue();
            if (value >= threshold.threshold()) {
                activatedFeatureCount++;
                supportingNames.add(entry.getKey().name());
                weightedSupport += value * threshold.weight();
                activatedWeight += threshold.weight();
                diagnostics.add(diagnostic(
                        entry.getKey().name(),
                        value,
                        "meets support threshold",
                        threshold.threshold()));
            }
        }

        if (activatedFeatureCount < rule.minimumSupportingFeatures()) {
            rule.supportingFeatures().forEach((feature, threshold) -> {
                double value = features.value(feature).normalizedValue();
                if (value < threshold.threshold()) {
                    rejectingNames.add(feature.name());
                    diagnostics.add(diagnostic(
                            feature.name(),
                            value,
                            "is below support threshold",
                            threshold.threshold()));
                }
            });
        }

        double rejectionValue = 0.0;
        double rejectionWeight = 0.0;
        boolean hardRejection = false;
        for (Map.Entry<SourceFeature, FeatureThreshold> entry
                : rule.rejectingFeatures().entrySet()) {
            double value = features.value(entry.getKey())
                    .normalizedValue();
            FeatureThreshold threshold = entry.getValue();
            rejectionValue += value * threshold.weight();
            rejectionWeight += threshold.weight();
            if (value >= threshold.threshold()) {
                hardRejection = true;
                rejectingNames.add(entry.getKey().name());
                diagnostics.add(diagnostic(
                        entry.getKey().name(),
                        value,
                        "meets rejection threshold",
                        threshold.threshold()));
            }
        }

        double baseConfidence = weightedSupport / activatedWeight;
        double averageRisk = rejectionWeight == 0.0
                ? 0.0
                : rejectionValue / rejectionWeight;
        double confidence = clamp(baseConfidence * (1.0 - averageRisk));
        boolean enoughSupport = activatedFeatureCount
                >= rule.minimumSupportingFeatures();
        boolean confident = confidence >= rule.minimumConfidence();
        if (!confident) {
            diagnostics.add(diagnostic(
                    "COMBINED_CONFIDENCE",
                    confidence,
                    "is below minimum",
                    rule.minimumConfidence()));
        }

        boolean eligible = relevant
                && enoughSupport
                && confident
                && !hardRejection;
        return new RoleEligibilityResult(
                role,
                eligible,
                confidence,
                List.copyOf(supportingNames),
                List.copyOf(rejectingNames),
                List.copyOf(diagnostics));
    }

    private static String diagnostic(
            String name,
            double value,
            String comparison,
            double threshold) {
        return String.format(
                Locale.ROOT,
                "%s=%.3f %s %.3f",
                name,
                value,
                comparison,
                threshold);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}