package com.branchlight.backend.search.scoring;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;

public record CandidateDeterministicRoleScores(
        String documentId,
        Map<SearchRole, DeterministicRoleScore> roleScores) {

    public CandidateDeterministicRoleScores {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(roleScores, "roleScores must not be null");
        if (documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId must not be blank");
        }
        var copy = new EnumMap<SearchRole, DeterministicRoleScore>(
                SearchRole.class);
        copy.putAll(roleScores);
        if (copy.size() != SearchRole.values().length) {
            throw new IllegalArgumentException(
                    "roleScores must contain every search role");
        }
        roleScores = Collections.unmodifiableMap(copy);
    }

    public DeterministicRoleScore role(SearchRole role) {
        return roleScores.get(Objects.requireNonNull(
                role,
                "role must not be null"));
    }
}