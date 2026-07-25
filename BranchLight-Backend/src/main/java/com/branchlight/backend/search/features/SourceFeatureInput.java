package com.branchlight.backend.search.features;

import java.util.Objects;

import com.branchlight.backend.search.content.ContentExtractionSuccess;
import com.branchlight.backend.search.ranking.CandidateDocument;

public record SourceFeatureInput(
        CandidateDocument candidate,
        ContentExtractionSuccess extraction,
        String sourceContent) {

    public SourceFeatureInput {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(extraction, "extraction must not be null");
        Objects.requireNonNull(
                sourceContent,
                "sourceContent must not be null");
        if (sourceContent.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceContent must not be blank");
        }
    }
}