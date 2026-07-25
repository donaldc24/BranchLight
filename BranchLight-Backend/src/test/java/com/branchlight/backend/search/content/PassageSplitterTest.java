package com.branchlight.backend.search.content;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PassageSplitterTest {

    private final PassageSplitter splitter =
            new PassageSplitter(20, 30, 4);

    @Test
    void keepsShortRelatedParagraphsTogether() {
        ExtractedDocument document = document(
                paragraph("A concise opening introduces the topic."),
                paragraph("A related detail completes the explanation."));

        List<Passage> passages = splitter.split(document);

        assertThat(passages).singleElement().satisfies(passage -> {
            assertThat(passage.documentId()).isEqualTo("document-1");
            assertThat(passage.headingPath()).isEmpty();
            assertThat(passage.text()).contains(
                    "introduces the topic.\n\nA related detail");
            assertThat(passage.approximateWordCount()).isEqualTo(12);
            assertThat(passage.position()).isEqualTo(
                    new SourcePosition(0, 200));
        });
    }

    @Test
    void dividesLongPagesAtParagraphBoundaries() {
        ExtractedDocument document = document(
                paragraph(words("alpha", 12) + "."),
                paragraph(words("bravo", 12) + "."),
                paragraph(words("charlie", 12) + "."),
                paragraph(words("delta", 12) + "."));

        List<Passage> passages = splitter.split(document);

        assertThat(passages).hasSize(2);
        assertThat(passages)
                .allSatisfy(passage -> assertThat(
                        passage.approximateWordCount())
                        .isLessThanOrEqualTo(30));
        assertThat(passages.get(0).text())
                .contains("alpha", "bravo")
                .doesNotContain("charlie");
        assertThat(passages.get(1).text())
                .contains("charlie", "delta");
    }

    @Test
    void preservesNestedHeadingPathsAndHeadingBoundaries() {
        ExtractedDocument document = document(
                heading(1, "Guide"),
                paragraph("The guide opens with shared context."),
                heading(2, "Setup"),
                paragraph("Setup instructions belong in this section."),
                heading(3, "Windows"),
                paragraph("Platform details retain every parent heading."),
                heading(2, "Operation"),
                paragraph("Operating guidance starts a sibling section."));

        List<Passage> passages = splitter.split(document);

        assertThat(passages).hasSize(4);
        assertThat(passages)
                .extracting(Passage::headingPath)
                .containsExactly(
                        List.of("Guide"),
                        List.of("Guide", "Setup"),
                        List.of("Guide", "Setup", "Windows"),
                        List.of("Guide", "Operation"));
        assertThat(passages)
                .extracting(Passage::text)
                .containsExactly(
                        "The guide opens with shared context.",
                        "Setup instructions belong in this section.",
                        "Platform details retain every parent heading.",
                        "Operating guidance starts a sibling section.");
    }

    @Test
    void keepsAListTogetherWithItsShortIntroduction() {
        ExtractedDocument document = document(
                heading(1, "Checklist"),
                paragraph("Complete these related steps."),
                list("First item\nSecond item\nThird item"),
                heading(2, "Afterwards"));

        List<Passage> passages = splitter.split(document);

        assertThat(passages).singleElement().satisfies(passage -> {
            assertThat(passage.headingPath())
                    .containsExactly("Checklist");
            assertThat(passage.text())
                    .contains("related steps", "First item", "Third item");
        });
        assertThat(passages).noneMatch(
                passage -> passage.text().isBlank());
    }

    @Test
    void splitsOversizedParagraphsWithBoundedOverlap() {
        String firstSentence = words("first", 18) + ".";
        String secondSentence = words("second", 18) + ".";
        String oversizedSentence = words("oversized", 65) + ".";
        ExtractedDocument document = document(paragraph(
                firstSentence + " " + secondSentence
                        + " " + oversizedSentence));

        List<Passage> passages = splitter.split(document);

        assertThat(passages).hasSizeGreaterThan(3);
        assertThat(passages)
                .allSatisfy(passage -> {
                    assertThat(passage.text()).isNotBlank();
                    assertThat(passage.approximateWordCount())
                            .isBetween(1, 30);
                });
        assertThat(passages.get(0).text()).isEqualTo(firstSentence);
        assertThat(passages.get(1).text())
                .startsWith("first first first first.")
                .contains("second");
        assertThat(passages)
                .filteredOn(passage -> passage.text()
                        .contains("oversized"))
                .hasSize(3);
    }

    private static ExtractedDocument document(
            DraftBlock... draftBlocks) {
        var blocks = new ArrayList<ExtractedBlock>();
        for (int index = 0; index < draftBlocks.length; index++) {
            DraftBlock draft = draftBlocks[index];
            SourcePosition position = new SourcePosition(
                    index * 100,
                    (index + 1) * 100);
            blocks.add(switch (draft.type()) {
                case HEADING -> ExtractedBlock.heading(
                        draft.headingLevel(),
                        draft.text(),
                        position);
                case PARAGRAPH -> ExtractedBlock.paragraph(
                        draft.text(),
                        position);
                case LIST -> ExtractedBlock.list(
                        draft.text(),
                        position);
            });
        }
        return new ExtractedDocument("document-1", blocks);
    }

    private static DraftBlock heading(int level, String text) {
        return new DraftBlock(
                ExtractedBlock.Type.HEADING,
                text,
                level);
    }

    private static DraftBlock paragraph(String text) {
        return new DraftBlock(
                ExtractedBlock.Type.PARAGRAPH,
                text,
                0);
    }

    private static DraftBlock list(String text) {
        return new DraftBlock(
                ExtractedBlock.Type.LIST,
                text,
                0);
    }

    private static String words(String word, int count) {
        return IntStream.range(0, count)
                .mapToObj(ignored -> word)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();
    }

    private record DraftBlock(
            ExtractedBlock.Type type,
            String text,
            int headingLevel) {
    }
}