package com.branchlight.backend.search.provider.brave;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.provider.SearchProviderException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BraveSearchProviderNetworkTimeoutTest {

    @Test
    void enforcesTheConfiguredResponseTimeout() throws Exception {
        var releaseResponse = new CountDownLatch(1);
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor(
                runnable -> {
                    var thread = new Thread(
                            runnable,
                            "brave-timeout-test-server");
                    thread.setDaemon(true);
                    return thread;
                });
        HttpServer mockServer = createBlockedMockServer(
                releaseResponse,
                serverExecutor);

        try {
            mockServer.start();
            var provider = new BraveSearchProvider(
                    "not-a-real-key",
                    URI.create("http://127.0.0.1:"
                            + mockServer.getAddress().getPort()),
                    Duration.ofMillis(150),
                    JsonMapper.builder().build());

            var exception = assertThrows(
                    SearchProviderException.class,
                    () -> provider.search("virtual threads", 5));

            assertEquals(
                    SearchProviderException.Failure.TIMEOUT,
                    exception.failure());
        } finally {
            releaseResponse.countDown();
            mockServer.stop(0);
            serverExecutor.shutdownNow();
            serverExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static HttpServer createBlockedMockServer(
            CountDownLatch releaseResponse,
            ExecutorService serverExecutor) throws IOException {
        var mockServer = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        mockServer.setExecutor(serverExecutor);
        mockServer.createContext("/res/v1/web/search", exchange -> {
            try {
                releaseResponse.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        return mockServer;
    }
}
