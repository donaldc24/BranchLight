package com.branchlight.backend.search.content;

import java.util.List;
import java.util.Objects;

public record Passage(
        String documentId,
        List<String> headingPath,
        String text,
        SourcePosition position,
        int approximateWordCount) {

    public Passage {
        Objects.requireNonNull(documentId, "documentId must not be null");
        headingPath = List.copyOf(Objects.requireNonNull(
                headingPath,
                "headingPath must not be null"));
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(position, "position must not be null");
        if (documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "documentId must not be blank");
        }
        if (headingPath.stream().anyMatch(
                heading -> heading == null || heading.isBlank())) {
            throw new IllegalArgumentException(
                    "headingPath must contain only non-blank headings");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (approximateWordCount <= 0) {
            throw new IllegalArgumentException(
                    "approximateWordCount must be greater than zero");
        }
    }
}