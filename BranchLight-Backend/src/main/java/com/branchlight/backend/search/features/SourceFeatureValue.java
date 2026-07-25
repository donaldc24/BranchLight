package com.branchlight.backend.search.features;

public record SourceFeatureValue(
        double rawValue,
        double normalizedValue) {

    public SourceFeatureValue {
        if (!Double.isFinite(rawValue)) {
            throw new IllegalArgumentException(
                    "rawValue must be finite");
        }
        if (!Double.isFinite(normalizedValue)
                || normalizedValue < 0.0
                || normalizedValue > 1.0) {
            throw new IllegalArgumentException(
                    "normalizedValue must be between 0.0 and 1.0");
        }
    }
}