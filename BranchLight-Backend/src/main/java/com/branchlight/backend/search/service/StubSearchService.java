package com.branchlight.backend.search.service;

import java.net.URI;
import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.branchlight.backend.search.domain.SearchResult;
import com.branchlight.backend.search.domain.SearchRole;

@Service
@Primary
@Profile({"test", "development"})
public class StubSearchService implements SearchService {

    private static final List<SearchResult> RESULTS = List.of(
            new SearchResult(
                    SearchRole.AUTHORITATIVE,
                    "Official Reference",
                    URI.create("https://example.com/reference"),
                    "example.com",
                    "A deterministic primary-source result for local development.",
                    "Represents an authoritative or official source.",
                    0.95),
            new SearchResult(
                    SearchRole.EXPLANATORY,
                    "Conceptual Guide",
                    URI.create("https://example.com/guide"),
                    "example.com",
                    "A deterministic explanatory result for local development.",
                    "Provides background and conceptual explanation.",
                    0.90),
            new SearchResult(
                    SearchRole.PRACTICAL,
                    "Hands-on Tutorial",
                    URI.create("https://example.com/tutorial"),
                    "example.com",
                    "A deterministic practical result for local development.",
                    "Shows how to apply the topic in practice.",
                    0.85),
            new SearchResult(
                    SearchRole.CRITICAL,
                    "Critical Analysis",
                    URI.create("https://example.com/analysis"),
                    "example.com",
                    "A deterministic critical result for local development.",
                    "Surfaces limitations and competing viewpoints.",
                    0.80),
            new SearchResult(
                    SearchRole.HUMAN_DISCUSSION,
                    "Community Discussion",
                    URI.create("https://example.com/discussion"),
                    "example.com",
                    "A deterministic discussion result for local development.",
                    "Captures experience and discussion from people.",
                    null));

    @Override
    public List<SearchResult> search(String query) {
        return RESULTS;
    }
}
