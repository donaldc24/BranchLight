package com.branchlight.backend.search.content;

public record PassageSplitterOptions(
        int targetWordCount,
        int maximumWordCount,
        int overlapWordCount) {

    public static final PassageSplitterOptions DEFAULTS =
            new PassageSplitterOptions(250, 350, 30);

    public PassageSplitterOptions {
        if (targetWordCount <= 0) {
            throw new IllegalArgumentException(
                    "targetWordCount must be greater than zero");
        }
        if (maximumWordCount < targetWordCount) {
            throw new IllegalArgumentException(
                    "maximumWordCount must be at least targetWordCount");
        }
        if (overlapWordCount < 0
                || overlapWordCount >= targetWordCount) {
            throw new IllegalArgumentException(
                    "overlapWordCount must be non-negative and less than targetWordCount");
        }
    }
}