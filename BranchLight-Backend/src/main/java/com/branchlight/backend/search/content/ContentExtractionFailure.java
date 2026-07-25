package com.branchlight.backend.search.content;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record ContentExtractionFailure(
        URI sourceUrl,
        ContentExtractionFailureType failureType,
        String message,
        List<OriginalMetadataEntry> originalMetadata)
        implements ContentExtractionResult {

    public ContentExtractionFailure {
        Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
        Objects.requireNonNull(
                failureType,
                "failureType must not be null");
        Objects.requireNonNull(message, "message must not be null");
        originalMetadata = List.copyOf(Objects.requireNonNull(
                originalMetadata,
                "originalMetadata must not be null"));

        if (message.isBlank()) {
            throw new IllegalArgumentException(
                    "message must not be blank");
        }
    }
}
