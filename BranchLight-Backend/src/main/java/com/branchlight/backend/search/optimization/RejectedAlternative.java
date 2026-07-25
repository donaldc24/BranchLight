package com.branchlight.backend.search.optimization;

import java.net.URI;
import java.util.Objects;

public record RejectedAlternative(
        String documentId,
        URI sourceUrl,
        Double roleScore,
        double eligibilityConfidence,
        String reason) {

    public RejectedAlternative {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (documentId.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId and reason must not be blank");
        }
        if (roleScore != null
                && (!Double.isFinite(roleScore)
                        || roleScore < 0.0
                        || roleScore > 1.0)) {
            throw new IllegalArgumentException(
                    "roleScore must be null or between 0.0 and 1.0");
        }
        if (!Double.isFinite(eligibilityConfidence)
                || eligibilityConfidence < 0.0
                || eligibilityConfidence > 1.0) {
            throw new IllegalArgumentException(
                    "eligibilityConfidence must be between 0.0 and 1.0");
        }
    }
}