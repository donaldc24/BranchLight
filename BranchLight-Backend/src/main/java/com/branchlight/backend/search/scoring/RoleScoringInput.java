package com.branchlight.backend.search.scoring;

import java.util.Objects;

import com.branchlight.backend.search.eligibility.CandidateRoleEligibility;
import com.branchlight.backend.search.features.SourceFeatureSet;
import com.branchlight.backend.search.ranking.ScoredCandidateDocument;

public record RoleScoringInput(
        ScoredCandidateDocument candidate,
        SourceFeatureSet features,
        CandidateRoleEligibility eligibility) {

    public RoleScoringInput {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(features, "features must not be null");
        Objects.requireNonNull(eligibility, "eligibility must not be null");
        String documentId = candidate.candidate().document().documentId();
        if (!documentId.equals(features.documentId())
                || !documentId.equals(eligibility.documentId())) {
            throw new IllegalArgumentException(
                    "candidate, features, and eligibility must identify the same document");
        }
    }
}