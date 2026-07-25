package com.branchlight.backend.search.aggregation;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlCanonicalizerTest {

    private final UrlCanonicalizer canonicalizer =
            new UrlCanonicalizer();

    @Test
    void canonicalizesOriginAndPathWithoutLosingPathSemantics() {
        URI canonical = canonicalizer.canonicalize(URI.create(
                "HTTPS://Example.COM:443/a//b/../Case/#section"));

        assertEquals(
                "https://example.com/a//Case/",
                canonical.toString());
    }

    @Test
    void suppliesRootPathAndRetainsNonDefaultPorts() {
        assertEquals(
                "http://example.com/",
                canonicalizer.canonicalize(
                        URI.create("HTTP://EXAMPLE.COM:80"))
                        .toString());
        assertEquals(
                "https://example.com:8443/",
                canonicalizer.canonicalize(
                        URI.create("https://EXAMPLE.COM:8443"))
                        .toString());
    }

    @Test
    void removesTrackingParametersCaseInsensitively() {
        URI canonical = canonicalizer.canonicalize(URI.create(
                "https://example.com/results"
                        + "?UTM_Source=newsletter"
                        + "&q=one"
                        + "&FbClId=click"
                        + "&q=two"
                        + "&ref=home"
                        + "&SOURCE=manual"));

        assertEquals(
                "https://example.com/results"
                        + "?q=one&q=two&ref=home&SOURCE=manual",
                canonical.toString());
    }

    @Test
    void recognizesEverySupportedTrackingParameter() {
        URI canonical = canonicalizer.canonicalize(URI.create(
                "https://example.com/"
                        + "?%75tm%5Fcampaign=x"
                        + "&gclid=x"
                        + "&DCLID=x"
                        + "&fbclid=x"
                        + "&msclkid=x"
                        + "&gbraid=x"
                        + "&wbraid=x"
                        + "&yclid=x"
                        + "&ttclid=x"
                        + "&twclid=x"
                        + "&mc_cid=x"
                        + "&mc_eid=x"
                        + "&igshid=x"
                        + "&li_fat_id=x"
                        + "&srsltid=x"
                        + "&gad_source=x"
                        + "&gad_campaignid=x"
                        + "&%5Fga=x"
                        + "&%5Fgl=x"
                        + "&keep=%2Fvalue%2Bplus"));

        assertEquals(
                "https://example.com/?keep=%2Fvalue%2Bplus",
                canonical.toString());
    }

    @Test
    void preservesRawEncodingQueryOrderAndDuplicateParameters() {
        String original = "https://example.com/search"
                + "?q=hello%20world"
                + "&redirect=https%3A%2F%2Fexample.org%2Fa%3Fb%3D1"
                + "&q=second"
                + "&source=x+y";

        URI canonical = canonicalizer.canonicalize(
                URI.create(original));

        assertEquals(original, canonical.toString());
    }

    @Test
    void omitsAQueryMadeOnlyOfTrackingParameters() {
        URI canonical = canonicalizer.canonicalize(URI.create(
                "https://example.com/path?utm_medium=email&gclid=x"));

        assertEquals(
                "https://example.com/path",
                canonical.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/relative",
            "mailto:reader@example.com",
            "ftp://example.com/file",
            "https:/path-without-host"
    })
    void rejectsUnsupportedUris(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> canonicalizer.canonicalize(URI.create(value)));
    }
}
