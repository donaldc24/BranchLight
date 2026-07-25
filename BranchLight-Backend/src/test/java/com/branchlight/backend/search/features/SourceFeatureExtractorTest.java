package com.branchlight.backend.search.features;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.aggregation.AggregatedSearchResult;
import com.branchlight.backend.search.content.ContentExtractionResult;
import com.branchlight.backend.search.content.ContentExtractionSuccess;
import com.branchlight.backend.search.content.ExtractedBlock;
import com.branchlight.backend.search.content.ExtractedDocument;
import com.branchlight.backend.search.content.JsoupContentExtractor;
import com.branchlight.backend.search.content.Passage;
import com.branchlight.backend.search.content.SourcePosition;
import com.branchlight.backend.search.fetch.PageFetchSuccess;
import com.branchlight.backend.search.ranking.CandidateDocument;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFeatureExtractorTest {

    private static final URI SOURCE_URL =
            URI.create("https://fixture.example/document");

    private final JsoupContentExtractor contentExtractor =
            new JsoupContentExtractor(JsonMapper.builder().build());
    private final SourceFeatureExtractor featureExtractor =
            new SourceFeatureExtractor();

    @Test
    void extractsProvenanceFeaturesFromFixture() throws IOException {
        SourceFeatureSet features = extract("provenance.html");

        assertEveryFeatureObserved(features, SourceFeatureGroup.PROVENANCE);
        assertThat(features.value(
                SourceFeature.REFERENCES_OR_CITATIONS_PRESENT).rawValue())
                .isGreaterThanOrEqualTo(2.0);
        assertThat(features.value(
                SourceFeature.STRUCTURED_METADATA_PRESENT).rawValue())
                .isEqualTo(1.0);
    }

    @Test
    void extractsExplanationFeaturesFromFixture() throws IOException {
        SourceFeatureSet features = extract("explanation.html");

        assertEveryFeatureObserved(features, SourceFeatureGroup.EXPLANATION);
        SourceFeatureValue readability = features.value(
                SourceFeature.READABILITY_ESTIMATE);
        assertThat(readability.rawValue()).isGreaterThan(1.0);
        assertThat(readability.normalizedValue()).isBetween(0.0, 1.0);
        assertThat(readability.rawValue())
                .isNotEqualTo(readability.normalizedValue());
    }

    @Test
    void extractsPracticalFeaturesFromFixture() throws IOException {
        SourceFeatureSet features = extract("practical.html");

        assertEveryFeatureObserved(features, SourceFeatureGroup.PRACTICAL);
        assertThat(features.value(SourceFeature.ORDERED_STEPS).rawValue())
                .isEqualTo(5.0);
        assertThat(features.value(
                SourceFeature.DOWNLOADABLE_OR_REPOSITORY_LINKS).rawValue())
                .isEqualTo(2.0);
    }

    @Test
    void extractsCriticalFeaturesFromFixture() throws IOException {
        SourceFeatureSet features = extract("critical.html");

        assertEveryFeatureObserved(features, SourceFeatureGroup.CRITICAL);
        assertThat(features.value(
                SourceFeature.METHODOLOGY_LIMITATIONS).rawValue())
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void extractsHumanDiscussionFeaturesFromFixture()
            throws IOException {
        SourceFeatureSet features = extract("human-discussion.html");

        assertEveryFeatureObserved(
                features,
                SourceFeatureGroup.HUMAN_DISCUSSION);
        assertThat(features.value(
                SourceFeature.MULTIPLE_PARTICIPANTS).rawValue())
                .isEqualTo(3.0);
        assertThat(features.value(
                SourceFeature.CONVERSATION_DEPTH).rawValue())
                .isGreaterThanOrEqualTo(3.0);
    }

    @Test
    void extractsQualityAndRiskFeaturesFromFixture()
            throws IOException {
        SourceFeatureSet features = extract("quality-risk.html");

        assertEveryFeatureObserved(
                features,
                SourceFeatureGroup.QUALITY_AND_RISK);
        assertThat(features.value(SourceFeature.THIN_CONTENT).rawValue())
                .isGreaterThan(0.0);
        assertThat(features.value(SourceFeature.THIN_CONTENT)
                .normalizedValue()).isGreaterThan(0.0);
        assertThat(features.value(SourceFeature.DUPLICATED_TEXT).rawValue())
                .isGreaterThan(0.0);
    }

    @Test
    void documentsEveryHeuristicAndReturnsEveryFeature()
            throws IOException {
        SourceFeatureSet features = extract("explanation.html");

        assertThat(SourceFeature.values())
                .allSatisfy(feature -> assertThat(feature.heuristic())
                        .isNotBlank());
        assertThat(features.features())
                .hasSize(SourceFeature.values().length);
        assertThat(features.documentId()).isEqualTo("fixture-document");
    }

    private SourceFeatureSet extract(String fixtureName)
            throws IOException {
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
        ContentExtractionResult extractionResult =
                contentExtractor.extract(fetchedPage);
        assertThat(extractionResult)
                .isInstanceOf(ContentExtractionSuccess.class);
        ContentExtractionSuccess extraction =
                (ContentExtractionSuccess) extractionResult;

        SourcePosition position = new SourcePosition(
                0,
                extraction.mainText().length());
        var blocks = new ArrayList<ExtractedBlock>();
        extraction.headings().forEach(heading -> blocks.add(
                ExtractedBlock.heading(
                        heading.level(),
                        heading.text(),
                        position)));
        blocks.add(ExtractedBlock.paragraph(
                extraction.mainText(),
                position));
        var document = new ExtractedDocument(
                "fixture-document",
                blocks);
        var passage = new Passage(
                document.documentId(),
                extraction.headings().stream()
                        .map(heading -> heading.text())
                        .toList(),
                extraction.mainText(),
                position,
                extraction.visibleWordCount());
        var searchResult = new AggregatedSearchResult(
                extraction.sourceUrl(),
                extraction.pageTitle(),
                1,
                null,
                List.of(),
                List.of());
        var candidate = new CandidateDocument(
                searchResult,
                document,
                List.of(passage));

        return featureExtractor.extract(new SourceFeatureInput(
                candidate,
                extraction,
                html));
    }

    private static void assertEveryFeatureObserved(
            SourceFeatureSet features,
            SourceFeatureGroup group) {
        assertThat(features.group(group))
                .isNotEmpty()
                .allSatisfy((feature, value) -> {
                    assertThat(value.rawValue())
                            .as(feature.name() + " raw value")
                            .isGreaterThan(0.0);
                    assertThat(value.normalizedValue())
                            .as(feature.name() + " normalized value")
                            .isGreaterThan(0.0)
                            .isLessThanOrEqualTo(1.0);
                });
    }

    private static String fixture(String fixtureName)
            throws IOException {
        String path = "/fixtures/features/" + fixtureName;
        try (var input = SourceFeatureExtractorTest.class
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Fixture not found: " + path);
            }
            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}