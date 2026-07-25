package com.branchlight.backend.search.eligibility;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;

public record CandidateRoleEligibility(
        String documentId,
        Map<SearchRole, RoleEligibilityResult> roles) {

    public CandidateRoleEligibility {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        if (documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId must not be blank");
        }
        var copy = new EnumMap<SearchRole, RoleEligibilityResult>(
                SearchRole.class);
        copy.putAll(roles);
        if (copy.size() != SearchRole.values().length) {
            throw new IllegalArgumentException(
                    "roles must contain every search role");
        }
        roles = Collections.unmodifiableMap(copy);
    }

    public RoleEligibilityResult role(SearchRole role) {
        return roles.get(Objects.requireNonNull(
                role,
                "role must not be null"));
    }
}