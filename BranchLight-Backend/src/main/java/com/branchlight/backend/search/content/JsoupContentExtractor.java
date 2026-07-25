package com.branchlight.backend.search.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.branchlight.backend.search.fetch.PageFetchSuccess;

public final class JsoupContentExtractor
        implements ContentExtractor {

    private static final Set<String> SUPPORTED_CONTENT_TYPES =
            Set.of("text/html", "application/xhtml+xml");

    private static final String CONTENT_CANDIDATE_SELECTOR =
            "article, main, [role=main], section, div";

    private static final String SUBSTANTIVE_BLOCK_SELECTOR =
            "p, pre, blockquote, li, td, th";

    private static final String TEXT_BOUNDARY_SELECTOR =
            "address, article, aside, blockquote, dd, div, dl, dt, "
                    + "figcaption, figure, footer, h1, h2, h3, h4, "
                    + "h5, h6, header, li, main, ol, p, pre, section, "
                    + "table, tbody, td, tfoot, th, thead, tr, ul";

    private static final String ALWAYS_REMOVED_SELECTOR =
            "script, style, noscript, template, iframe, object, embed, "
                    + "canvas, svg, dialog, nav, footer, aside, "
                    + "button, input, select, textarea";

    private static final Pattern BOILERPLATE_IDENTIFIER_PATTERN =
            Pattern.compile(
                    "(?:^|[^\\p{L}\\p{N}])"
                            + "(?:cookie[-_ ]?(?:banner|consent|notice"
                            + "|preferences|prompt)|consent[-_ ]?"
                            + "(?:banner|dialog|notice|prompt)"
                            + "|advert(?:isement|ising)?|ads?[-_ ]?"
                            + "(?:banner|container|slot|unit|wrapper)"
                            + "|sponsor(?:ed)?[-_ ]?(?:banner|content"
                            + "|link|module|placement|promo)"
                            + "|promo(?:tion)?[-_ ]?(?:banner|box|module)"
                            + "|related[-_ ]?(?:articles?|content|links?"
                            + "|posts?|reading|resources|stories)"
                            + "|recommend(?:ed|ation|ations)?[-_ ]?"
                            + "(?:articles?|content|links?|posts?"
                            + "|reading|resources|stories)"
                            + "|share[-_ ]?(?:bar|buttons?|links?|tools?"
                            + "|widget)|social[-_ ]?(?:bar|buttons?"
                            + "|links?|share|sharing|tools?|widget)"
                            + "|newsletter[-_ ]?(?:form|signup|subscribe)"
                            + "|subscribe[-_ ]?(?:button|cta|form)"
                            + "|breadcrumb|pagination|modal|popup|sidebar"
                            + "|toolbar|(?:top|main|primary)[-_ ]?menu"
                            + "|navigation[-_ ]?menu"
                            + "|(?:site|global)[-_ ]?"
                            + "(?:nav|header|footer|chrome|menu|utility))"
                            + "(?:$|[^\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE);

    private static final Pattern CONTENT_IDENTIFIER_PATTERN =
            Pattern.compile(
                    "(?:^|[^\\p{L}\\p{N}])"
                            + "(?:article|content|entry|main|post|story|text)"
                            + "(?:$|[^\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE);

    private static final Pattern COMMENT_IDENTIFIER_PATTERN =
            Pattern.compile(
                    "(?:^|[^\\p{L}\\p{N}])"
                            + "(?:comment|reply|response)"
                            + "(?:$|[^\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE);

    private static final Pattern COMMENT_NON_INSTANCE_PATTERN =
            Pattern.compile(
                    "(?:^|[^\\p{L}\\p{N}])"
                            + "(?:count|form|header|list|thread|section"
                            + "|container|wrapper|feed)"
                            + "(?:$|[^\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE);

    private static final Pattern WORD_PATTERN =
            Pattern.compile(
                    "[\\p{L}\\p{N}]+"
                            + "(?:['\u2019-][\\p{L}\\p{N}]+)*",
                    Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern INTEGER_PATTERN =
            Pattern.compile("\\b(\\d[\\d,]*)\\b");

    private static final Pattern NEGATIVE_INTEGER_PATTERN =
            Pattern.compile("(?:^|\\D)-\\s*\\d");

    private static final Pattern PUBLISHED_IDENTIFIER_PATTERN =
            Pattern.compile(
                    "(?:^|[^\\p{L}\\p{N}])"
                            + "(?:published|publication[-_ ]?date"
                            + "|publish[-_ ]?(?:date|time))"
                            + "(?:$|[^\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE);

    private static final Pattern MODIFIED_IDENTIFIER_PATTERN =
            Pattern.compile(
                    "(?:^|[^\\p{L}\\p{N}])"
                            + "(?:modified|updated|last[-_ ]?modified)"
                            + "(?:$|[^\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE);

    private static final Pattern HORIZONTAL_WHITESPACE_PATTERN =
            Pattern.compile("[\\p{Zs}\\t\\x0B\\f]+");

    private static final int MINIMUM_MEANINGFUL_WORDS = 12;

    private static final int MINIMUM_MEANINGFUL_CHARACTERS = 100;

    private static final int MAXIMUM_JSON_DEPTH = 32;

    private static final int MAXIMUM_STRUCTURED_PROPERTIES = 512;

    private final JsonMapper jsonMapper;

    public JsoupContentExtractor(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper must not be null");
    }

    @Override
    public ContentExtractionResult extract(
            PageFetchSuccess fetchedPage) {
        Objects.requireNonNull(
                fetchedPage,
                "fetchedPage must not be null");
        URI sourceUrl = fetchedPage.finalUrl();

        if (!isSupportedContentType(fetchedPage.contentType())) {
            return failure(
                    sourceUrl,
                    ContentExtractionFailureType
                            .UNSUPPORTED_CONTENT_TYPE,
                    "Fetched page is not HTML",
                    List.of());
        }

        Document document;
        try {
            document = Jsoup.parse(
                    fetchedPage.content(),
                    sourceUrl.toString());
        } catch (RuntimeException exception) {
            return failure(
                    sourceUrl,
                    ContentExtractionFailureType.MALFORMED_CONTENT,
                    "Fetched HTML could not be parsed",
                    List.of());
        }

        List<OriginalMetadataEntry> originalMetadata = List.of();
        try {
            originalMetadata = captureOriginalMetadata(document);
            List<ParsedStructuredMetadata> parsedStructuredMetadata =
                    extractStructuredMetadata(document);
            List<StructuredMetadata> structuredMetadata =
                    parsedStructuredMetadata.stream()
                            .map(ParsedStructuredMetadata::metadata)
                            .toList();

            URI canonicalUrl = extractCanonicalUrl(
                    document,
                    sourceUrl);
            String pageTitle = extractPageTitle(
                    document,
                    parsedStructuredMetadata);
            String author = firstNonBlank(
                    findFirstStructuredText(
                            parsedStructuredMetadata,
                            "author"),
                    firstMetaValue(
                            document,
                            "author",
                            "article:author",
                            "byl",
                            "dc.creator",
                            "dcterms.creator",
                            "citation_author"),
                    firstItempropValue(document, "author"),
                    firstRelValue(document, "author"));
            String publisher = firstNonBlank(
                    findFirstStructuredText(
                            parsedStructuredMetadata,
                            "publisher"),
                    firstMetaValue(
                            document,
                            "publisher",
                            "og:site_name",
                            "application-name",
                            "dc.publisher",
                            "citation_journal_title"),
                    firstItempropValue(document, "publisher"));
            String publicationDate = firstNonBlank(
                    findFirstStructuredText(
                            parsedStructuredMetadata,
                            "datePublished"),
                    firstMetaValue(
                            document,
                            "article:published_time",
                            "datepublished",
                            "date",
                            "pubdate",
                            "publish-date",
                            "dc.date",
                            "dcterms.created",
                            "citation_publication_date"),
                    firstItempropValue(
                            document,
                            "datePublished"),
                    firstPublishedTimeValue(document));
            String modifiedDate = firstNonBlank(
                    findFirstStructuredText(
                            parsedStructuredMetadata,
                            "dateModified"),
                    firstMetaValue(
                            document,
                            "article:modified_time",
                            "datemodified",
                            "last-modified",
                            "dcterms.modified"),
                    firstItempropValue(
                            document,
                            "dateModified"),
                    firstModifiedTimeValue(document));
            String metaDescription = firstNonBlank(
                    firstMetaValue(document, "description"),
                    firstMetaValue(document, "og:description"),
                    firstMetaValue(document, "twitter:description"),
                    findFirstStructuredText(
                            parsedStructuredMetadata,
                            "description"));

            Document cleanedDocument = document.clone();
            removeBoilerplate(cleanedDocument);
            Element mainContent = selectMainContent(cleanedDocument);
            if (mainContent == null) {
                return noMeaningfulContent(
                        sourceUrl,
                        originalMetadata);
            }

            String mainText = extractMainText(mainContent);
            int visibleWordCount = countWords(mainText);
            if (!isMeaningful(mainText, visibleWordCount)
                    || !hasSufficientNonLinkContent(mainContent)) {
                return noMeaningfulContent(
                        sourceUrl,
                        originalMetadata);
            }

            List<ExtractedHeading> headings =
                    extractHeadings(mainContent);
            int possibleCommentOrReplyCount =
                    discoverCommentOrReplyCount(
                            parsedStructuredMetadata,
                            cleanedDocument);

            return new ContentExtractionSuccess(
                    sourceUrl,
                    Objects.requireNonNullElse(pageTitle, ""),
                    canonicalUrl,
                    mainText,
                    headings,
                    author,
                    publisher,
                    publicationDate,
                    modifiedDate,
                    metaDescription,
                    structuredMetadata,
                    mainContent.select("pre").size(),
                    mainContent.select("ol").size(),
                    mainContent.select("ul").size(),
                    mainContent.select("table").size(),
                    countOutboundLinks(mainContent, sourceUrl),
                    possibleCommentOrReplyCount,
                    visibleWordCount,
                    originalMetadata);
        } catch (RuntimeException exception) {
            return failure(
                    sourceUrl,
                    ContentExtractionFailureType.UNEXPECTED_ERROR,
                    "Unexpected content extraction failure",
                    originalMetadata);
        }
    }

    private static boolean isSupportedContentType(
            String contentType) {
        if (contentType == null) {
            return false;
        }
        int parameterIndex = contentType.indexOf(';');
        String mediaType = (parameterIndex >= 0
                ? contentType.substring(0, parameterIndex)
                : contentType)
                .trim()
                .toLowerCase(Locale.ROOT);
        return SUPPORTED_CONTENT_TYPES.contains(mediaType);
    }

    private static List<OriginalMetadataEntry>
            captureOriginalMetadata(Document document) {
        var metadata = new ArrayList<OriginalMetadataEntry>();

        for (Element element : document.getAllElements()) {
            String tagName = element.normalName();
            if (tagName.equals("title")) {
                metadata.add(new OriginalMetadataEntry(
                        "document",
                        "title",
                        element.text()));
            }
            if (tagName.equals("meta")) {
                captureMetaElement(element, metadata);
            }
            if (tagName.equals("link")
                    && hasRelToken(element, "canonical")) {
                metadata.add(new OriginalMetadataEntry(
                        "link:rel",
                        "canonical",
                        element.attr("href")));
            }
            if (tagName.equals("script")
                    && isJsonLdScript(element)) {
                metadata.add(new OriginalMetadataEntry(
                        "script:type",
                        "application/ld+json",
                        element.data()));
            }
            if (tagName.equals("time")
                    && element.hasAttr("datetime")) {
                String metadataName = normalizeInline(
                        element.attr("itemprop"));
                metadata.add(new OriginalMetadataEntry(
                        "time",
                        Objects.requireNonNullElse(
                                metadataName,
                                "datetime"),
                        element.attr("datetime")));
            }
            if (!tagName.equals("meta")
                    && element.hasAttr("itemprop")
                    && !element.attr("itemprop").isBlank()) {
                metadata.add(new OriginalMetadataEntry(
                        tagName + ":itemprop",
                        element.attr("itemprop"),
                        elementMetadataValue(element)));
            }
            if (!tagName.equals("meta")
                    && element.hasAttr("property")
                    && !element.attr("property").isBlank()) {
                metadata.add(new OriginalMetadataEntry(
                        tagName + ":property",
                        element.attr("property"),
                        elementMetadataValue(element)));
            }
            captureAttributeMetadata(
                    element,
                    tagName,
                    "itemtype",
                    metadata);
            captureAttributeMetadata(
                    element,
                    tagName,
                    "typeof",
                    metadata);
            captureAttributeMetadata(
                    element,
                    tagName,
                    "vocab",
                    metadata);
            captureAttributeMetadata(
                    element,
                    tagName,
                    "prefix",
                    metadata);
            if (hasRelToken(element, "author")) {
                String authorValue = firstNonBlank(
                        element.text(),
                        elementMetadataValue(element));
                metadata.add(new OriginalMetadataEntry(
                        tagName + ":rel",
                        "author",
                        Objects.requireNonNullElse(authorValue, "")));
            }
        }

        return List.copyOf(metadata);
    }

    private static void captureAttributeMetadata(
            Element element,
            String tagName,
            String attributeName,
            List<OriginalMetadataEntry> metadata) {
        if (!element.hasAttr(attributeName)
                || element.attr(attributeName).isBlank()) {
            return;
        }
        metadata.add(new OriginalMetadataEntry(
                tagName + ":" + attributeName,
                attributeName,
                element.attr(attributeName)));
    }

    private static void captureMetaElement(
            Element meta,
            List<OriginalMetadataEntry> metadata) {
        for (String attributeName : List.of(
                "name",
                "property",
                "itemprop",
                "http-equiv")) {
            if (!meta.hasAttr(attributeName)
                    || meta.attr(attributeName).isBlank()) {
                continue;
            }
            metadata.add(new OriginalMetadataEntry(
                    "meta:" + attributeName,
                    meta.attr(attributeName),
                    meta.attr("content")));
        }
    }

    private List<ParsedStructuredMetadata>
            extractStructuredMetadata(Document document) {
        var structured =
                new ArrayList<ParsedStructuredMetadata>();

        for (Element script : document.select("script[type]")) {
            if (!isJsonLdScript(script)) {
                continue;
            }
            String rawContent = script.data();
            if (rawContent.isBlank()) {
                continue;
            }

            try {
                JsonNode root = jsonMapper.readTree(rawContent);
                if (root == null || root.isNull()) {
                    continue;
                }
                var mutableProperties =
                        new LinkedHashMap<String, List<String>>();
                var types = new LinkedHashSet<String>();
                flattenStructuredMetadata(
                        root,
                        "$",
                        0,
                        mutableProperties,
                        types);
                structured.add(new ParsedStructuredMetadata(
                        new StructuredMetadata(
                                "JSON_LD",
                                List.copyOf(types),
                                mutableProperties,
                                rawContent),
                        root));
            } catch (JacksonException | IllegalArgumentException exception) {
                // The raw block remains available in original metadata.
            }
        }

        extractMicrodata(document, structured);
        extractRdfa(document, structured);

        return List.copyOf(structured);
    }

    private static void extractMicrodata(
            Document document,
            List<ParsedStructuredMetadata> structured) {
        for (Element scope : document.select("[itemscope]")) {
            if (hasAncestorWithAttribute(scope, "itemscope")) {
                continue;
            }

            var properties =
                    new LinkedHashMap<String, List<String>>();
            var types = new LinkedHashSet<String>();
            for (Element element : scope.getAllElements()) {
                collectAttributeTokens(
                        element.attr("itemtype"),
                        types);
                collectDomStructuredProperties(
                        element,
                        "itemprop",
                        properties);
            }

            addDomStructuredMetadata(
                    structured,
                    "MICRODATA",
                    types,
                    properties,
                    scope);
        }
    }

    private static void extractRdfa(
            Document document,
            List<ParsedStructuredMetadata> structured) {
        for (Element scope : document.select("[typeof]")) {
            if (hasAncestorWithAttribute(scope, "typeof")) {
                continue;
            }

            var properties =
                    new LinkedHashMap<String, List<String>>();
            var types = new LinkedHashSet<String>();
            for (Element element : scope.getAllElements()) {
                collectAttributeTokens(
                        element.attr("typeof"),
                        types);
                collectDomStructuredProperties(
                        element,
                        "property",
                        properties);
            }

            addDomStructuredMetadata(
                    structured,
                    "RDFA",
                    types,
                    properties,
                    scope);
        }
    }

    private static boolean hasAncestorWithAttribute(
            Element element,
            String attributeName) {
        Element ancestor = element.parent();
        while (ancestor != null) {
            if (ancestor.hasAttr(attributeName)) {
                return true;
            }
            ancestor = ancestor.parent();
        }
        return false;
    }

    private static void collectAttributeTokens(
            String attributeValue,
            Set<String> values) {
        if (attributeValue == null || attributeValue.isBlank()) {
            return;
        }
        for (String token : attributeValue.strip().split("\\s+")) {
            String normalized = normalizeInline(token);
            if (normalized != null) {
                values.add(normalized);
            }
        }
    }

    private static void collectDomStructuredProperties(
            Element element,
            String propertyAttribute,
            Map<String, List<String>> properties) {
        if (!element.hasAttr(propertyAttribute)
                || properties.size()
                        >= MAXIMUM_STRUCTURED_PROPERTIES) {
            return;
        }

        String value = normalizeInline(
                elementMetadataValue(element));
        if (value == null) {
            return;
        }

        for (String token : element.attr(propertyAttribute)
                .strip()
                .split("\\s+")) {
            String propertyName = normalizeInline(token);
            if (propertyName == null) {
                continue;
            }
            properties.computeIfAbsent(
                    "$." + propertyName,
                    ignored -> new ArrayList<>())
                    .add(value);
            if (properties.size()
                    >= MAXIMUM_STRUCTURED_PROPERTIES) {
                return;
            }
        }
    }

    private static void addDomStructuredMetadata(
            List<ParsedStructuredMetadata> structured,
            String format,
            Set<String> types,
            Map<String, List<String>> properties,
            Element scope) {
        if (types.isEmpty() && properties.isEmpty()) {
            return;
        }
        String rawContent = scope.outerHtml();
        if (rawContent.isBlank()) {
            return;
        }
        structured.add(new ParsedStructuredMetadata(
                new StructuredMetadata(
                        format,
                        List.copyOf(types),
                        properties,
                        rawContent),
                null));
    }

    private static void flattenStructuredMetadata(
            JsonNode node,
            String path,
            int depth,
            Map<String, List<String>> properties,
            Set<String> types) {
        if (depth > MAXIMUM_JSON_DEPTH
                || properties.size()
                        >= MAXIMUM_STRUCTURED_PROPERTIES
                || node == null
                || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property
                    : node.properties()) {
                if (property.getKey().equalsIgnoreCase("@type")) {
                    collectScalarValues(property.getValue(), types);
                }
                String childPath = path
                        + "."
                        + property.getKey();
                flattenStructuredMetadata(
                        property.getValue(),
                        childPath,
                        depth + 1,
                        properties,
                        types);
                if (properties.size()
                        >= MAXIMUM_STRUCTURED_PROPERTIES) {
                    break;
                }
            }
            return;
        }

        if (node.isArray()) {
            for (int index = 0;
                    index < node.size()
                            && properties.size()
                                    < MAXIMUM_STRUCTURED_PROPERTIES;
                    index++) {
                flattenStructuredMetadata(
                        node.get(index),
                        path + "[" + index + "]",
                        depth + 1,
                        properties,
                        types);
            }
            return;
        }

        String value = normalizeInline(node.asString());
        if (value != null) {
            properties.computeIfAbsent(
                    path,
                    ignored -> new ArrayList<>())
                    .add(value);
        }
    }

    private static void collectScalarValues(
            JsonNode node,
            Set<String> values) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectScalarValues(item, values);
            }
            return;
        }
        if (node.isValueNode()) {
            String value = normalizeInline(node.asString());
            if (value != null) {
                values.add(value);
            }
        }
    }

    private static URI extractCanonicalUrl(
            Document document,
            URI sourceUrl) {
        for (Element link : document.select("link[rel][href]")) {
            if (!hasRelToken(link, "canonical")) {
                continue;
            }
            String rawUrl = link.attr("href").strip();
            if (rawUrl.isBlank()) {
                continue;
            }
            URI canonicalUrl = resolveWebUrl(
                    link,
                    "href",
                    sourceUrl);
            if (canonicalUrl != null) {
                return canonicalUrl;
            }
        }
        return null;
    }

    private static String extractPageTitle(
            Document document,
            List<ParsedStructuredMetadata> structuredMetadata) {
        String documentTitle = normalizeInline(document.title());
        if (documentTitle != null) {
            return documentTitle;
        }

        return firstNonBlank(
                firstMetaValue(document, "og:title"),
                firstMetaValue(document, "twitter:title"),
                findFirstStructuredText(
                        structuredMetadata,
                        "headline"),
                findFirstStructuredText(
                        structuredMetadata,
                        "name"),
                firstElementText(document, "h1"));
    }

    private static String findFirstStructuredText(
            List<ParsedStructuredMetadata> structuredMetadata,
            String fieldName) {
        for (ParsedStructuredMetadata parsed : structuredMetadata) {
            String value = findFirstFieldText(
                    parsed.root(),
                    fieldName,
                    0);
            if (value != null) {
                return value;
            }
            value = findFirstStructuredProperty(
                    parsed.metadata(),
                    fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String findFirstStructuredProperty(
            StructuredMetadata metadata,
            String fieldName) {
        for (Map.Entry<String, List<String>> property
                : metadata.properties().entrySet()) {
            if (!structuredPropertyMatches(
                    property.getKey(),
                    fieldName)) {
                continue;
            }
            for (String value : property.getValue()) {
                String normalized = normalizeInline(value);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private static boolean structuredPropertyMatches(
            String propertyPath,
            String expectedName) {
        String normalizedPath = propertyPath;
        int lastSeparator = Math.max(
                Math.max(
                        normalizedPath.lastIndexOf('.'),
                        normalizedPath.lastIndexOf('/')),
                Math.max(
                        normalizedPath.lastIndexOf('#'),
                        normalizedPath.lastIndexOf(':')));
        if (lastSeparator >= 0
                && lastSeparator + 1 < normalizedPath.length()) {
            normalizedPath = normalizedPath.substring(
                    lastSeparator + 1);
        }
        return normalizedPath.equalsIgnoreCase(expectedName);
    }

    private static String findFirstFieldText(
            JsonNode node,
            String fieldName,
            int depth) {
        if (node == null
                || node.isNull()
                || depth > MAXIMUM_JSON_DEPTH) {
            return null;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property
                    : node.properties()) {
                if (property.getKey().equalsIgnoreCase(fieldName)) {
                    String value = structuredDisplayValue(
                            property.getValue(),
                            depth + 1);
                    if (value != null) {
                        return value;
                    }
                }
            }
            for (Map.Entry<String, JsonNode> property
                    : node.properties()) {
                String nestedValue = findFirstFieldText(
                        property.getValue(),
                        fieldName,
                        depth + 1);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }
            return null;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                String nestedValue = findFirstFieldText(
                        item,
                        fieldName,
                        depth + 1);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }
        }
        return null;
    }

    private static String structuredDisplayValue(
            JsonNode node,
            int depth) {
        if (node == null
                || node.isNull()
                || depth > MAXIMUM_JSON_DEPTH) {
            return null;
        }
        if (node.isValueNode()) {
            return normalizeInline(node.asString());
        }
        if (node.isArray()) {
            var values = new LinkedHashSet<String>();
            for (JsonNode item : node) {
                String value = structuredDisplayValue(
                        item,
                        depth + 1);
                if (value != null) {
                    values.add(value);
                }
            }
            return values.isEmpty()
                    ? null
                    : String.join(", ", values);
        }
        if (node.isObject()) {
            JsonNode name = directProperty(node, "name");
            String nameValue = structuredDisplayValue(
                    name,
                    depth + 1);
            if (nameValue != null) {
                return nameValue;
            }

            String givenName = structuredDisplayValue(
                    directProperty(node, "givenName"),
                    depth + 1);
            String familyName = structuredDisplayValue(
                    directProperty(node, "familyName"),
                    depth + 1);
            return firstNonBlank(
                    joinNonBlank(givenName, familyName),
                    structuredDisplayValue(
                            directProperty(node, "@value"),
                            depth + 1));
        }
        return null;
    }

    private static JsonNode directProperty(
            JsonNode node,
            String propertyName) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (Map.Entry<String, JsonNode> property
                : node.properties()) {
            if (property.getKey().equalsIgnoreCase(propertyName)) {
                return property.getValue();
            }
        }
        return null;
    }

    private static String firstMetaValue(
            Document document,
            String... metadataNames) {
        List<Element> metaElements = document.select("meta");
        for (String metadataName : metadataNames) {
            for (Element meta : metaElements) {
                if (!metadataElementHasName(
                        meta,
                        metadataName)) {
                    continue;
                }
                String value = normalizeInline(meta.attr("content"));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean metadataElementHasName(
            Element meta,
            String expectedName) {
        for (String attributeName : List.of(
                "name",
                "property",
                "itemprop",
                "http-equiv")) {
            if (meta.hasAttr(attributeName)
                    && meta.attr(attributeName)
                            .strip()
                            .equalsIgnoreCase(expectedName)) {
                return true;
            }
        }
        return false;
    }

    private static String firstItempropValue(
            Document document,
            String expectedItemprop) {
        for (Element element : document.select("[itemprop]")) {
            if (!hasAttributeToken(
                    element.attr("itemprop"),
                    expectedItemprop)) {
                continue;
            }
            String value = normalizeInline(
                    elementMetadataValue(element));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstRelValue(
            Document document,
            String expectedRel) {
        for (Element element : document.select("[rel]")) {
            if (!hasRelToken(element, expectedRel)) {
                continue;
            }
            String value = firstNonBlank(
                    element.text(),
                    element.attr("content"),
                    element.attr("title"));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstPublishedTimeValue(
            Document document) {
        for (Element time : document.select("time[datetime]")) {
            if (time.hasAttr("pubdate")
                    || hasAttributeToken(
                            time.attr("itemprop"),
                            "datePublished")
                    || PUBLISHED_IDENTIFIER_PATTERN.matcher(
                            time.id()
                                    + " "
                                    + time.className())
                            .find()) {
                String value = normalizeInline(
                        time.attr("datetime"));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String firstModifiedTimeValue(
            Document document) {
        for (Element time : document.select("time[datetime]")) {
            if (hasAttributeToken(
                    time.attr("itemprop"),
                    "dateModified")
                    || MODIFIED_IDENTIFIER_PATTERN.matcher(
                            time.id()
                                    + " "
                                    + time.className())
                            .find()) {
                String value = normalizeInline(
                        time.attr("datetime"));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String firstElementText(
            Document document,
            String selector) {
        for (Element element : document.select(selector)) {
            String value = normalizeInline(element.text());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String elementMetadataValue(Element element) {
        for (String attributeName : List.of(
                "content",
                "datetime",
                "resource",
                "href",
                "src",
                "data",
                "value")) {
            if (element.hasAttr(attributeName)
                    && !element.attr(attributeName).isBlank()) {
                return element.attr(attributeName);
            }
        }

        for (Element namedChild : element.select("[itemprop=name]")) {
            if (namedChild == element) {
                continue;
            }
            String childValue = elementMetadataValue(namedChild);
            if (!childValue.isBlank()) {
                return childValue;
            }
        }
        return element.text();
    }

    private static void removeBoilerplate(Document document) {
        document.select(ALWAYS_REMOVED_SELECTOR).remove();

        for (Element header
                : new ArrayList<>(document.select("header"))) {
            if (!isWithinContentLandmark(header)) {
                header.remove();
            }
        }

        for (Element element
                : new ArrayList<>(document.getAllElements())) {
            if (element.parent() == null
                    || element.normalName().equals("html")
                    || element.normalName().equals("body")) {
                continue;
            }

            String role = element.attr("role").strip();
            if (role.equalsIgnoreCase("navigation")
                    || role.equalsIgnoreCase("banner")
                    || role.equalsIgnoreCase("contentinfo")
                    || role.equalsIgnoreCase("complementary")
                    || role.equalsIgnoreCase("dialog")
                    || isHidden(element)
                    || hasBoilerplateIdentifier(element)) {
                element.remove();
            }
        }
    }

    private static boolean isWithinContentLandmark(Element element) {
        Element current = element.parent();
        while (current != null) {
            if (current.normalName().equals("article")
                    || current.normalName().equals("main")
                    || current.attr("role")
                            .equalsIgnoreCase("main")) {
                return true;
            }
            current = current.parent();
        }
        return false;
    }

    private static boolean isHidden(Element element) {
        if (element.hasAttr("hidden")
                || element.attr("aria-hidden")
                        .equalsIgnoreCase("true")) {
            return true;
        }
        String style = element.attr("style")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        return style.contains("display:none")
                || style.contains("visibility:hidden");
    }

    private static boolean hasBoilerplateIdentifier(
            Element element) {
        String identifiers = element.id()
                + " "
                + element.className()
                + " "
                + element.attr("aria-label")
                + " "
                + element.attr("role");
        boolean exactAdvertisementIdentifier =
                element.id().equalsIgnoreCase("ad")
                        || element.classNames().stream()
                                .anyMatch(name -> name
                                        .equalsIgnoreCase("ad"));
        return exactAdvertisementIdentifier
                || BOILERPLATE_IDENTIFIER_PATTERN
                .matcher(identifiers)
                .find();
    }

    private static Element selectMainContent(Document document) {
        Element body = document.body();
        if (body == null) {
            return null;
        }

        var candidates = new ArrayList<Element>(
                body.select(CONTENT_CANDIDATE_SELECTOR));
        candidates.add(body);
        Element bestCandidate =
                highestScoringCandidate(candidates);
        return nearestMeaningfulSemanticAncestor(
                bestCandidate);
    }

    private static Element nearestMeaningfulSemanticAncestor(
            Element candidate) {
        Element current = candidate;
        while (current != null) {
            if (current.normalName().equals("article")
                    || current.normalName().equals("main")
                    || current.attr("role")
                            .equalsIgnoreCase("main")) {
                String text = normalizeInline(current.text());
                if (text != null
                        && isMeaningful(text, countWords(text))
                        && hasSufficientNonLinkContent(current)) {
                    return current;
                }
            }
            current = current.parent();
        }
        return candidate;
    }

    private static Element highestScoringCandidate(
            List<Element> candidates) {
        Element bestCandidate = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Element candidate : candidates) {
            String candidateText = normalizeInline(candidate.text());
            if (candidateText == null
                    || countLetterOrDigitCharacters(
                            candidateText) == 0) {
                continue;
            }

            double score = scoreContentCandidate(candidate);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    private static double scoreContentCandidate(Element candidate) {
        String text = candidate.text();
        int textLength = text.length();
        double blockScore = 0.0;

        for (Element block
                : candidate.select(SUBSTANTIVE_BLOCK_SELECTOR)) {
            int blockLength = block.text().length();
            if (blockLength < 25) {
                continue;
            }
            int distance = Math.max(
                    1,
                    distanceToAncestor(block, candidate));
            double contribution = 1.0
                    + Math.min(5.0, blockLength / 80.0)
                    + punctuationScore(block.text());
            blockScore += contribution / distance;
        }

        double score = blockScore * 100.0
                + Math.min(textLength, 5_000) / 50.0;
        score *= Math.max(
                0.05,
                1.0 - linkDensity(candidate));

        if (candidate.normalName().equals("article")) {
            score += 250.0;
        }
        if (candidate.normalName().equals("main")
                || candidate.attr("role")
                        .equalsIgnoreCase("main")) {
            score += 225.0;
        }
        if (CONTENT_IDENTIFIER_PATTERN.matcher(
                candidate.id()
                        + " "
                        + candidate.className())
                .find()) {
            score += 150.0;
        }
        if (candidate.normalName().equals("body")) {
            score *= 0.65;
        }
        if (COMMENT_IDENTIFIER_PATTERN.matcher(
                candidate.id()
                        + " "
                        + candidate.className())
                .find()) {
            score *= 0.5;
        }
        return score;
    }

    private static int distanceToAncestor(
            Element element,
            Element ancestor) {
        int distance = 0;
        Element current = element;
        while (current != null && current != ancestor) {
            distance++;
            current = current.parent();
        }
        return current == ancestor ? distance : Integer.MAX_VALUE;
    }

    private static double punctuationScore(String text) {
        int punctuation = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == ','
                    || character == '.'
                    || character == '!'
                    || character == '?'
                    || character == ';'
                    || character == ':') {
                punctuation++;
            }
        }
        return Math.min(5.0, punctuation * 0.25);
    }

    private static double linkDensity(Element element) {
        int totalLength = element.text().length();
        if (totalLength == 0) {
            return 1.0;
        }

        int linkLength = element.select("a").stream()
                .mapToInt(link -> link.text().length())
                .sum();
        return Math.min(1.0, (double) linkLength / totalLength);
    }

    private static String extractMainText(Element mainContent) {
        Element textRoot = mainContent.clone();
        for (Element boundary : new ArrayList<>(
                textRoot.select(TEXT_BOUNDARY_SELECTOR))) {
            boundary.appendText("\n");
        }
        return normalizeTextLines(textRoot.wholeText());
    }

    private static String normalizeTextLines(String text) {
        var normalized = new StringBuilder();
        for (String line : text
                .replace('\u00a0', ' ')
                .split("\\R+")) {
            String normalizedLine = HORIZONTAL_WHITESPACE_PATTERN
                    .matcher(line)
                    .replaceAll(" ")
                    .strip();
            if (normalizedLine.isBlank()) {
                continue;
            }
            if (normalized.length() > 0) {
                normalized.append('\n');
            }
            normalized.append(normalizedLine);
        }
        return normalized.toString();
    }

    private static List<ExtractedHeading> extractHeadings(
            Element mainContent) {
        var headings = new ArrayList<ExtractedHeading>();
        for (Element heading
                : mainContent.select("h1, h2, h3, h4, h5, h6")) {
            String text = normalizeInline(heading.text());
            if (text == null) {
                continue;
            }
            headings.add(new ExtractedHeading(
                    Integer.parseInt(
                            heading.normalName().substring(1)),
                    text));
        }
        return List.copyOf(headings);
    }

    private static int countOutboundLinks(
            Element mainContent,
            URI sourceUrl) {
        String sourceHost = normalizeHost(sourceUrl.getHost());
        int outboundLinks = 0;

        for (Element link : mainContent.select("a[href]")) {
            String rawUrl = link.attr("href").strip();
            if (rawUrl.isBlank()) {
                continue;
            }
            URI targetUrl = resolveWebUrl(
                    link,
                    "href",
                    sourceUrl);
            if (targetUrl != null
                    && !Objects.equals(
                            sourceHost,
                            normalizeHost(targetUrl.getHost()))) {
                outboundLinks++;
            }
        }
        return outboundLinks;
    }

    private static URI resolveWebUrl(
            Element element,
            String attributeName,
            URI sourceUrl) {
        String absoluteUrl = element.absUrl(attributeName);
        if (!absoluteUrl.isBlank()) {
            try {
                URI resolvedUrl = new URI(absoluteUrl);
                if (isAbsoluteHttpUrl(resolvedUrl)) {
                    return resolvedUrl;
                }
            } catch (URISyntaxException exception) {
                // Fall back to resolving the raw declaration.
            }
        }

        String rawUrl = element.attr(attributeName).strip();
        try {
            URI resolvedUrl = sourceUrl.resolve(new URI(rawUrl));
            return isAbsoluteHttpUrl(resolvedUrl)
                    ? resolvedUrl
                    : null;
        } catch (URISyntaxException
                | IllegalArgumentException exception) {
            return null;
        }
    }

    private static int discoverCommentOrReplyCount(
            List<ParsedStructuredMetadata> structuredMetadata,
            Document cleanedDocument) {
        OptionalInt structuredCount =
                findStructuredCommentCount(structuredMetadata);
        if (structuredCount.isPresent()) {
            return structuredCount.getAsInt();
        }

        OptionalInt declaredDomCount =
                findDeclaredDomCommentCount(cleanedDocument);
        if (declaredDomCount.isPresent()) {
            return declaredDomCount.getAsInt();
        }

        Set<Element> commentElements = Collections.newSetFromMap(
                new IdentityHashMap<>());
        for (Element element
                : cleanedDocument.select("[itemprop], [role], [id], [class]")) {
            if (isCommentOrReplyInstance(element)) {
                commentElements.add(element);
            }
        }
        return commentElements.size();
    }

    private static OptionalInt findStructuredCommentCount(
            List<ParsedStructuredMetadata> structuredMetadata) {
        for (ParsedStructuredMetadata parsed : structuredMetadata) {
            OptionalInt count = findNonNegativeIntegerField(
                    parsed.root(),
                    "commentCount",
                    0);
            if (count.isPresent()) {
                return count;
            }

            OptionalInt interactionCount =
                    findCommentInteractionCount(
                            parsed.root(),
                            0);
            if (interactionCount.isPresent()) {
                return interactionCount;
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt findNonNegativeIntegerField(
            JsonNode node,
            String fieldName,
            int depth) {
        if (node == null
                || node.isNull()
                || depth > MAXIMUM_JSON_DEPTH) {
            return OptionalInt.empty();
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property
                    : node.properties()) {
                if (property.getKey().equalsIgnoreCase(fieldName)) {
                    OptionalInt value = parseNonNegativeInteger(
                            property.getValue().asString());
                    if (value.isPresent()) {
                        return value;
                    }
                }
            }
            for (Map.Entry<String, JsonNode> property
                    : node.properties()) {
                OptionalInt nested = findNonNegativeIntegerField(
                        property.getValue(),
                        fieldName,
                        depth + 1);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                OptionalInt nested = findNonNegativeIntegerField(
                        item,
                        fieldName,
                        depth + 1);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt findCommentInteractionCount(
            JsonNode node,
            int depth) {
        if (node == null
                || node.isNull()
                || depth > MAXIMUM_JSON_DEPTH) {
            return OptionalInt.empty();
        }
        if (node.isObject()) {
            String interactionType = structuredDisplayValue(
                    directProperty(node, "interactionType"),
                    depth + 1);
            if (interactionType != null
                    && interactionType.toLowerCase(Locale.ROOT)
                            .contains("commentaction")) {
                OptionalInt count = parseNonNegativeInteger(
                        structuredDisplayValue(
                                directProperty(
                                        node,
                                        "userInteractionCount"),
                                depth + 1));
                if (count.isPresent()) {
                    return count;
                }
            }
            for (Map.Entry<String, JsonNode> property
                    : node.properties()) {
                OptionalInt nested = findCommentInteractionCount(
                        property.getValue(),
                        depth + 1);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                OptionalInt nested = findCommentInteractionCount(
                        item,
                        depth + 1);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt findDeclaredDomCommentCount(
            Document document) {
        for (Element element : document.select(
                "[data-comment-count], "
                        + "[class*=comment-count], "
                        + "[id*=comment-count]")) {
            String rawValue = element.hasAttr("data-comment-count")
                    ? element.attr("data-comment-count")
                    : element.text();
            OptionalInt count = parseNonNegativeInteger(rawValue);
            if (count.isPresent()) {
                return count;
            }
        }
        return OptionalInt.empty();
    }

    private static boolean isCommentOrReplyInstance(
            Element element) {
        if (hasAttributeToken(
                element.attr("itemprop"),
                "comment")
                || element.attr("role")
                        .equalsIgnoreCase("comment")) {
            return true;
        }

        String identifiers = element.id()
                + " "
                + element.className();
        return COMMENT_IDENTIFIER_PATTERN
                .matcher(identifiers)
                .find()
                && !COMMENT_NON_INSTANCE_PATTERN
                        .matcher(identifiers)
                        .find();
    }

    private static OptionalInt parseNonNegativeInteger(
            String value) {
        if (value == null) {
            return OptionalInt.empty();
        }
        if (NEGATIVE_INTEGER_PATTERN.matcher(value).find()) {
            return OptionalInt.empty();
        }
        var matcher = INTEGER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return OptionalInt.empty();
        }
        try {
            int parsed = Integer.parseInt(
                    matcher.group(1).replace(",", ""));
            return parsed >= 0
                    ? OptionalInt.of(parsed)
                    : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private static boolean isMeaningful(
            String mainText,
            int wordCount) {
        return countLetterOrDigitCharacters(mainText)
                >= MINIMUM_MEANINGFUL_CHARACTERS
                || wordCount >= MINIMUM_MEANINGFUL_WORDS;
    }

    private static boolean hasSufficientNonLinkContent(
            Element mainContent) {
        if (linkDensity(mainContent) < 0.65) {
            return true;
        }

        Element withoutLinks = mainContent.clone();
        withoutLinks.select("a").remove();
        String nonLinkText = normalizeInline(withoutLinks.text());
        return nonLinkText != null
                && (countWords(nonLinkText) >= 6
                        || countLetterOrDigitCharacters(nonLinkText)
                                >= 60);
    }

    private static int countWords(String text) {
        int wordCount = 0;
        var matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            wordCount++;
        }
        return wordCount;
    }

    private static int countLetterOrDigitCharacters(String text) {
        return (int) text.codePoints()
                .filter(character -> Character.isLetterOrDigit(
                        character))
                .count();
    }

    private static String normalizeInline(String value) {
        if (value == null) {
            return null;
        }
        String normalized = HORIZONTAL_WHITESPACE_PATTERN
                .matcher(value.replace('\u00a0', ' '))
                .replaceAll(" ")
                .strip();
        return normalized.isBlank() ? null : normalized;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeInline(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String joinNonBlank(
            String first,
            String second) {
        String normalizedFirst = normalizeInline(first);
        String normalizedSecond = normalizeInline(second);
        if (normalizedFirst == null) {
            return normalizedSecond;
        }
        if (normalizedSecond == null) {
            return normalizedFirst;
        }
        return normalizedFirst + " " + normalizedSecond;
    }

    private static boolean hasRelToken(
            Element element,
            String expectedToken) {
        return hasAttributeToken(
                element.attr("rel"),
                expectedToken);
    }

    private static boolean hasAttributeToken(
            String value,
            String expectedToken) {
        for (String token : value.strip().split("\\s+")) {
            if (token.equalsIgnoreCase(expectedToken)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJsonLdScript(Element script) {
        String type = script.attr("type");
        int parameterIndex = type.indexOf(';');
        String mediaType = (parameterIndex >= 0
                ? type.substring(0, parameterIndex)
                : type)
                .strip();
        return mediaType.equalsIgnoreCase("application/ld+json");
    }

    private static boolean isAbsoluteHttpUrl(URI url) {
        String scheme = url.getScheme();
        return url.isAbsolute()
                && !url.isOpaque()
                && url.getHost() != null
                && scheme != null
                && (scheme.equalsIgnoreCase("http")
                        || scheme.equalsIgnoreCase("https"));
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        return host.toLowerCase(Locale.ROOT)
                .replaceFirst("\\.$", "");
    }

    private static ContentExtractionFailure noMeaningfulContent(
            URI sourceUrl,
            List<OriginalMetadataEntry> originalMetadata) {
        return failure(
                sourceUrl,
                ContentExtractionFailureType.NO_MEANINGFUL_CONTENT,
                "No meaningful page content was found",
                originalMetadata);
    }

    private static ContentExtractionFailure failure(
            URI sourceUrl,
            ContentExtractionFailureType failureType,
            String message,
            List<OriginalMetadataEntry> originalMetadata) {
        return new ContentExtractionFailure(
                sourceUrl,
                failureType,
                message,
                originalMetadata);
    }

    private record ParsedStructuredMetadata(
            StructuredMetadata metadata,
            JsonNode root) {
    }
}
