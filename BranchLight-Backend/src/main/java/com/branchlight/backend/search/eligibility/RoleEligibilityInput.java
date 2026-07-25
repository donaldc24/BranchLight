package com.branchlight.backend.search.eligibility;

import java.util.Objects;

import com.branchlight.backend.search.features.SourceFeatureSet;
import com.branchlight.backend.search.ranking.ScoredCandidateDocument;

public record RoleEligibilityInput(
        ScoredCandidateDocument candidate,
        SourceFeatureSet features) {

    public RoleEligibilityInput {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(features, "features must not be null");
        String candidateDocumentId = candidate.candidate()
                .document()
                .documentId();
        if (!candidateDocumentId.equals(features.documentId())) {
            throw new IllegalArgumentException(
                    "candidate and features must identify the same document");
        }
    }
}