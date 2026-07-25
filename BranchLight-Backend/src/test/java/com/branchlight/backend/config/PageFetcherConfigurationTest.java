package com.branchlight.backend.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.branchlight.backend.search.fetch.HttpPageFetcher;
import com.branchlight.backend.search.fetch.PageFetcher;

import static org.assertj.core.api.Assertions.assertThat;

class PageFetcherConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(
                            new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(
                            PageFetcherConfiguration.class);

    @Test
    void configuresTheDefaultFetcherLimits() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PageFetcher.class);
            var fetcher = context.getBean(HttpPageFetcher.class);

            assertThat(fetcher.connectionTimeout())
                    .isEqualTo(Duration.ofSeconds(3));
            assertThat(fetcher.responseTimeout())
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(fetcher.maximumRedirects()).isEqualTo(5);
            assertThat(fetcher.maximumResponseBytes())
                    .isEqualTo(1_048_576);
        });
    }

    @Test
    void configuresEveryLimitFromTheEnvironment() {
        contextRunner
                .withPropertyValues(
                        "BRANCHLIGHT_PAGE_FETCH"
                                + "_CONNECTION_TIMEOUT=250ms",
                        "BRANCHLIGHT_PAGE_FETCH"
                                + "_RESPONSE_TIMEOUT=750ms",
                        "BRANCHLIGHT_PAGE_FETCH"
                                + "_MAXIMUM_REDIRECTS=2",
                        "BRANCHLIGHT_PAGE_FETCH"
                                + "_MAXIMUM_RESPONSE_BYTES=4096")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var fetcher = context.getBean(
                            HttpPageFetcher.class);

                    assertThat(fetcher.connectionTimeout())
                            .isEqualTo(Duration.ofMillis(250));
                    assertThat(fetcher.responseTimeout())
                            .isEqualTo(Duration.ofMillis(750));
                    assertThat(fetcher.maximumRedirects())
                            .isEqualTo(2);
                    assertThat(fetcher.maximumResponseBytes())
                            .isEqualTo(4096);
                });
    }
}
