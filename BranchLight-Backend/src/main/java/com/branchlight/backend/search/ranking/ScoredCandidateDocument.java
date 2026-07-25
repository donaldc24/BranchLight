package com.branchlight.backend.search.ranking;

import java.util.List;
import java.util.Objects;

public record ScoredCandidateDocument(
        CandidateDocument candidate,
        double overallPageRelevance,
        double titleScore,
        double snippetScore,
        List<TopRelevantPassage> topRelevantPassages,
        RelevanceScoreBreakdown scoreBreakdown) {

    public ScoredCandidateDocument {
        Objects.requireNonNull(candidate, "candidate must not be null");
        TopRelevantPassage.requireNormalized(
                overallPageRelevance,
                "overallPageRelevance");
        TopRelevantPassage.requireNormalized(titleScore, "titleScore");
        TopRelevantPassage.requireNormalized(snippetScore, "snippetScore");
        topRelevantPassages = List.copyOf(Objects.requireNonNull(
                topRelevantPassages,
                "topRelevantPassages must not be null"));
        Objects.requireNonNull(
                scoreBreakdown,
                "scoreBreakdown must not be null");
    }
}