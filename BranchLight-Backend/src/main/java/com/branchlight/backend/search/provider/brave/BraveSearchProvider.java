package com.branchlight.backend.search.provider.brave;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.provider.RawSearchResult;
import com.branchlight.backend.search.provider.SearchProvider;
import com.branchlight.backend.search.provider.SearchProviderException;

public final class BraveSearchProvider implements SearchProvider {

    static final String RETRIEVAL_PURPOSE = "General web search";

    private static final String SEARCH_PATH = "/res/v1/web/search";
    private static final String API_KEY_HEADER = "X-Subscription-Token";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    private static final int MAX_RESULTS_PER_REQUEST = 20;

    private final String apiKey;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public BraveSearchProvider(
            String apiKey,
            URI baseUrl,
            Duration timeout,
            JsonMapper jsonMapper) {
        this(
                apiKey,
                createRestClient(baseUrl, timeout),
                jsonMapper);
    }

    BraveSearchProvider(
            String apiKey,
            RestClient restClient,
            JsonMapper jsonMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }

        this.apiKey = apiKey;
        this.restClient = Objects.requireNonNull(
                restClient,
                "restClient must not be null");
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper must not be null");
    }

    @Override
    public List<RawSearchResult> search(String query, int resultLimit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (resultLimit < 0) {
            throw new IllegalArgumentException(
                    "resultLimit must not be negative");
        }
        if (resultLimit == 0) {
            return List.of();
        }

        int requestLimit = Math.min(
                resultLimit,
                MAX_RESULTS_PER_REQUEST);
        var response = executeSearch(query, requestLimit);

        if (response.statusCode() == 429) {
            throw SearchProviderException.rateLimited(
                    response.rateLimitReset());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw SearchProviderException.httpError(response.statusCode());
        }

        return mapResponse(response.body(), query, requestLimit);
    }

    private ProviderHttpResponse executeSearch(
            String query,
            int requestLimit) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SEARCH_PATH)
                            .queryParam("q", query)
                            .queryParam("count", requestLimit)
                            .queryParam("result_filter", "web")
                            .queryParam("text_decorations", false)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header(API_KEY_HEADER, apiKey)
                    .exchangeForRequiredValue((request, response) -> {
                        int statusCode = response.getStatusCode().value();
                        String rateLimitReset = response.getHeaders()
                                .getFirst(RATE_LIMIT_RESET_HEADER);
                        byte[] body = response.getStatusCode()
                                .is2xxSuccessful()
                                ? response.bodyTo(byte[].class)
                                : null;
                        return new ProviderHttpResponse(
                                statusCode,
                                rateLimitReset,
                                body);
                    });
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw SearchProviderException.timeout(exception);
            }
            throw SearchProviderException.connectionError(exception);
        } catch (RestClientException exception) {
            throw SearchProviderException.connectionError(exception);
        }
    }

    private List<RawSearchResult> mapResponse(
            byte[] body,
            String query,
            int resultLimit) {
        try {
            var response = jsonMapper.readValue(
                    Objects.requireNonNull(body, "body must not be null"),
                    BraveSearchResponse.class);

            if (response == null) {
                throw new IllegalArgumentException(
                        "response must be a JSON object");
            }
            if (response.web() == null
                    || response.web().results() == null) {
                return List.of();
            }

            var providerResults = response.web().results();
            int resultCount = Math.min(resultLimit, providerResults.size());
            var mappedResults = new ArrayList<RawSearchResult>(resultCount);

            for (int index = 0; index < resultCount; index++) {
                var providerResult = Objects.requireNonNull(
                        providerResults.get(index),
                        "result must not be null");
                mappedResults.add(new RawSearchResult(
                        parseUrl(providerResult.url()),
                        requireText(providerResult.title(), "title"),
                        Objects.requireNonNullElse(
                                providerResult.description(),
                                ""),
                        index + 1,
                        parsePublicationDate(providerResult.pageAge()),
                        query,
                        RETRIEVAL_PURPOSE));
            }

            return List.copyOf(mappedResults);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw SearchProviderException.malformedResponse(exception);
        } catch (NullPointerException exception) {
            throw SearchProviderException.malformedResponse(exception);
        }
    }

    private static URI parseUrl(String url) {
        var parsedUrl = URI.create(requireText(url, "url"));
        String scheme = parsedUrl.getScheme();

        if (!parsedUrl.isAbsolute()
                || scheme == null
                || parsedUrl.isOpaque()
                || parsedUrl.getHost() == null
                || (!scheme.toLowerCase(Locale.ROOT).equals("http")
                && !scheme.toLowerCase(Locale.ROOT).equals("https"))) {
            throw new IllegalArgumentException("url must be an absolute HTTP URL");
        }

        return parsedUrl;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
        return value;
    }

    private static LocalDate parsePublicationDate(String pageAge) {
        if (pageAge == null || pageAge.isBlank()) {
            return null;
        }

        try {
            return DateTimeFormatter.ISO_DATE_TIME.parse(
                    pageAge,
                    LocalDate::from);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "pageAge must be an ISO date-time",
                    exception);
        }
    }

    private static boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private static RestClient createRestClient(
            URI baseUrl,
            Duration timeout) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        if (!baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("baseUrl must be absolute");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private record ProviderHttpResponse(
            int statusCode,
            String rateLimitReset,
            byte[] body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BraveSearchResponse(BraveWebResults web) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BraveWebResults(List<BraveWebResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BraveWebResult(
            String url,
            String title,
            String description,
            @JsonProperty("page_age") String pageAge) {
    }
}
