package com.branchlight.backend.search.content;

public record SourcePosition(int startOffset, int endOffset) {

    public SourcePosition {
        if (startOffset < 0) {
            throw new IllegalArgumentException(
                    "startOffset must not be negative");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException(
                    "endOffset must not precede startOffset");
        }
    }
}