package com.branchlight.backend.search.fetch;

public sealed interface PageFetchResult
        permits PageFetchSuccess, PageFetchFailure {

    String requestedUrl();

    default boolean successful() {
        return this instanceof PageFetchSuccess;
    }
}
