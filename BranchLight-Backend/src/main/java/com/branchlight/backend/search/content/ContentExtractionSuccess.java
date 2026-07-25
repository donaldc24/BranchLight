package com.branchlight.backend.search.content;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record ContentExtractionSuccess(
        URI sourceUrl,
        String pageTitle,
        URI canonicalUrl,
        String mainText,
        List<ExtractedHeading> headings,
        String author,
        String publisher,
        String publicationDate,
        String modifiedDate,
        String metaDescription,
        List<StructuredMetadata> structuredMetadata,
        int codeBlockCount,
        int orderedListCount,
        int unorderedListCount,
        int tableCount,
        int outboundLinkCount,
        int possibleCommentOrReplyCount,
        int visibleWordCount,
        List<OriginalMetadataEntry> originalMetadata)
        implements ContentExtractionResult {

    public ContentExtractionSuccess {
        Objects.requireNonNull(sourceUrl, "sourceUrl must not be null");
        Objects.requireNonNull(pageTitle, "pageTitle must not be null");
        Objects.requireNonNull(mainText, "mainText must not be null");
        headings = List.copyOf(Objects.requireNonNull(
                headings,
                "headings must not be null"));
        structuredMetadata = List.copyOf(Objects.requireNonNull(
                structuredMetadata,
                "structuredMetadata must not be null"));
        originalMetadata = List.copyOf(Objects.requireNonNull(
                originalMetadata,
                "originalMetadata must not be null"));

        if (canonicalUrl != null && !canonicalUrl.isAbsolute()) {
            throw new IllegalArgumentException(
                    "canonicalUrl must be absolute");
        }
        if (mainText.isBlank()) {
            throw new IllegalArgumentException(
                    "mainText must not be blank");
        }
        requireOptionalText(author, "author");
        requireOptionalText(publisher, "publisher");
        requireOptionalText(publicationDate, "publicationDate");
        requireOptionalText(modifiedDate, "modifiedDate");
        requireOptionalText(metaDescription, "metaDescription");

        requireNonNegative(codeBlockCount, "codeBlockCount");
        requireNonNegative(orderedListCount, "orderedListCount");
        requireNonNegative(unorderedListCount, "unorderedListCount");
        requireNonNegative(tableCount, "tableCount");
        requireNonNegative(outboundLinkCount, "outboundLinkCount");
        requireNonNegative(
                possibleCommentOrReplyCount,
                "possibleCommentOrReplyCount");
        if (visibleWordCount <= 0) {
            throw new IllegalArgumentException(
                    "visibleWordCount must be greater than zero");
        }
    }

    @Override
    public String toString() {
        return "ContentExtractionSuccess[sourceUrl="
                + sourceUrl
                + ", pageTitle="
                + pageTitle
                + ", canonicalUrl="
                + canonicalUrl
                + ", headingCount="
                + headings.size()
                + ", structuredMetadataCount="
                + structuredMetadata.size()
                + ", codeBlockCount="
                + codeBlockCount
                + ", orderedListCount="
                + orderedListCount
                + ", unorderedListCount="
                + unorderedListCount
                + ", tableCount="
                + tableCount
                + ", outboundLinkCount="
                + outboundLinkCount
                + ", possibleCommentOrReplyCount="
                + possibleCommentOrReplyCount
                + ", visibleWordCount="
                + visibleWordCount
                + ", mainText=<redacted>, originalMetadata=<redacted>]";
    }

    private static void requireOptionalText(
            String value,
            String name) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must be null or non-blank");
        }
    }

    private static void requireNonNegative(
            int value,
            String name) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    name + " must not be negative");
        }
    }
}
