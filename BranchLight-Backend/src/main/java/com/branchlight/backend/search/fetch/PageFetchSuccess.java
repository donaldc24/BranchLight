package com.branchlight.backend.search.fetch;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

public record PageFetchSuccess(
        String requestedUrl,
        URI finalUrl,
        int statusCode,
        String contentType,
        Charset charset,
        String content,
        int responseSizeBytes,
        List<URI> redirectChain) implements PageFetchResult {

    public PageFetchSuccess {
        Objects.requireNonNull(
                requestedUrl,
                "requestedUrl must not be null");
        Objects.requireNonNull(finalUrl, "finalUrl must not be null");
        Objects.requireNonNull(
                contentType,
                "contentType must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        Objects.requireNonNull(content, "content must not be null");
        redirectChain = List.copyOf(Objects.requireNonNull(
                redirectChain,
                "redirectChain must not be null"));

        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalArgumentException(
                    "statusCode must be successful");
        }
        if (responseSizeBytes < 0) {
            throw new IllegalArgumentException(
                    "responseSizeBytes must not be negative");
        }
    }

    public int redirectCount() {
        return Math.max(0, redirectChain.size() - 1);
    }

    @Override
    public String toString() {
        return "PageFetchSuccess[requestedUrl="
                + requestedUrl
                + ", finalUrl="
                + finalUrl
                + ", statusCode="
                + statusCode
                + ", contentType="
                + contentType
                + ", charset="
                + charset
                + ", responseSizeBytes="
                + responseSizeBytes
                + ", redirectCount="
                + redirectCount()
                + ", content=<redacted>]";
    }
}
