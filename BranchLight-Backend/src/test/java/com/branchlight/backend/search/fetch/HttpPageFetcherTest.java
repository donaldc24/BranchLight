package com.branchlight.backend.search.fetch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPageFetcherTest {

    private static final Duration CONNECTION_TIMEOUT =
            Duration.ofSeconds(1);
    private static final Duration RESPONSE_TIMEOUT =
            Duration.ofSeconds(2);
    private static final int MAXIMUM_REDIRECTS = 3;
    private static final int MAXIMUM_RESPONSE_BYTES = 1024;

    private final Map<String, HttpHandler> handlers =
            new ConcurrentHashMap<>();
    private final List<HttpPageFetcher> fetchers =
            new ArrayList<>();

    private HttpServer mockServer;
    private ExecutorService serverExecutor;

    @BeforeEach
    void setUp() throws IOException {
        serverExecutor = Executors.newCachedThreadPool(runnable -> {
            var thread = new Thread(
                    runnable,
                    "page-fetcher-test-server");
            thread.setDaemon(true);
            return thread;
        });
        mockServer = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        mockServer.setExecutor(serverExecutor);
        mockServer.createContext("/", exchange -> {
            HttpHandler handler = handlers.get(
                    exchange.getRequestURI().getPath());
            if (handler == null) {
                respond(
                        exchange,
                        404,
                        "text/plain",
                        "not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            handler.handle(exchange);
        });
        mockServer.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        fetchers.forEach(HttpPageFetcher::close);
        mockServer.stop(0);
        serverExecutor.shutdownNow();
        serverExecutor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void downloadsHtmlAndSendsDescriptiveHeaders() {
        String pageContent =
                "<html><body>BranchLight test page</body></html>";
        var userAgent = new AtomicReference<String>();
        var accept = new AtomicReference<String>();
        var acceptEncoding = new AtomicReference<String>();
        var host = new AtomicReference<String>();
        handlers.put("/html", exchange -> {
            userAgent.set(exchange.getRequestHeaders()
                    .getFirst("User-Agent"));
            accept.set(exchange.getRequestHeaders()
                    .getFirst("Accept"));
            acceptEncoding.set(exchange.getRequestHeaders()
                    .getFirst("Accept-Encoding"));
            host.set(exchange.getRequestHeaders()
                    .getFirst("Host"));
            respond(
                    exchange,
                    200,
                    "text/html; charset=UTF-8",
                    pageContent.getBytes(StandardCharsets.UTF_8));
        });

        PageFetchResult result = fetcher().fetch(url("/html"));

        assertThat(result).isInstanceOfSatisfying(
                PageFetchSuccess.class,
                success -> {
                    assertThat(success.content()).isEqualTo(pageContent);
                    assertThat(success.contentType())
                            .isEqualTo("text/html");
                    assertThat(success.charset())
                            .isEqualTo(StandardCharsets.UTF_8);
                    assertThat(success.responseSizeBytes())
                            .isEqualTo(pageContent.getBytes(
                                    StandardCharsets.UTF_8).length);
                    assertThat(success.redirectCount()).isZero();
                    assertThat(success.toString())
                            .doesNotContain(pageContent);
                });
        assertThat(userAgent.get())
                .isEqualTo(HttpPageFetcher.USER_AGENT)
                .contains("BranchLight");
        assertThat(accept.get())
                .contains("text/html", "text/plain");
        assertThat(acceptEncoding.get()).isEqualTo("identity");
        assertThat(host.get()).startsWith("mock.branchlight.test:");
    }

    @Test
    void downloadsPlainTextUsingTheDeclaredCharset() {
        String pageContent = "caf\u00e9";
        var charset = StandardCharsets.ISO_8859_1;
        handlers.put("/text", exchange -> respond(
                exchange,
                200,
                "text/plain; charset=ISO-8859-1",
                pageContent.getBytes(charset)));

        PageFetchResult result = fetcher().fetch(url("/text"));

        assertThat(result).isInstanceOfSatisfying(
                PageFetchSuccess.class,
                success -> {
                    assertThat(success.content()).isEqualTo(pageContent);
                    assertThat(success.contentType())
                            .isEqualTo("text/plain");
                    assertThat(success.charset()).isEqualTo(charset);
                });
    }

    @Test
    void returnsStructuredFailuresWithoutExposingResponseBodies() {
        String privateErrorBody =
                "complete upstream page content must not escape";
        handlers.put("/json", exchange -> respond(
                exchange,
                200,
                "application/json",
                "{}".getBytes(StandardCharsets.UTF_8)));
        handlers.put("/error", exchange -> respond(
                exchange,
                503,
                "text/plain",
                privateErrorBody.getBytes(StandardCharsets.UTF_8)));
        handlers.put("/encoded", exchange -> {
            exchange.getResponseHeaders().set(
                    "Content-Encoding",
                    "gzip");
            respond(
                    exchange,
                    200,
                    "text/html",
                    "encoded".getBytes(StandardCharsets.UTF_8));
        });

        PageFetchResult unsupported =
                fetcher().fetch(url("/json"));
        PageFetchResult httpError =
                fetcher().fetch(url("/error"));
        PageFetchResult encoded =
                fetcher().fetch(url("/encoded"));

        assertThat(unsupported).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> assertThat(failure.failureType())
                        .isEqualTo(
                                PageFetchFailureType
                                        .UNSUPPORTED_CONTENT_TYPE));
        assertThat(httpError).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> {
                    assertThat(failure.failureType())
                            .isEqualTo(
                                    PageFetchFailureType.HTTP_ERROR);
                    assertThat(failure.statusCode()).isEqualTo(503);
                    assertThat(failure.message())
                            .doesNotContain(privateErrorBody);
                    assertThat(failure.toString())
                            .doesNotContain(privateErrorBody);
                });
        assertThat(encoded).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> assertThat(failure.failureType())
                        .isEqualTo(
                                PageFetchFailureType
                                        .UNSUPPORTED_CONTENT_ENCODING));
    }

    @Test
    void followsRedirectsAndRevalidatesEveryDestination() {
        handlers.put("/redirect", exchange -> redirect(
                exchange,
                302,
                url("redirect.branchlight.test", "/final")));
        handlers.put("/final", exchange -> respond(
                exchange,
                200,
                "text/plain",
                "redirected".getBytes(StandardCharsets.UTF_8)));
        var resolvedHosts = new ArrayList<String>();
        DnsResolver recordingResolver = dnsResolver(host -> {
            resolvedHosts.add(host);
            return mockServerAddress();
        });
        var fetcher = fetcher(
                MAXIMUM_REDIRECTS,
                MAXIMUM_RESPONSE_BYTES,
                RESPONSE_TIMEOUT,
                recordingResolver);

        PageFetchResult result = fetcher.fetch(url("/redirect"));

        assertThat(result).isInstanceOfSatisfying(
                PageFetchSuccess.class,
                success -> {
                    assertThat(success.content())
                            .isEqualTo("redirected");
                    assertThat(success.finalUrl())
                            .isEqualTo(URI.create(url(
                                    "redirect.branchlight.test",
                                    "/final")));
                    assertThat(success.redirectCount()).isEqualTo(1);
                    assertThat(success.redirectChain())
                            .containsExactly(
                                    URI.create(url("/redirect")),
                                    URI.create(url(
                                            "redirect.branchlight.test",
                                            "/final")));
                });
        assertThat(resolvedHosts)
                .containsExactly(
                        "mock.branchlight.test",
                        "redirect.branchlight.test");
    }

    @Test
    void blocksTheLocalMockServerBeforeSendingARequest() {
        var targetRequests = new AtomicInteger();
        handlers.put("/private-target", exchange -> {
            targetRequests.incrementAndGet();
            respond(
                    exchange,
                    200,
                    "text/plain",
                    "must not be reached".getBytes(
                            StandardCharsets.UTF_8));
        });
        var strictFetcher = new HttpPageFetcher(
                CONNECTION_TIMEOUT,
                RESPONSE_TIMEOUT,
                MAXIMUM_REDIRECTS,
                MAXIMUM_RESPONSE_BYTES);
        fetchers.add(strictFetcher);
        String privateUrl = "http://127.0.0.1:"
                + mockServer.getAddress().getPort()
                + "/private-target";

        PageFetchResult result = strictFetcher.fetch(privateUrl);

        assertThat(result).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> assertThat(failure.failureType())
                        .isEqualTo(
                                PageFetchFailureType
                                        .BLOCKED_DESTINATION));
        assertThat(targetRequests).hasValue(0);
    }

    @Test
    void usesTheResolvedAddressForTheConnectionWithoutASecondLookup() {
        handlers.put("/resolved-once", exchange -> respond(
                exchange,
                200,
                "text/plain",
                "resolved".getBytes(StandardCharsets.UTF_8)));
        var resolutions = new AtomicInteger();
        DnsResolver oneShotResolver = dnsResolver(host -> {
            if (resolutions.incrementAndGet() > 1) {
                throw new UnknownHostException(
                        "Unexpected second DNS lookup");
            }
            return mockServerAddress();
        });
        var fetcher = fetcher(
                MAXIMUM_REDIRECTS,
                MAXIMUM_RESPONSE_BYTES,
                RESPONSE_TIMEOUT,
                oneShotResolver);

        PageFetchResult result =
                fetcher.fetch(url("/resolved-once"));

        assertThat(result).isInstanceOf(PageFetchSuccess.class);
        assertThat(resolutions).hasValue(1);
    }

    @Test
    void blocksARedirectDestinationBeforeSendingTheNextRequest() {
        var targetRequests = new AtomicInteger();
        handlers.put("/redirect-blocked", exchange -> redirect(
                exchange,
                302,
                url("blocked.branchlight.test", "/blocked-target")));
        handlers.put("/blocked-target", exchange -> {
            targetRequests.incrementAndGet();
            respond(
                    exchange,
                    200,
                    "text/plain",
                    "must not be reached".getBytes(
                            StandardCharsets.UTF_8));
        });
        DnsResolver blockingResolver = dnsResolver(host -> {
            if (host.equals("blocked.branchlight.test")) {
                throw new BlockedDestinationException();
            }
            return mockServerAddress();
        });
        var fetcher = fetcher(
                MAXIMUM_REDIRECTS,
                MAXIMUM_RESPONSE_BYTES,
                RESPONSE_TIMEOUT,
                blockingResolver);

        PageFetchResult result =
                fetcher.fetch(url("/redirect-blocked"));

        assertThat(result).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> {
                    assertThat(failure.failureType())
                            .isEqualTo(
                                    PageFetchFailureType
                                            .BLOCKED_DESTINATION);
                    assertThat(failure.failedUrl().getHost())
                            .isEqualTo("blocked.branchlight.test");
                    assertThat(failure.redirectChain()).hasSize(2);
                });
        assertThat(targetRequests).hasValue(0);
    }

    @Test
    void enforcesTheMaximumRedirectCount() {
        handlers.put("/first", exchange -> redirect(
                exchange,
                302,
                "/second"));
        handlers.put("/second", exchange -> redirect(
                exchange,
                302,
                "/final"));
        handlers.put("/final", exchange -> respond(
                exchange,
                200,
                "text/plain",
                "must not be reached".getBytes(StandardCharsets.UTF_8)));

        PageFetchResult result = fetcher(
                1,
                MAXIMUM_RESPONSE_BYTES,
                RESPONSE_TIMEOUT,
                localResolver())
                .fetch(url("/first"));

        assertThat(result).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> {
                    assertThat(failure.failureType())
                            .isEqualTo(
                                    PageFetchFailureType
                                            .TOO_MANY_REDIRECTS);
                    assertThat(failure.statusCode()).isEqualTo(302);
                    assertThat(failure.redirectChain())
                            .containsExactly(
                                    URI.create(url("/first")),
                                    URI.create(url("/second")));
                });
    }

    @Test
    void rejectsDeclaredAndStreamingBodiesAboveTheByteLimit() {
        byte[] oversizedBody = "123456789".getBytes(
                StandardCharsets.UTF_8);
        handlers.put("/declared-large", exchange -> respond(
                exchange,
                200,
                "text/plain",
                oversizedBody));
        handlers.put("/chunked-large", exchange -> {
            exchange.getResponseHeaders()
                    .set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write(oversizedBody);
            }
        });
        var fetcher = fetcher(
                MAXIMUM_REDIRECTS,
                8,
                RESPONSE_TIMEOUT,
                localResolver());

        PageFetchResult declared =
                fetcher.fetch(url("/declared-large"));
        PageFetchResult chunked =
                fetcher.fetch(url("/chunked-large"));

        assertTooLarge(declared);
        assertTooLarge(chunked);
    }

    @Test
    void enforcesTheConfiguredResponseTimeout() {
        var releaseResponse = new CountDownLatch(1);
        handlers.put("/slow", exchange -> {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "text/plain");
            exchange.sendResponseHeaders(200, 0);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write('a');
                responseBody.flush();
                releaseResponse.await();
                responseBody.write('b');
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            } catch (IOException exception) {
                exchange.close();
            }
        });
        var fetcher = fetcher(
                MAXIMUM_REDIRECTS,
                MAXIMUM_RESPONSE_BYTES,
                Duration.ofMillis(150),
                localResolver());

        try {
            PageFetchResult result = fetcher.fetch(url("/slow"));

            assertThat(result).isInstanceOfSatisfying(
                    PageFetchFailure.class,
                    failure -> assertThat(failure.failureType())
                            .isEqualTo(
                                    PageFetchFailureType
                                            .RESPONSE_TIMEOUT));
        } finally {
            releaseResponse.countDown();
        }
    }

    @Test
    void convertsUnexpectedDirectFetchFailuresToStructuredResults() {
        DnsResolver failingResolver = dnsResolver(host -> {
            throw new IllegalStateException(
                    "internal detail must not escape");
        });
        var fetcher = fetcher(
                MAXIMUM_REDIRECTS,
                MAXIMUM_RESPONSE_BYTES,
                RESPONSE_TIMEOUT,
                failingResolver);

        PageFetchResult result = fetcher.fetch(url("/unused"));

        assertThat(result).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> {
                    assertThat(failure.failureType())
                            .isEqualTo(
                                    PageFetchFailureType
                                            .UNEXPECTED_ERROR);
                    assertThat(failure.message())
                            .doesNotContain("internal detail");
                });
    }

    @Test
    void keepsFetchingAfterAnIndividualPageFails() {
        handlers.put("/first-ok", exchange -> respond(
                exchange,
                200,
                "text/html",
                "first".getBytes(StandardCharsets.UTF_8)));
        handlers.put("/failure", exchange -> respond(
                exchange,
                500,
                "text/plain",
                "failure body".getBytes(StandardCharsets.UTF_8)));
        handlers.put("/second-ok", exchange -> respond(
                exchange,
                200,
                "text/plain",
                "second".getBytes(StandardCharsets.UTF_8)));

        List<PageFetchResult> results = fetcher().fetchAll(List.of(
                url("/first-ok"),
                url("/failure"),
                url("/second-ok")));

        assertThat(results).hasSize(3);
        assertThat(results.get(0))
                .isInstanceOf(PageFetchSuccess.class);
        assertThat(results.get(1))
                .isInstanceOfSatisfying(
                        PageFetchFailure.class,
                        failure -> assertThat(failure.failureType())
                                .isEqualTo(
                                        PageFetchFailureType.HTTP_ERROR));
        assertThat(results.get(2))
                .isInstanceOf(PageFetchSuccess.class);
    }

    private HttpPageFetcher fetcher() {
        return fetcher(
                MAXIMUM_REDIRECTS,
                MAXIMUM_RESPONSE_BYTES,
                RESPONSE_TIMEOUT,
                localResolver());
    }

    private HttpPageFetcher fetcher(
            int maximumRedirects,
            int maximumResponseBytes,
            Duration responseTimeout,
            DnsResolver dnsResolver) {
        var fetcher = new HttpPageFetcher(
                CONNECTION_TIMEOUT,
                responseTimeout,
                maximumRedirects,
                maximumResponseBytes,
                dnsResolver);
        fetchers.add(fetcher);
        return fetcher;
    }

    private DnsResolver localResolver() {
        return dnsResolver(host -> mockServerAddress());
    }

    private InetAddress[] mockServerAddress()
            throws UnknownHostException {
        return new InetAddress[]{
                InetAddress.getByName("127.0.0.1")
        };
    }

    private static DnsResolver dnsResolver(
            HostResolver hostResolver) {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host)
                    throws UnknownHostException {
                return hostResolver.resolve(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
    }

    private String url(String path) {
        return url("mock.branchlight.test", path);
    }

    private String url(String host, String path) {
        return "http://"
                + host
                + ":"
                + mockServer.getAddress().getPort()
                + path;
    }

    private static void assertTooLarge(PageFetchResult result) {
        assertThat(result).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> assertThat(failure.failureType())
                        .isEqualTo(
                                PageFetchFailureType
                                        .RESPONSE_TOO_LARGE));
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String contentType,
            byte[] body) throws IOException {
        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        }
    }

    private static void redirect(
            HttpExchange exchange,
            int status,
            String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
