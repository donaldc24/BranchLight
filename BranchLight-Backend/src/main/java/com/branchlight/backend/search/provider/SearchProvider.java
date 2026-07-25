package com.branchlight.backend.search.provider;

import java.util.List;

public interface SearchProvider {

    List<RawSearchResult> search(String query, int resultLimit);
}
