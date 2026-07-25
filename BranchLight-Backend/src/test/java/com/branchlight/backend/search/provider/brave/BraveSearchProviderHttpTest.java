package com.branchlight.backend.search.provider.brave;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.provider.RawSearchResult;
import com.branchlight.backend.search.provider.SearchProviderException;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BraveSearchProviderHttpTest {

    private static final String BASE_URL = "https://brave.test";
    private static final String API_KEY = "test-api-key";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private MockRestServiceServer mockServer;
    private BraveSearchProvider provider;

    @BeforeEach
    void setUp() {
        var restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new BraveSearchProvider(
                API_KEY,
                restClientBuilder.build(),
                JSON_MAPPER);
    }

    @AfterEach
    void verifyMockServer() {
        mockServer.verify();
    }

    @Test
    void mapsWebResultsAndKeepsCredentialsOutOfTheUrl() {
        String query = "virtual threads & structured concurrency";
        mockServer.expect(requestTo(startsWith(
                        BASE_URL + "/res/v1/web/search")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam(
                        "q",
                        "virtual%20threads%20%26%20structured%20concurrency"))
                .andExpect(queryParam("count", "2"))
                .andExpect(queryParam("result_filter", "web"))
                .andExpect(queryParam("text_decorations", "false"))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("X-Subscription-Token", API_KEY))
                .andExpect(request -> assertFalse(
                        request.getURI().toString().contains(API_KEY)))
                .andRespond(withSuccess("""
                        {
                          "type": "search",
                          "unknown_root_field": true,
                          "web": {
                            "unknown_web_field": "ignored",
                            "results": [
                              {
                                "url": "https://example.com/reference",
                                "title": "Official reference",
                                "description": "Primary documentation.",
                                "page_age": "2026-07-23T14:22:41Z",
                                "unknown_result_field": 42
                              },
                              {
                                "url": "https://example.com/guide",
                                "title": "Practical guide"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var results = provider.search(query, 2);

        assertEquals(List.of(
                new RawSearchResult(
                        URI.create("https://example.com/reference"),
                        "Official reference",
                        "Primary documentation.",
                        1,
                        LocalDate.of(2026, 7, 23),
                        query,
                        BraveSearchProvider.RETRIEVAL_PURPOSE),
                new RawSearchResult(
                        URI.create("https://example.com/guide"),
                        "Practical guide",
                        "",
                        2,
                        null,
                        query,
                        BraveSearchProvider.RETRIEVAL_PURPOSE)),
                results);
    }

    @Test
    void treatsAMissingWebSectionAsNoResults() {
        mockServer.expect(requestTo(startsWith(
                        BASE_URL + "/res/v1/web/search")))
                .andRespond(withSuccess(
                        """
                        {
                          "type": "search",
                          "query": {
                            "original": "no results"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        assertTrue(provider.search("no results", 5).isEmpty());
    }

    @Test
    void reportsRateLimitsWithTheProviderResetHeader() {
        mockServer.expect(requestTo(startsWith(
                        BASE_URL + "/res/v1/web/search")))
                .andRespond(withStatus(HttpStatusCode.valueOf(429))
                        .header("X-RateLimit-Reset", "1, 3600")
                        .body("{\"error\":\"rate limited\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                SearchProviderException.class,
                () -> provider.search("virtual threads", 5));

        assertEquals(
                SearchProviderException.Failure.RATE_LIMITED,
                exception.failure());
        assertEquals(429, exception.statusCode());
        assertEquals("1, 3600", exception.rateLimitReset());
        assertDoesNotExposeApiKey(exception);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 500, 503})
    void reportsNonSuccessStatusCodesWithoutReturningTheBody(int statusCode) {
        mockServer.expect(requestTo(startsWith(
                        BASE_URL + "/res/v1/web/search")))
                .andRespond(withStatus(HttpStatusCode.valueOf(statusCode))
                        .body("upstream response must stay private"));

        var exception = assertThrows(
                SearchProviderException.class,
                () -> provider.search("virtual threads", 5));

        assertEquals(
                SearchProviderException.Failure.HTTP_ERROR,
                exception.failure());
        assertEquals(statusCode, exception.statusCode());
        assertFalse(exception.getMessage().contains(
                "upstream response must stay private"));
        assertDoesNotExposeApiKey(exception);
    }

    @Test
    void reportsTimeoutsSeparately() {
        mockServer.expect(requestTo(startsWith(
                        BASE_URL + "/res/v1/web/search")))
                .andRespond(request -> {
                    throw new SocketTimeoutException("simulated timeout");
                });

        var exception = assertThrows(
                SearchProviderException.class,
                () -> provider.search("virtual threads", 5));

        assertEquals(
                SearchProviderException.Failure.TIMEOUT,
                exception.failure());
        assertDoesNotExposeApiKey(exception);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "null",
            "{\"web\":{\"results\":\"not-an-array\"}}",
            "{\"web\":{\"results\":[{\"url\":\"https:opaque\",\"title\":\"Bad URL\"}]}}",
            "{\"web\":{\"results\":[{\"url\":\"https://example.com\",\"title\":\"Bad date\",\"page_age\":\"not-a-date\"}]}}"
    })
    void reportsMalformedSuccessfulResponses(String responseBody) {
        mockServer.expect(requestTo(startsWith(
                        BASE_URL + "/res/v1/web/search")))
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                SearchProviderException.class,
                () -> provider.search("virtual threads", 5));

        assertEquals(
                SearchProviderException.Failure.MALFORMED_RESPONSE,
                exception.failure());
        assertDoesNotExposeApiKey(exception);
    }

    @Test
    void clampsTheProviderRequestToBravesMaximumCount() {
        mockServer.expect(requestTo(startsWith(
                        BASE_URL + "/res/v1/web/search")))
                .andExpect(queryParam("count", "20"))
                .andRespond(withSuccess(
                        "{\"web\":{\"results\":[]}}",
                        MediaType.APPLICATION_JSON));

        assertTrue(provider.search("virtual threads", 100).isEmpty());
    }

    @Test
    void skipsHttpForAZeroLimitAndRejectsNegativeLimits() {
        assertTrue(provider.search("virtual threads", 0).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.search("virtual threads", -1));
    }

    private static void assertDoesNotExposeApiKey(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            String message = current.getMessage();
            assertTrue(message == null || !message.contains(API_KEY));
            current = current.getCause();
        }
    }
}
