package com.branchlight.backend.search.content;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.fetch.PageFetchSuccess;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupContentExtractorEdgeCasesTest {

    private static final URI SOURCE_URL =
            URI.create("https://pages.example/articles/input");

    private final ContentExtractor extractor =
            new JsoupContentExtractor(
                    JsonMapper.builder().build());

    @Test
    void rejectsLinkOnlySemanticContentAsNotMeaningful()
            throws IOException {
        ContentExtractionResult result =
                extractFixture("edge-link-only.html");

        assertThat(result).isInstanceOfSatisfying(
                ContentExtractionFailure.class,
                failure -> assertThat(failure.failureType())
                        .isEqualTo(
                                ContentExtractionFailureType
                                        .NO_MEANINGFUL_CONTENT));
    }

    @Test
    void prefersDenseGenericProseOverShortSemanticTeaser()
            throws IOException {
        ContentExtractionSuccess result =
                extractSuccess("edge-teaser-vs-dense.html");

        assertThat(result.mainText())
                .contains(
                        "Dense analysis begins with evidence",
                        "The final paragraph connects")
                .doesNotContain(
                        "Brief semantic teaser should not win");
        assertThat(result.headings()).containsExactly(
                new ExtractedHeading(
                        1,
                        "Dense generic analysis"));
    }

    @Test
    void ignoresNegativeStructuredCommentCount()
            throws IOException {
        ContentExtractionSuccess result =
                extractSuccess("edge-negative-comment-count.html");

        assertThat(result.possibleCommentOrReplyCount()).isZero();
        assertThat(result.structuredMetadata())
                .singleElement()
                .satisfies(metadata -> assertThat(
                        metadata.rawContent())
                        .contains("\"commentCount\": -12"));
    }

    @Test
    void usesVisibleRelAuthorAndClassBasedTimeValues()
            throws IOException {
        ContentExtractionSuccess result =
                extractSuccess("edge-rel-author-times.html");

        assertThat(result.author()).isEqualTo("Taylor Quinn");
        assertThat(result.publicationDate())
                .isEqualTo("2024-06-01T08:30:00Z");
        assertThat(result.modifiedDate())
                .isEqualTo("2024-06-04T11:45:00Z");
    }

    @Test
    void exposesMicrodataAndRdfaAsStructuredMetadata()
            throws IOException {
        ContentExtractionSuccess result =
                extractSuccess("edge-microdata-rdfa.html");

        assertThat(result.structuredMetadata())
                .extracting(StructuredMetadata::format)
                .contains("MICRODATA", "RDFA");
        assertThat(result.publisher())
                .isEqualTo("Evidence Review Collective");
        assertThat(result.publicationDate())
                .isEqualTo("2025-02-03");
        assertThat(result.structuredMetadata())
                .filteredOn(metadata -> metadata.format()
                        .equals("MICRODATA"))
                .singleElement()
                .satisfies(metadata -> {
                    assertThat(metadata.types())
                            .anyMatch(type -> type.endsWith("Article"));
                    assertThat(metadata.properties().entrySet())
                            .anySatisfy(entry -> {
                                assertThat(entry.getKey())
                                        .containsIgnoringCase("genre");
                                assertThat(entry.getValue())
                                        .contains("Analysis");
                            });
                });
        assertThat(result.structuredMetadata())
                .filteredOn(metadata -> metadata.format()
                        .equals("RDFA"))
                .singleElement()
                .satisfies(metadata -> {
                    assertThat(metadata.types())
                            .anyMatch(type -> type.endsWith("Review"));
                    assertThat(metadata.properties().entrySet())
                            .anySatisfy(entry -> {
                                assertThat(entry.getKey())
                                        .containsIgnoringCase("reviewAspect");
                                assertThat(entry.getValue())
                                        .contains("Evidence quality");
                            });
                });
        assertThat(result.originalMetadata())
                .contains(
                        new OriginalMetadataEntry(
                                "article:itemtype",
                                "itemtype",
                                "https://schema.org/Article"),
                        new OriginalMetadataEntry(
                                "section:typeof",
                                "typeof",
                                "Review"));
    }

    private ContentExtractionSuccess extractSuccess(
            String fixtureName) throws IOException {
        ContentExtractionResult result =
                extractFixture(fixtureName);
        assertThat(result)
                .isInstanceOf(ContentExtractionSuccess.class);
        return (ContentExtractionSuccess) result;
    }

    private ContentExtractionResult extractFixture(
            String fixtureName) throws IOException {
        String html = fixture(fixtureName);
        var fetchedPage = new PageFetchSuccess(
                SOURCE_URL.toString(),
                SOURCE_URL,
                200,
                "text/html",
                StandardCharsets.UTF_8,
                html,
                html.getBytes(StandardCharsets.UTF_8).length,
                List.of(SOURCE_URL));
        return extractor.extract(fetchedPage);
    }

    private static String fixture(String fixtureName)
            throws IOException {
        String resourcePath =
                "/fixtures/content/" + fixtureName;
        try (var input = JsoupContentExtractorEdgeCasesTest.class
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException(
                        "Fixture not found: " + resourcePath);
            }
            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
