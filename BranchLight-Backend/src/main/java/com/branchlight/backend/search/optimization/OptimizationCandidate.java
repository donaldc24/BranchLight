package com.branchlight.backend.search.optimization;

import java.util.Objects;

import com.branchlight.backend.search.ranking.ScoredCandidateDocument;
import com.branchlight.backend.search.scoring.CandidateDeterministicRoleScores;

public record OptimizationCandidate(
        ScoredCandidateDocument candidate,
        CandidateDeterministicRoleScores roleScores) {

    public OptimizationCandidate {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(roleScores, "roleScores must not be null");
        String candidateId = candidate.candidate()
                .document()
                .documentId();
        if (!candidateId.equals(roleScores.documentId())) {
            throw new IllegalArgumentException(
                    "candidate and roleScores must identify the same document");
        }
    }

    public String documentId() {
        return roleScores.documentId();
    }
}