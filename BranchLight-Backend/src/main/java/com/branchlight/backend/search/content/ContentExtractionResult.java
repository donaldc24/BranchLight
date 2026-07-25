package com.branchlight.backend.search.content;

import java.net.URI;
import java.util.List;

public sealed interface ContentExtractionResult
        permits ContentExtractionSuccess, ContentExtractionFailure {

    URI sourceUrl();

    List<OriginalMetadataEntry> originalMetadata();

    default boolean successful() {
        return this instanceof ContentExtractionSuccess;
    }
}
