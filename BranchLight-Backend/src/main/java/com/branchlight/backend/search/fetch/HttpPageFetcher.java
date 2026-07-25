package com.branchlight.backend.search.fetch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;

public final class HttpPageFetcher
        implements PageFetcher, AutoCloseable {

    static final String USER_AGENT =
            "BranchLight/1.0 (search result page fetcher)";

    private static final String ACCEPT_HEADER =
            "text/html, text/plain;q=0.9";

    private static final int COPY_BUFFER_SIZE = 8192;

    private static final int MAXIMUM_CONCURRENT_FETCHES = 25;

    private static final long FETCH_THREAD_KEEP_ALIVE_SECONDS = 60L;

    private static final Set<Integer> REDIRECT_STATUS_CODES =
            Set.of(301, 302, 303, 307, 308);

    private static final AtomicInteger FETCH_THREAD_SEQUENCE =
            new AtomicInteger();

    private final CloseableHttpClient httpClient;
    private final ExecutorService requestExecutor;
    private final Duration connectionTimeout;
    private final Duration responseTimeout;
    private final long responseTimeoutNanos;
    private final int maximumRedirects;
    private final int maximumResponseBytes;

    public HttpPageFetcher(
            Duration connectionTimeout,
            Duration responseTimeout,
            int maximumRedirects,
            int maximumResponseBytes) {
        this(
                connectionTimeout,
                responseTimeout,
                maximumRedirects,
                maximumResponseBytes,
                new PublicNetworkDestinationValidator());
    }

    HttpPageFetcher(
            Duration connectionTimeout,
            Duration responseTimeout,
            int maximumRedirects,
            int maximumResponseBytes,
            DnsResolver dnsResolver) {
        this.connectionTimeout = requirePositiveDuration(
                connectionTimeout,
                "connectionTimeout");
        this.responseTimeout = requirePositiveDuration(
                responseTimeout,
                "responseTimeout");
        durationToNanos(this.connectionTimeout, "connectionTimeout");
        this.responseTimeoutNanos = durationToNanos(
                this.responseTimeout,
                "responseTimeout");
        if (maximumRedirects < 0) {
            throw new IllegalArgumentException(
                    "maximumRedirects must not be negative");
        }
        if (maximumResponseBytes <= 0) {
            throw new IllegalArgumentException(
                    "maximumResponseBytes must be greater than zero");
        }

        this.maximumRedirects = maximumRedirects;
        this.maximumResponseBytes = maximumResponseBytes;

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(this.connectionTimeout))
                .setSocketTimeout(Timeout.of(this.responseTimeout))
                .build();
        var connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(Objects.requireNonNull(
                                dnsResolver,
                                "dnsResolver must not be null"))
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(
                        Timeout.of(this.connectionTimeout))
                .setResponseTimeout(Timeout.of(this.responseTimeout))
                .setRedirectsEnabled(false)
                .setContentCompressionEnabled(false)
                .setHardCancellationEnabled(true)
                .setProtocolUpgradeEnabled(false)
                .build();

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(USER_AGENT)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableContentCompression()
                .build();
        this.requestExecutor = new ThreadPoolExecutor(
                0,
                MAXIMUM_CONCURRENT_FETCHES,
                FETCH_THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    var thread = new Thread(
                            runnable,
                            "branchlight-page-fetch-"
                                    + FETCH_THREAD_SEQUENCE
                                            .incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public PageFetchResult fetch(String url) {
        try {
            return fetchInternal(url);
        } catch (RuntimeException exception) {
            return failure(
                    url == null ? "" : url,
                    null,
                    PageFetchFailureType.UNEXPECTED_ERROR,
                    null,
                    "Unexpected page fetch failure",
                    List.of());
        }
    }

    private PageFetchResult fetchInternal(String url) {
        String requestedUrl = url == null ? "" : url;
        URI currentUrl;
        try {
            currentUrl = parseHttpUrl(url);
        } catch (UrlValidationException exception) {
            return failure(
                    requestedUrl,
                    null,
                    exception.failureType(),
                    null,
                    exception.getMessage(),
                    List.of());
        }

        var redirectChain = new ArrayList<URI>();
        redirectChain.add(currentUrl);
        var visitedUrls = new HashSet<URI>();
        visitedUrls.add(currentUrl);
        int redirectsFollowed = 0;

        while (true) {
            HttpGet request = new HttpGet(currentUrl);
            request.setHeader("User-Agent", USER_AGENT);
            request.setHeader("Accept", ACCEPT_HEADER);
            request.setHeader("Accept-Encoding", "identity");

            TransportResponse response;
            Future<TransportResponse> responseFuture;
            try {
                responseFuture = requestExecutor.submit(
                        () -> executeRequest(request));
            } catch (RejectedExecutionException exception) {
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType.UNEXPECTED_ERROR,
                        null,
                        "Page fetch capacity is temporarily unavailable",
                        redirectChain);
            }
            try {
                response = responseFuture.get(
                        responseTimeoutNanos,
                        TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                request.cancel();
                responseFuture.cancel(true);
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType.RESPONSE_TIMEOUT,
                        null,
                        "Response timed out",
                        redirectChain);
            } catch (InterruptedException exception) {
                request.cancel();
                responseFuture.cancel(true);
                Thread.currentThread().interrupt();
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType.INTERRUPTED,
                        null,
                        "Page fetch was interrupted",
                        redirectChain);
            } catch (ExecutionException exception) {
                return transportFailure(
                        requestedUrl,
                        currentUrl,
                        exception.getCause(),
                        redirectChain);
            }

            int statusCode = response.statusCode();
            if (REDIRECT_STATUS_CODES.contains(statusCode)) {
                if (redirectsFollowed >= maximumRedirects) {
                    return failure(
                            requestedUrl,
                            currentUrl,
                            PageFetchFailureType.TOO_MANY_REDIRECTS,
                            statusCode,
                            "Maximum redirect count exceeded",
                            redirectChain);
                }

                URI redirectUrl;
                try {
                    redirectUrl = resolveRedirect(
                            currentUrl,
                            response.location());
                } catch (UrlValidationException exception) {
                    return failure(
                            requestedUrl,
                            currentUrl,
                            PageFetchFailureType.INVALID_REDIRECT,
                            statusCode,
                            "Redirect location is not a valid HTTP URL",
                            redirectChain);
                }

                redirectChain.add(redirectUrl);
                if (!visitedUrls.add(redirectUrl)) {
                    return failure(
                            requestedUrl,
                            redirectUrl,
                            PageFetchFailureType.REDIRECT_LOOP,
                            statusCode,
                            "Redirect loop detected",
                            redirectChain);
                }

                currentUrl = redirectUrl;
                redirectsFollowed++;
                continue;
            }

            if (statusCode < 200 || statusCode >= 300) {
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType.HTTP_ERROR,
                        statusCode,
                        "Page returned a non-success HTTP status",
                        redirectChain);
            }

            if (!isIdentityContentEncoding(
                    response.contentEncoding())) {
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType
                                .UNSUPPORTED_CONTENT_ENCODING,
                        statusCode,
                        "Page content encoding is not supported",
                        redirectChain);
            }

            ContentDescriptor contentDescriptor;
            try {
                contentDescriptor = parseContentType(
                        response.contentType());
            } catch (UnsupportedContentTypeException exception) {
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType.UNSUPPORTED_CONTENT_TYPE,
                        statusCode,
                        exception.getMessage(),
                        redirectChain);
            } catch (MalformedResponseException exception) {
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType.MALFORMED_RESPONSE,
                        statusCode,
                        exception.getMessage(),
                        redirectChain);
            }

            if (response.tooLarge()) {
                return failure(
                        requestedUrl,
                        currentUrl,
                        PageFetchFailureType.RESPONSE_TOO_LARGE,
                        statusCode,
                        "Page response exceeded the configured size limit",
                        redirectChain);
            }

            byte[] bytes = response.body();
            return new PageFetchSuccess(
                    requestedUrl,
                    currentUrl,
                    statusCode,
                    contentDescriptor.mediaType(),
                    contentDescriptor.charset(),
                    new String(bytes, contentDescriptor.charset()),
                    bytes.length,
                    redirectChain);
        }
    }

    public Duration connectionTimeout() {
        return connectionTimeout;
    }

    public Duration responseTimeout() {
        return responseTimeout;
    }

    public int maximumRedirects() {
        return maximumRedirects;
    }

    public int maximumResponseBytes() {
        return maximumResponseBytes;
    }

    @Override
    public void close() {
        requestExecutor.shutdownNow();
        httpClient.close(CloseMode.IMMEDIATE);
    }

    private TransportResponse executeRequest(HttpGet request)
            throws IOException {
        ClassicHttpResponse response = null;
        boolean fullyConsumed = false;

        try {
            response = httpClient.executeOpen(
                    null,
                    request,
                    HttpClientContext.create());
            int statusCode = response.getCode();
            String location = firstHeaderValue(
                    response,
                    "Location");
            String contentType = firstHeaderValue(
                    response,
                    "Content-Type");
            String contentEncoding = firstHeaderValue(
                    response,
                    "Content-Encoding");

            if (statusCode < 200
                    || statusCode >= 300
                    || !isSupportedMediaType(contentType)
                    || !isIdentityContentEncoding(contentEncoding)) {
                return new TransportResponse(
                        statusCode,
                        location,
                        contentType,
                        contentEncoding,
                        new byte[0],
                        false);
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                fullyConsumed = true;
                return new TransportResponse(
                        statusCode,
                        location,
                        contentType,
                        contentEncoding,
                        new byte[0],
                        false);
            }
            if (entity.getContentLength() > maximumResponseBytes) {
                return new TransportResponse(
                        statusCode,
                        location,
                        contentType,
                        contentEncoding,
                        new byte[0],
                        true);
            }

            BoundedBody body = readBounded(entity.getContent());
            fullyConsumed = !body.tooLarge();
            return new TransportResponse(
                    statusCode,
                    location,
                    contentType,
                    contentEncoding,
                    body.bytes(),
                    body.tooLarge());
        } finally {
            closeResponse(response, fullyConsumed);
        }
    }

    private BoundedBody readBounded(InputStream inputStream)
            throws IOException {
        var output = new ByteArrayOutputStream(
                Math.min(maximumResponseBytes, COPY_BUFFER_SIZE));
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytesRead = 0;

        while (true) {
            long bytesUntilOversized =
                    (long) maximumResponseBytes - bytesRead + 1L;
            int readLength = (int) Math.min(
                    buffer.length,
                    bytesUntilOversized);
            int currentRead = inputStream.read(
                    buffer,
                    0,
                    readLength);
            if (currentRead < 0) {
                return new BoundedBody(
                        output.toByteArray(),
                        false);
            }
            if ((long) bytesRead + currentRead
                    > maximumResponseBytes) {
                return new BoundedBody(new byte[0], true);
            }

            output.write(buffer, 0, currentRead);
            bytesRead += currentRead;
        }
    }

    private static void closeResponse(
            ClassicHttpResponse response,
            boolean fullyConsumed) {
        if (response == null) {
            return;
        }

        if (response instanceof ModalCloseable modalCloseable) {
            modalCloseable.close(
                    fullyConsumed
                            ? CloseMode.GRACEFUL
                            : CloseMode.IMMEDIATE);
            return;
        }

        try {
            response.close();
        } catch (IOException exception) {
            // The fetch result must not be replaced by a close failure.
        }
    }

    private static String firstHeaderValue(
            ClassicHttpResponse response,
            String headerName) {
        Header header = response.getFirstHeader(headerName);
        return header == null || header.getValue() == null
                ? ""
                : header.getValue();
    }

    private static URI parseHttpUrl(String url)
            throws UrlValidationException {
        if (url == null || url.isBlank()) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "URL must not be blank");
        }
        if (!url.equals(url.strip())) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "URL must not contain surrounding whitespace");
        }

        try {
            return validateHttpUrl(new URI(url));
        } catch (URISyntaxException exception) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "URL is malformed");
        }
    }

    private static URI validateHttpUrl(URI url)
            throws UrlValidationException {
        String scheme = url.getScheme();
        if (scheme == null) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "URL must be absolute");
        }
        if (!scheme.equalsIgnoreCase("http")
                && !scheme.equalsIgnoreCase("https")) {
            throw new UrlValidationException(
                    PageFetchFailureType.UNSUPPORTED_SCHEME,
                    "Only HTTP and HTTPS URLs are supported");
        }
        if (!url.isAbsolute()
                || url.isOpaque()
                || url.getHost() == null
                || url.getHost().isBlank()
                || url.getRawUserInfo() != null) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "URL must contain a host and no user information");
        }
        if (url.getPort() == 0 || url.getPort() > 65535) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "URL contains an invalid port");
        }

        String asciiUrl = url.toASCIIString();
        int fragmentIndex = asciiUrl.indexOf('#');
        if (fragmentIndex >= 0) {
            asciiUrl = asciiUrl.substring(0, fragmentIndex);
        }
        try {
            return new URI(asciiUrl);
        } catch (URISyntaxException exception) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "URL is malformed");
        }
    }

    private static URI resolveRedirect(
            URI currentUrl,
            String location) throws UrlValidationException {
        if (location == null || location.isBlank()) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "Redirect location must not be blank");
        }

        try {
            return validateHttpUrl(
                    currentUrl.resolve(new URI(location)));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new UrlValidationException(
                    PageFetchFailureType.MALFORMED_URL,
                    "Redirect location is malformed");
        }
    }

    private static ContentDescriptor parseContentType(String contentType)
            throws UnsupportedContentTypeException,
            MalformedResponseException {
        if (!isSupportedMediaType(contentType)) {
            throw new UnsupportedContentTypeException(
                    "Page content type must be HTML or plain text");
        }

        String[] parts = contentType.split(";");
        String mediaType = parts[0].trim().toLowerCase(Locale.ROOT);
        Charset charset = StandardCharsets.UTF_8;

        for (int index = 1; index < parts.length; index++) {
            String parameter = parts[index].trim();
            int equalsIndex = parameter.indexOf('=');
            if (equalsIndex < 0
                    || !parameter.substring(0, equalsIndex)
                            .trim()
                            .equalsIgnoreCase("charset")) {
                continue;
            }

            String charsetName = parameter
                    .substring(equalsIndex + 1)
                    .trim();
            if (charsetName.startsWith("\"")
                    && charsetName.endsWith("\"")
                    && charsetName.length() >= 2) {
                charsetName = charsetName.substring(
                        1,
                        charsetName.length() - 1);
            }
            if (charsetName.isBlank()) {
                throw new MalformedResponseException(
                        "Page response contains an invalid charset");
            }
            try {
                charset = Charset.forName(charsetName);
            } catch (IllegalCharsetNameException
                    | UnsupportedCharsetException exception) {
                throw new MalformedResponseException(
                        "Page response contains an unsupported charset");
            }
            break;
        }

        return new ContentDescriptor(mediaType, charset);
    }

    private static boolean isSupportedMediaType(String contentType) {
        int semicolonIndex = contentType.indexOf(';');
        String mediaType = (semicolonIndex >= 0
                ? contentType.substring(0, semicolonIndex)
                : contentType)
                .trim()
                .toLowerCase(Locale.ROOT);
        return mediaType.equals("text/html")
                || mediaType.equals("text/plain");
    }

    private static boolean isIdentityContentEncoding(
            String contentEncoding) {
        return contentEncoding.isBlank()
                || contentEncoding.trim().equalsIgnoreCase("identity");
    }

    private static Duration requirePositiveDuration(
            Duration duration,
            String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    name + " must be positive");
        }
        return duration;
    }

    private static long durationToNanos(
            Duration duration,
            String name) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    name + " is too large",
                    exception);
        }
    }

    private static PageFetchFailure transportFailure(
            String requestedUrl,
            URI failedUrl,
            Throwable transportFailure,
            List<URI> redirectChain) {
        if (hasCause(
                transportFailure,
                BlockedDestinationException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.BLOCKED_DESTINATION,
                    null,
                    "Destination is not a public network address",
                    redirectChain);
        }
        if (hasCause(
                transportFailure,
                ConnectTimeoutException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.CONNECTION_TIMEOUT,
                    null,
                    "Connection timed out",
                    redirectChain);
        }
        if (hasCause(
                transportFailure,
                ConnectionRequestTimeoutException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.CONNECTION_TIMEOUT,
                    null,
                    "Connection timed out",
                    redirectChain);
        }
        if (hasCause(transportFailure, SocketTimeoutException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.RESPONSE_TIMEOUT,
                    null,
                    "Response timed out",
                    redirectChain);
        }
        if (hasCause(transportFailure, UnknownHostException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.DNS_FAILURE,
                    null,
                    "Destination host could not be resolved",
                    redirectChain);
        }
        if (hasCause(transportFailure, ConnectException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.CONNECTION_FAILURE,
                    null,
                    "Connection failed",
                    redirectChain);
        }
        if (hasCause(transportFailure, ClientProtocolException.class)
                || hasCause(
                        transportFailure,
                        MalformedChunkCodingException.class)
                || hasCause(
                        transportFailure,
                        MessageConstraintException.class)
                || hasCause(
                        transportFailure,
                        ConnectionClosedException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.MALFORMED_RESPONSE,
                    null,
                    "Page returned a malformed HTTP response",
                    redirectChain);
        }
        if (hasCause(transportFailure, IOException.class)) {
            return failure(
                    requestedUrl,
                    failedUrl,
                    PageFetchFailureType.CONNECTION_FAILURE,
                    null,
                    "Page request failed",
                    redirectChain);
        }
        return failure(
                requestedUrl,
                failedUrl,
                PageFetchFailureType.UNEXPECTED_ERROR,
                null,
                "Unexpected page fetch failure",
                redirectChain);
    }

    private static boolean hasCause(
            Throwable failure,
            Class<? extends Throwable> causeType) {
        Throwable current = failure;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static PageFetchFailure failure(
            String requestedUrl,
            URI failedUrl,
            PageFetchFailureType failureType,
            Integer statusCode,
            String message,
            List<URI> redirectChain) {
        return new PageFetchFailure(
                requestedUrl,
                failedUrl,
                failureType,
                statusCode,
                message,
                redirectChain);
    }

    private record TransportResponse(
            int statusCode,
            String location,
            String contentType,
            String contentEncoding,
            byte[] body,
            boolean tooLarge) {

        private TransportResponse {
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    private record BoundedBody(byte[] bytes, boolean tooLarge) {

        private BoundedBody {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record ContentDescriptor(
            String mediaType,
            Charset charset) {
    }

    private static final class UrlValidationException
            extends Exception {

        private final PageFetchFailureType failureType;

        private UrlValidationException(
                PageFetchFailureType failureType,
                String message) {
            super(message);
            this.failureType = failureType;
        }

        private PageFetchFailureType failureType() {
            return failureType;
        }
    }

    private static final class UnsupportedContentTypeException
            extends Exception {

        private UnsupportedContentTypeException(String message) {
            super(message);
        }
    }

    private static final class MalformedResponseException
            extends Exception {

        private MalformedResponseException(String message) {
            super(message);
        }
    }
}
