package com.branchlight.backend.search.aggregation;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class UrlCanonicalizer {

    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "gclid",
            "dclid",
            "fbclid",
            "msclkid",
            "gbraid",
            "wbraid",
            "yclid",
            "ttclid",
            "twclid",
            "mc_cid",
            "mc_eid",
            "igshid",
            "li_fat_id",
            "srsltid",
            "gad_source",
            "gad_campaignid",
            "_ga",
            "_gl");

    URI canonicalize(URI url) {
        Objects.requireNonNull(url, "url must not be null");

        if (!url.isAbsolute() || url.isOpaque()) {
            throw new IllegalArgumentException(
                    "url must be an absolute hierarchical HTTP(S) URI");
        }

        String scheme = url.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(
                    "url must use HTTP or HTTPS");
        }

        String host = url.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "url must contain a host");
        }

        StringBuilder canonicalUrl = new StringBuilder()
                .append(scheme)
                .append("://");

        String userInfo = url.getRawUserInfo();
        if (userInfo != null) {
            canonicalUrl.append(userInfo).append('@');
        }

        canonicalUrl.append(host.toLowerCase(Locale.ROOT));

        int port = url.getPort();
        if (port != -1 && !isDefaultPort(scheme, port)) {
            canonicalUrl.append(':').append(port);
        }

        canonicalUrl.append(normalizePath(url.getRawPath()));

        String query = removeTrackingParameters(url.getRawQuery());
        if (query != null) {
            canonicalUrl.append('?').append(query);
        }

        return URI.create(canonicalUrl.toString());
    }

    private boolean isDefaultPort(String scheme, int port) {
        return scheme.equals("http") && port == 80
                || scheme.equals("https") && port == 443;
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }

        if (!rawPath.startsWith("/")) {
            throw new IllegalArgumentException(
                    "HTTP(S) URI path must be empty or absolute");
        }

        String[] inputSegments = rawPath.split("/", -1);
        List<String> outputSegments = new ArrayList<>(
                inputSegments.length - 1);

        for (int index = 1; index < inputSegments.length; index++) {
            String segment = inputSegments[index];
            boolean finalSegment = index == inputSegments.length - 1;

            if (segment.equals(".")) {
                if (finalSegment) {
                    outputSegments.add("");
                }
                continue;
            }

            if (segment.equals("..")) {
                if (!outputSegments.isEmpty()) {
                    outputSegments.remove(outputSegments.size() - 1);
                }
                if (finalSegment) {
                    outputSegments.add("");
                }
                continue;
            }

            outputSegments.add(segment);
        }

        return "/" + String.join("/", outputSegments);
    }

    private String removeTrackingParameters(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }

        String[] parameters = rawQuery.split("&", -1);
        List<String> retainedParameters = new ArrayList<>(
                parameters.length);

        for (String parameter : parameters) {
            String rawName = parameter;
            int equalsIndex = parameter.indexOf('=');
            if (equalsIndex >= 0) {
                rawName = parameter.substring(0, equalsIndex);
            }

            if (!isTrackingParameter(rawName)) {
                retainedParameters.add(parameter);
            }
        }

        if (retainedParameters.isEmpty()) {
            return null;
        }

        return String.join("&", retainedParameters);
    }

    private boolean isTrackingParameter(String rawName) {
        String comparableName = decodeUnreserved(rawName)
                .toLowerCase(Locale.ROOT);
        return comparableName.startsWith("utm_")
                || TRACKING_PARAMETERS.contains(comparableName);
    }

    private String decodeUnreserved(String rawValue) {
        StringBuilder decoded = new StringBuilder(rawValue.length());

        for (int index = 0; index < rawValue.length(); index++) {
            char current = rawValue.charAt(index);
            if (current == '%' && index + 2 < rawValue.length()) {
                int high = Character.digit(
                        rawValue.charAt(index + 1),
                        16);
                int low = Character.digit(
                        rawValue.charAt(index + 2),
                        16);
                if (high >= 0 && low >= 0) {
                    char value = (char) (high * 16 + low);
                    if (isUnreserved(value)) {
                        decoded.append(value);
                        index += 2;
                        continue;
                    }
                }
            }

            decoded.append(current);
        }

        return decoded.toString();
    }

    private boolean isUnreserved(char value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '.'
                || value == '_'
                || value == '~';
    }
}
