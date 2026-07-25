package com.branchlight.backend.search.eligibility;

public record FeatureThreshold(
        double threshold,
        double weight) {

    public FeatureThreshold {
        if (!Double.isFinite(threshold)
                || threshold < 0.0
                || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "threshold must be between 0.0 and 1.0");
        }
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException(
                    "weight must be finite and greater than zero");
        }
    }
}