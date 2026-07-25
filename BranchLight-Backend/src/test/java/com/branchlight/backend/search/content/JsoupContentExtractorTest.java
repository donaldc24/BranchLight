package com.branchlight.backend.search.content;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.fetch.PageFetchSuccess;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupContentExtractorTest {

    private static final URI SOURCE_URL =
            URI.create("https://pages.example/articles/input");

    private final ContentExtractor extractor =
            new JsoupContentExtractor(
                    JsonMapper.builder().build());

    @Test
    void extractsSemanticArticleAndRemovesGenericBoilerplate()
            throws IOException {
        ContentExtractionSuccess result = extractSuccess(
                "semantic-article.html");

        assertThat(result.pageTitle())
                .isEqualTo("Understanding Branching Systems");
        assertThat(result.canonicalUrl()).isEqualTo(
                URI.create(
                        "https://pages.example/guides/branching-systems"));
        assertThat(result.author()).isEqualTo("Alex Rivera");
        assertThat(result.publisher())
                .isEqualTo("Open Knowledge Review");
        assertThat(result.publicationDate())
                .isEqualTo("2026-04-02T09:30:00Z");
        assertThat(result.modifiedDate())
                .isEqualTo("2026-04-05T14:00:00Z");
        assertThat(result.metaDescription()).isEqualTo(
                "A practical explanation of branching systems.");
        assertThat(result.headings()).containsExactly(
                new ExtractedHeading(
                        1,
                        "Understanding Branching Systems"),
                new ExtractedHeading(
                        2,
                        "Applying the pattern"));
        assertThat(result.mainText())
                .contains(
                        "A field guide for careful readers.",
                        "Branching systems organize related paths",
                        "branch(\"clear choice\", \"retained context\");")
                .doesNotContain(
                        "global masthead",
                        "cookie notice",
                        "Sponsored advertisement",
                        "Recommended material",
                        "Hidden words",
                        "Repeated site chrome");
        assertThat(result.codeBlockCount()).isEqualTo(1);
        assertThat(result.orderedListCount()).isEqualTo(1);
        assertThat(result.unorderedListCount()).isEqualTo(1);
        assertThat(result.tableCount()).isEqualTo(1);
        assertThat(result.outboundLinkCount()).isEqualTo(1);
        assertThat(result.possibleCommentOrReplyCount()).isZero();
        assertThat(result.visibleWordCount()).isGreaterThan(70);
        assertThat(result.originalMetadata())
                .contains(new OriginalMetadataEntry(
                        "meta:name",
                        "author",
                        "Alex Rivera"));
        assertThat(result.toString())
                .doesNotContain("Branching systems organize");
    }

    @Test
    void extractsJsonLdAndPreservesMalformedSiblingMetadata()
            throws IOException {
        ContentExtractionSuccess result = extractSuccess(
                "metadata-jsonld.html");

        assertThat(result.author())
                .isEqualTo("Jordan Lee, Morgan Ray");
        assertThat(result.publisher())
                .isEqualTo("Independent Press");
        assertThat(result.publicationDate())
                .isEqualTo("2025-11-03");
        assertThat(result.modifiedDate())
                .isEqualTo("2025-11-08T10:15:00Z");
        assertThat(result.metaDescription()).isEqualTo(
                "Description discovered from structured metadata.");
        assertThat(result.possibleCommentOrReplyCount())
                .isEqualTo(14);
        assertThat(result.structuredMetadata()).hasSize(1);

        StructuredMetadata metadata =
                result.structuredMetadata().get(0);
        assertThat(metadata.format()).isEqualTo("JSON_LD");
        assertThat(metadata.types())
                .contains("Article", "Person", "Organization");
        assertThat(metadata.properties().entrySet())
                .anySatisfy(entry -> {
                    assertThat(entry.getKey())
                            .endsWith(".datePublished");
                    assertThat(entry.getValue())
                            .containsExactly("2025-11-03");
                });
        assertThat(metadata.rawContent())
                .contains("\"commentCount\": 14");

        assertThat(result.originalMetadata())
                .filteredOn(entry -> entry.name()
                        .equals("application/ld+json"))
                .hasSize(2)
                .anySatisfy(entry -> assertThat(entry.value())
                        .contains("\"headline\":"));
    }

    @Test
    void selectsDenseContentWithoutSemanticLandmarks()
            throws IOException {
        ContentExtractionSuccess result = extractSuccess(
                "generic-div-layout.html");

        assertThat(result.canonicalUrl()).isNull();
        assertThat(result.mainText())
                .contains(
                        "Finding content without semantic landmarks",
                        "text density",
                        "A second section")
                .doesNotContain(
                        "Menu one",
                        "Sidebar destination",
                        "Cookie consent",
                        "Related and recommended");
        assertThat(result.headings()).containsExactly(
                new ExtractedHeading(
                        1,
                        "Finding content without semantic landmarks"),
                new ExtractedHeading(2, "A second section"));
        assertThat(result.outboundLinkCount()).isEqualTo(1);
        assertThat(result.originalMetadata())
                .contains(new OriginalMetadataEntry(
                        "link:rel",
                        "canonical",
                        "mailto:not-canonical@example.test"));
    }

    @Test
    void retainsDiscussionContentAndEstimatesReplyCount()
            throws IOException {
        ContentExtractionSuccess result = extractSuccess(
                "discussion-thread.html");

        assertThat(result.mainText())
                .contains(
                        "The opening post asks",
                        "One participant compares",
                        "Another participant looks",
                        "A reply adds");
        assertThat(result.headings()).containsExactly(
                new ExtractedHeading(
                        1,
                        "Discussion About Evidence"),
                new ExtractedHeading(2, "First perspective"),
                new ExtractedHeading(2, "Second perspective"));
        assertThat(result.possibleCommentOrReplyCount())
                .isEqualTo(3);
    }

    @Test
    void returnsStructuredFailureForBoilerplateOnlyPage()
            throws IOException {
        ContentExtractionResult result = extractFixture(
                "boilerplate-only.html");

        assertThat(result).isInstanceOfSatisfying(
                ContentExtractionFailure.class,
                failure -> {
                    assertThat(failure.failureType()).isEqualTo(
                            ContentExtractionFailureType
                                    .NO_MEANINGFUL_CONTENT);
                    assertThat(failure.originalMetadata())
                            .contains(new OriginalMetadataEntry(
                                    "meta:name",
                                    "author",
                                    "Diagnostic Author"))
                            .contains(
                                    new OriginalMetadataEntry(
                                            "div:itemtype",
                                            "itemtype",
                                            "https://schema.org/Article"),
                                    new OriginalMetadataEntry(
                                            "section:typeof",
                                            "typeof",
                                            "Article"),
                                    new OriginalMetadataEntry(
                                            "body:prefix",
                                            "prefix",
                                            "schema: https://schema.org/"));
                });
        assertThat(result.successful()).isFalse();
    }

    @Test
    void returnsStructuredFailureForNonHtmlFetchedContent() {
        String plainText =
                "This fetched page is plain text rather than HTML.";
        var fetchedPage = new PageFetchSuccess(
                SOURCE_URL.toString(),
                SOURCE_URL,
                200,
                "text/plain",
                StandardCharsets.UTF_8,
                plainText,
                plainText.getBytes(StandardCharsets.UTF_8).length,
                List.of(SOURCE_URL));

        ContentExtractionResult result =
                extractor.extract(fetchedPage);

        assertThat(result).isInstanceOfSatisfying(
                ContentExtractionFailure.class,
                failure -> assertThat(failure.failureType())
                        .isEqualTo(
                                ContentExtractionFailureType
                                        .UNSUPPORTED_CONTENT_TYPE));
    }

    private ContentExtractionSuccess extractSuccess(
            String fixtureName) throws IOException {
        ContentExtractionResult result = extractFixture(fixtureName);
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
        try (var input = JsoupContentExtractorTest.class
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
