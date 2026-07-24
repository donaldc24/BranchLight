package com.branchlight.backend.search.service;

import java.util.List;

import com.branchlight.backend.search.domain.SearchResult;

public interface SearchService {

    List<SearchResult> search(String query);
}
