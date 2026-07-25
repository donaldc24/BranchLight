package com.branchlight.backend.search.scoring;

import java.util.Objects;

import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.eligibility.RoleEligibilityResult;

public record DeterministicRoleScore(
        SearchRole role,
        Double finalScore,
        RoleScoreBreakdown scoreBreakdown,
        String reason,
        RoleEligibilityResult eligibilityResult) {

    public DeterministicRoleScore {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(
                eligibilityResult,
                "eligibilityResult must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (role != eligibilityResult.role()) {
            throw new IllegalArgumentException(
                    "role must match eligibilityResult role");
        }
        if (eligibilityResult.eligible()) {
            Objects.requireNonNull(
                    finalScore,
                    "eligible roles must have a finalScore");
            Objects.requireNonNull(
                    scoreBreakdown,
                    "eligible roles must have a scoreBreakdown");
            if (!Double.isFinite(finalScore)
                    || finalScore < 0.0
                    || finalScore > 1.0) {
                throw new IllegalArgumentException(
                        "finalScore must be between 0.0 and 1.0");
            }
        } else if (finalScore != null || scoreBreakdown != null) {
            throw new IllegalArgumentException(
                    "ineligible roles must not have a score or breakdown");
        }
    }
}