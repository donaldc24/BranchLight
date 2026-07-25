package com.branchlight.backend.search.optimization;

import java.net.URI;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;

public record SelectedRoleSource(
        SearchRole role,
        String documentId,
        URI sourceUrl,
        double roleScore,
        String reason) {

    public SelectedRoleSource {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (documentId.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId and reason must not be blank");
        }
        if (!Double.isFinite(roleScore)
                || roleScore < 0.0
                || roleScore > 1.0) {
            throw new IllegalArgumentException(
                    "roleScore must be between 0.0 and 1.0");
        }
    }
}