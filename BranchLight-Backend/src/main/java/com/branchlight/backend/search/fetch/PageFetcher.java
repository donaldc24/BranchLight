package com.branchlight.backend.search.fetch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface PageFetcher {

    PageFetchResult fetch(String url);

    default List<PageFetchResult> fetchAll(List<String> urls) {
        Objects.requireNonNull(urls, "urls must not be null");
        var results = new ArrayList<PageFetchResult>(urls.size());

        for (String url : urls) {
            try {
                results.add(Objects.requireNonNull(
                        fetch(url),
                        "fetch result must not be null"));
            } catch (RuntimeException exception) {
                results.add(new PageFetchFailure(
                        url == null ? "" : url,
                        null,
                        PageFetchFailureType.UNEXPECTED_ERROR,
                        null,
                        "Unexpected page fetch failure",
                        List.of()));
            }
        }

        return List.copyOf(results);
    }
}
