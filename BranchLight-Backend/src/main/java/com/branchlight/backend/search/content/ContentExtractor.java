package com.branchlight.backend.search.content;

import com.branchlight.backend.search.fetch.PageFetchSuccess;

public interface ContentExtractor {

    ContentExtractionResult extract(PageFetchSuccess fetchedPage);
}
