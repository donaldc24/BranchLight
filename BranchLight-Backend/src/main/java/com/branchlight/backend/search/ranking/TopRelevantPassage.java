package com.branchlight.backend.search.ranking;

import java.util.Objects;

import com.branchlight.backend.search.content.Passage;

public record TopRelevantPassage(
        Passage passage,
        double relevance) {

    public TopRelevantPassage {
        Objects.requireNonNull(passage, "passage must not be null");
        requireNormalized(relevance, "relevance");
    }

    static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0.0 and 1.0");
        }
    }
}