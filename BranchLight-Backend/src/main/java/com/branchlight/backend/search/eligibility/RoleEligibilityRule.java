package com.branchlight.backend.search.eligibility;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.features.SourceFeature;

public record RoleEligibilityRule(
        double minimumRelevance,
        double minimumConfidence,
        int minimumSupportingFeatures,
        double relevanceWeight,
        Map<SourceFeature, FeatureThreshold> supportingFeatures,
        Map<SourceFeature, FeatureThreshold> rejectingFeatures) {

    public RoleEligibilityRule {
        requireNormalized(minimumRelevance, "minimumRelevance");
        requireNormalized(minimumConfidence, "minimumConfidence");
        if (minimumSupportingFeatures <= 0) {
            throw new IllegalArgumentException(
                    "minimumSupportingFeatures must be greater than zero");
        }
        if (!Double.isFinite(relevanceWeight) || relevanceWeight <= 0.0) {
            throw new IllegalArgumentException(
                    "relevanceWeight must be finite and greater than zero");
        }
        supportingFeatures = immutableFeatureMap(
                supportingFeatures,
                "supportingFeatures");
        rejectingFeatures = immutableFeatureMap(
                rejectingFeatures,
                "rejectingFeatures");
        if (supportingFeatures.size() < minimumSupportingFeatures) {
            throw new IllegalArgumentException(
                    "minimumSupportingFeatures exceeds configured supporting features");
        }
    }

    private static Map<SourceFeature, FeatureThreshold>
            immutableFeatureMap(
                    Map<SourceFeature, FeatureThreshold> values,
                    String name) {
        Objects.requireNonNull(values, name + " must not be null");
        var copy = new EnumMap<SourceFeature, FeatureThreshold>(
                SourceFeature.class);
        copy.putAll(values);
        if (copy.containsKey(null) || copy.containsValue(null)) {
            throw new IllegalArgumentException(
                    name + " must not contain null values");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0.0 and 1.0");
        }
    }
}