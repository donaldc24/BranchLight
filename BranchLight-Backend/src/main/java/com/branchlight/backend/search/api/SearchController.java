package com.branchlight.backend.search.api;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.branchlight.backend.search.domain.SearchResult;
import com.branchlight.backend.search.service.SearchService;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = Objects.requireNonNull(
                searchService,
                "searchService must not be null");
    }

    @PostMapping
    public ResponseEntity<?> search(
            @RequestBody(required = false) SearchRequest request) {
        if (request == null
                || request.query() == null
                || request.query().isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(invalidQueryProblem());
        }

        var results = searchService.search(request.query());
        return ResponseEntity.ok(toResponse(request.query(), results));
    }

    private static SearchResponse toResponse(
            String query,
            List<SearchResult> results) {
        var categorizedResults = results.stream()
                .map(SearchController::toCategorizedResult)
                .toList();

        return new SearchResponse(query, categorizedResults);
    }

    private static CategorizedResult toCategorizedResult(SearchResult result) {
        return new CategorizedResult(
                result.role(),
                result.title(),
                result.url().toString(),
                result.domain(),
                result.snippet(),
                result.selectionReason(),
                result.score());
    }

    private static ProblemDetail invalidQueryProblem() {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "query must not be blank");
        problem.setTitle("Invalid search request");
        return problem;
    }
}
