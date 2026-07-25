package com.branchlight.backend.search.fetch;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record PageFetchFailure(
        String requestedUrl,
        URI failedUrl,
        PageFetchFailureType failureType,
        Integer statusCode,
        String message,
        List<URI> redirectChain) implements PageFetchResult {

    public PageFetchFailure {
        Objects.requireNonNull(
                requestedUrl,
                "requestedUrl must not be null");
        Objects.requireNonNull(
                failureType,
                "failureType must not be null");
        Objects.requireNonNull(message, "message must not be null");
        redirectChain = List.copyOf(Objects.requireNonNull(
                redirectChain,
                "redirectChain must not be null"));

        if (message.isBlank()) {
            throw new IllegalArgumentException(
                    "message must not be blank");
        }
        if (statusCode != null
                && (statusCode < 100 || statusCode > 599)) {
            throw new IllegalArgumentException(
                    "statusCode must be a valid HTTP status");
        }
    }
}
