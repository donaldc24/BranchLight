package com.branchlight.backend.search.ranking;

import java.util.Objects;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;

public record RankedSearchCandidate(
        AggregatedSearchResult candidate,
        PreliminaryCandidateScoreBreakdown scoreBreakdown) {

    public RankedSearchCandidate {
        Objects.requireNonNull(
                candidate,
                "candidate must not be null");
        Objects.requireNonNull(
                scoreBreakdown,
                "scoreBreakdown must not be null");
    }

    public double score() {
        return scoreBreakdown.totalScore();
    }
}
