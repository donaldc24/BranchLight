package com.branchlight.backend.search.provider;

import java.util.Objects;

public final class SearchProviderException extends RuntimeException {

    public enum Failure {
        RATE_LIMITED,
        TIMEOUT,
        MALFORMED_RESPONSE,
        HTTP_ERROR,
        CONNECTION_ERROR
    }

    private final Failure failure;
    private final Integer statusCode;
    private final String rateLimitReset;

    private SearchProviderException(
            Failure failure,
            String message,
            Integer statusCode,
            String rateLimitReset,
            Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(
                failure,
                "failure must not be null");
        this.statusCode = statusCode;
        this.rateLimitReset = rateLimitReset;
    }

    public static SearchProviderException rateLimited(String rateLimitReset) {
        return new SearchProviderException(
                Failure.RATE_LIMITED,
                "Search provider rate limit exceeded",
                429,
                rateLimitReset,
                null);
    }

    public static SearchProviderException timeout(Throwable cause) {
        return new SearchProviderException(
                Failure.TIMEOUT,
                "Search provider request timed out",
                null,
                null,
                cause);
    }

    public static SearchProviderException malformedResponse(Throwable cause) {
        return new SearchProviderException(
                Failure.MALFORMED_RESPONSE,
                "Search provider returned a malformed response",
                null,
                null,
                cause);
    }

    public static SearchProviderException httpError(int statusCode) {
        return new SearchProviderException(
                Failure.HTTP_ERROR,
                "Search provider returned HTTP status " + statusCode,
                statusCode,
                null,
                null);
    }

    public static SearchProviderException connectionError(Throwable cause) {
        return new SearchProviderException(
                Failure.CONNECTION_ERROR,
                "Search provider request failed",
                null,
                null,
                cause);
    }

    public Failure failure() {
        return failure;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public String rateLimitReset() {
        return rateLimitReset;
    }
}
