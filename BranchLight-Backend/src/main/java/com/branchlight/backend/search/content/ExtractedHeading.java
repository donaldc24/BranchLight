package com.branchlight.backend.search.content;

import java.util.Objects;

public record ExtractedHeading(int level, String text) {

    public ExtractedHeading {
        Objects.requireNonNull(text, "text must not be null");
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException(
                    "level must be between 1 and 6");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    "text must not be blank");
        }
    }
}
