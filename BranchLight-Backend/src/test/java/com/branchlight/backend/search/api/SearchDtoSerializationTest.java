package com.branchlight.backend.search.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.domain.SearchRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchDtoSerializationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void roundTripsSearchRequestWithoutChangingTheQuery() throws Exception {
        var request = new SearchRequest("  virtual threads  ");

        var json = JSON_MAPPER.writeValueAsString(request);
        var parsedJson = JSON_MAPPER.readTree(json);

        assertEquals("  virtual threads  ", parsedJson.get("query").stringValue());
        assertEquals(request, JSON_MAPPER.readValue(json, SearchRequest.class));
    }

    @Test
    void roundTripsCategorizedResultsAndOmitsAnAbsentScore() throws Exception {
        var response = new SearchResponse(
                "virtual threads",
                List.of(
                        result(SearchRole.AUTHORITATIVE, 0.98),
                        result(SearchRole.EXPLANATORY, null),
                        result(SearchRole.PRACTICAL, null),
                        result(SearchRole.CRITICAL, null),
                        result(SearchRole.HUMAN_DISCUSSION, null)));

        var json = JSON_MAPPER.writeValueAsString(response);
        var parsedJson = JSON_MAPPER.readTree(json);
        var results = parsedJson.get("results");

        assertEquals("virtual threads", parsedJson.get("query").stringValue());
        assertEquals(SearchRole.values().length, results.size());

        for (int index = 0; index < SearchRole.values().length; index++) {
            assertEquals(
                    SearchRole.values()[index].name(),
                    results.get(index).get("role").stringValue());
        }

        JsonNode authoritativeResult = results.get(0);
        assertEquals(
                "AUTHORITATIVE title",
                authoritativeResult.get("title").stringValue());
        assertEquals(
                "https://example.com/AUTHORITATIVE",
                authoritativeResult.get("url").stringValue());
        assertEquals(
                "example.com",
                authoritativeResult.get("domain").stringValue());
        assertEquals(
                "Example snippet",
                authoritativeResult.get("snippet").stringValue());
        assertEquals(
                "Selected for AUTHORITATIVE",
                authoritativeResult.get("selectionReason").stringValue());
        assertEquals(0.98, authoritativeResult.get("score").doubleValue(), 0.0001);
        assertFalse(results.get(1).has("score"));

        assertEquals(response, JSON_MAPPER.readValue(json, SearchResponse.class));
    }

    private static CategorizedResult result(SearchRole role, Double score) {
        return new CategorizedResult(
                role,
                role.name() + " title",
                "https://example.com/" + role.name(),
                "example.com",
                "Example snippet",
                "Selected for " + role.name(),
                score);
    }
}
