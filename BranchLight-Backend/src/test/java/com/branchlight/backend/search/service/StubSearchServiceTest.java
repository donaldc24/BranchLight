package com.branchlight.backend.search.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.branchlight.backend.search.domain.SearchRole;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StubSearchServiceTest {

    private final StubSearchService searchService = new StubSearchService();

    @Test
    void returnsExactlyOneResultForEachSearchRole() {
        var results = searchService.search("virtual threads");
        var roles = results.stream()
                .map(result -> result.role())
                .toList();

        assertEquals(5, results.size());
        assertEquals(List.of(SearchRole.values()), roles);
        results.forEach(result -> assertAll(
                () -> assertFalse(result.title().isBlank()),
                () -> assertFalse(result.url().toString().isBlank()),
                () -> assertFalse(result.domain().isBlank()),
                () -> assertFalse(result.snippet().isBlank()),
                () -> assertFalse(result.selectionReason().isBlank())));
    }

    @Test
    void returnsDeterministicResults() {
        var firstResults = searchService.search("virtual threads");
        var secondResults = searchService.search("virtual threads");

        assertEquals(firstResults, secondResults);
    }
}
