package com.branchlight.backend.search.features;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record SourceFeatureSet(
        String documentId,
        Map<SourceFeature, SourceFeatureValue> features) {

    public SourceFeatureSet {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(features, "features must not be null");
        if (documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId must not be blank");
        }
        var copy = new EnumMap<SourceFeature, SourceFeatureValue>(
                SourceFeature.class);
        copy.putAll(features);
        if (copy.size() != SourceFeature.values().length) {
            throw new IllegalArgumentException(
                    "features must contain every source feature");
        }
        if (copy.containsValue(null)) {
            throw new IllegalArgumentException(
                    "feature values must not be null");
        }
        features = Collections.unmodifiableMap(copy);
    }

    public SourceFeatureValue value(SourceFeature feature) {
        return features.get(Objects.requireNonNull(
                feature,
                "feature must not be null"));
    }

    public Map<SourceFeature, SourceFeatureValue> group(
            SourceFeatureGroup group) {
        Objects.requireNonNull(group, "group must not be null");
        var grouped = new EnumMap<SourceFeature, SourceFeatureValue>(
                SourceFeature.class);
        features.forEach((feature, value) -> {
            if (feature.group() == group) {
                grouped.put(feature, value);
            }
        });
        return Collections.unmodifiableMap(grouped);
    }
}