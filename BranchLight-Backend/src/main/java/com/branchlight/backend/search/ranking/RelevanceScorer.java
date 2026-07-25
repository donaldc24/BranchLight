package com.branchlight.backend.search.ranking;

import java.util.List;

/**
 * Scores candidate documents on the normalized range {@code [0.0, 1.0]}.
 */
public interface RelevanceScorer {

    List<ScoredCandidateDocument> score(
            RelevanceQuery query,
            List<CandidateDocument> candidates);

    default List<ScoredCandidateDocument> score(
            String originalQuery,
            List<CandidateDocument> candidates) {
        return score(
                new RelevanceQuery(originalQuery, List.of()),
                candidates);
    }
}