package com.branchlight.backend.search.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

import org.apache.hc.client5.http.DnsResolver;

final class PublicNetworkDestinationValidator
        implements DnsResolver {

    private final HostResolver hostResolver;

    PublicNetworkDestinationValidator() {
        this(InetAddress::getAllByName);
    }

    PublicNetworkDestinationValidator(HostResolver hostResolver) {
        this.hostResolver = Objects.requireNonNull(
                hostResolver,
                "hostResolver must not be null");
    }

    @Override
    public InetAddress[] resolve(String host)
            throws UnknownHostException {
        String normalizedHost = normalizeHost(host);
        if (isLocalhostName(normalizedHost)) {
            throw blocked();
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(normalizedHost);
        } catch (UnknownHostException exception) {
            throw sanitizedUnknownHost(exception);
        }

        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException(
                    "Destination host did not resolve to an address");
        }

        for (InetAddress address : addresses) {
            if (address == null || isBlockedAddress(address)) {
                throw blocked();
            }
        }

        return addresses.clone();
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }

    private static String normalizeHost(String host)
            throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new UnknownHostException(
                    "Destination host must not be blank");
        }

        String normalized = host
                .toLowerCase(Locale.ROOT)
                .replaceFirst("\\.$", "");
        if (normalized.startsWith("[")
                && normalized.endsWith("]")) {
            return normalized.substring(
                    1,
                    normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isLocalhostName(String host) {
        return host.equals("localhost")
                || host.endsWith(".localhost");
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4(bytes);
        }
        if (bytes.length == 16) {
            return isUniqueLocalIpv6(bytes)
                    || isEmbeddedBlockedIpv4(bytes);
        }
        return true;
    }

    private static boolean isBlockedIpv4(byte[] address) {
        int first = Byte.toUnsignedInt(address[0]);
        int second = Byte.toUnsignedInt(address[1]);

        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || first >= 224;
    }

    private static boolean isUniqueLocalIpv6(byte[] address) {
        return (Byte.toUnsignedInt(address[0]) & 0xfe) == 0xfc;
    }

    private static boolean isEmbeddedBlockedIpv4(byte[] address) {
        boolean compatible = true;
        for (int index = 0; index < 12; index++) {
            if (address[index] != 0) {
                compatible = false;
                break;
            }
        }

        boolean mapped = true;
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) {
                mapped = false;
                break;
            }
        }
        mapped = mapped
                && address[10] == (byte) 0xff
                && address[11] == (byte) 0xff;

        if (!compatible && !mapped) {
            return false;
        }
        return isBlockedIpv4(new byte[]{
                address[12],
                address[13],
                address[14],
                address[15]
        });
    }

    private static BlockedDestinationException blocked() {
        return new BlockedDestinationException();
    }

    private static UnknownHostException sanitizedUnknownHost(
            UnknownHostException cause) {
        var exception = new UnknownHostException(
                "Destination host could not be resolved");
        exception.initCause(cause);
        return exception;
    }
}
