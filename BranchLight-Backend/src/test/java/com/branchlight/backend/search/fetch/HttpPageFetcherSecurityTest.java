package com.branchlight.backend.search.fetch;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpPageFetcherSecurityTest {

    private final HttpPageFetcher fetcher = new HttpPageFetcher(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            2,
            1024);

    @AfterEach
    void tearDown() {
        fetcher.close();
    }

    @Test
    void rejectsMalformedAndUnsupportedUrlsWithoutConnecting() {
        assertFailureType("", PageFetchFailureType.MALFORMED_URL);
        assertFailureType(
                "not a URL",
                PageFetchFailureType.MALFORMED_URL);
        assertFailureType(
                "example.com/page",
                PageFetchFailureType.MALFORMED_URL);
        assertFailureType(
                "http://user:password@example.com/page",
                PageFetchFailureType.MALFORMED_URL);
        assertFailureType(
                "http://example.com:70000/page",
                PageFetchFailureType.MALFORMED_URL);
        assertFailureType(
                "file:///tmp/page.html",
                PageFetchFailureType.UNSUPPORTED_SCHEME);
        assertFailureType(
                "ftp://example.com/page",
                PageFetchFailureType.UNSUPPORTED_SCHEME);
    }

    @Test
    void blocksLocalPrivateAndLinkLocalDestinations() {
        for (String blockedUrl : List.of(
                "http://localhost:8080/page",
                "http://service.localhost:8080/page",
                "http://127.0.0.1:8080/page",
                "http://0.0.0.1/page",
                "http://10.0.0.1/page",
                "http://172.16.0.1/page",
                "http://192.168.1.1/page",
                "http://169.254.169.254/latest/meta-data",
                "http://100.64.0.1/page",
                "http://[::1]/page",
                "http://[fe80::1]/page",
                "http://[fc00::1]/page")) {
            assertFailureType(
                    blockedUrl,
                    PageFetchFailureType.BLOCKED_DESTINATION);
        }
    }

    @Test
    void rejectsAHostWhenAnyResolvedAddressIsPrivate()
            throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(
                new byte[]{93, (byte) 184, (byte) 216, 34});
        InetAddress privateAddress = InetAddress.getByAddress(
                new byte[]{10, 0, 0, 5});
        var publicOnly = new PublicNetworkDestinationValidator(
                host -> new InetAddress[]{publicAddress});
        var mixed = new PublicNetworkDestinationValidator(
                host -> new InetAddress[]{
                        publicAddress,
                        privateAddress
                });
        assertThatCode(() -> publicOnly.resolve("public.test"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> mixed.resolve("public.test"))
                .isInstanceOf(BlockedDestinationException.class);
    }

    private void assertFailureType(
            String url,
            PageFetchFailureType expectedFailureType) {
        assertThat(fetcher.fetch(url)).isInstanceOfSatisfying(
                PageFetchFailure.class,
                failure -> assertThat(failure.failureType())
                        .isEqualTo(expectedFailureType));
    }
}
