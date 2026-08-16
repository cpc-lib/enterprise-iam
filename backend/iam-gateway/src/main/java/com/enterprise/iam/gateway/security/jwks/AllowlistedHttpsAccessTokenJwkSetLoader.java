package com.enterprise.iam.gateway.security.jwks;

import com.enterprise.iam.common.security.access.AccessTokenJwkSetLoader;
import com.enterprise.iam.common.security.jwt.BoundedRefreshingJwkSetPublicKeyResolver;

import java.io.IOException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fetches one immutable, exact-host HTTPS JWKS endpoint. DNS preflight blocks
 * non-global answers; deployment egress policy remains required because the JDK
 * client can resolve the hostname again when it opens the TLS connection.
 */
public final class AllowlistedHttpsAccessTokenJwkSetLoader
        implements AccessTokenJwkSetLoader {

    private static final int MAX_BYTES =
            BoundedRefreshingJwkSetPublicKeyResolver.MAX_JWKS_BYTES;
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(?:\\.(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?))*$");
    private static final Pattern ENCODED_PATH_SEPARATOR_OR_DOT = Pattern.compile(
            "(?i)%2e|%2f|%5c");
    private static final Set<String> RESERVED_HOST_SUFFIXES = Set.of(
            "localhost", "local", "internal", "invalid", "test", "example");

    private final URI jwksUri;
    private final String hostname;
    private final JwksDnsResolver dnsResolver;
    private final JwksHttpTransport transport;
    private final Duration requestTimeout;

    public AllowlistedHttpsAccessTokenJwkSetLoader(
            URI jwksUri,
            Set<String> allowedHosts,
            JwksDnsResolver dnsResolver,
            JwksHttpTransport transport,
            Duration requestTimeout) {
        this.jwksUri = requireSafeUri(jwksUri);
        this.hostname = normalizeHostname(this.jwksUri.getHost());
        Set<String> normalizedAllowlist = normalizeAllowlist(allowedHosts);
        if (!normalizedAllowlist.contains(hostname)) {
            throw new IllegalArgumentException("JWKS URI host is not exactly allowlisted");
        }
        this.dnsResolver = Objects.requireNonNull(dnsResolver, "dnsResolver must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.requestTimeout = requireDuration(requestTimeout, "requestTimeout");
    }

    @Override
    public String load() {
        requireOnlyGlobalAddresses(dnsResolver.resolve(hostname));
        try (JwksHttpResponse response = transport.get(jwksUri, requestTimeout)) {
            requireResponseMetadata(response);
            byte[] bytes = readBounded(response);
            return decodeStrictUtf8(bytes);
        }
    }

    private static URI requireSafeUri(URI uri) {
        Objects.requireNonNull(uri, "jwksUri must not be null");
        if (!uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("JWKS URI must be an exact HTTPS endpoint");
        }
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isBlank() || !rawPath.startsWith("/")
                || rawPath.contains("\\") || rawPath.contains("//")
                || ENCODED_PATH_SEPARATOR_OR_DOT.matcher(rawPath).find()
                || !uri.normalize().equals(uri)) {
            throw new IllegalArgumentException("JWKS URI path is unsafe");
        }
        normalizeHostname(uri.getHost());
        return uri;
    }

    private static Set<String> normalizeAllowlist(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("JWKS host allowlist must not be empty");
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            normalized.add(normalizeHostname(value));
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeHostname(String value) {
        if (value == null || value.isBlank() || value.endsWith(".")) {
            throw new IllegalArgumentException("JWKS hostname is invalid");
        }
        final String host;
        try {
            host = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWKS hostname is invalid", exception);
        }
        if (!HOSTNAME.matcher(host).matches()
                || host.chars().allMatch(character ->
                        (character >= '0' && character <= '9') || character == '.')) {
            throw new IllegalArgumentException("JWKS hostname must be a DNS name");
        }
        for (String suffix : RESERVED_HOST_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                throw new IllegalArgumentException("JWKS hostname uses a reserved suffix");
            }
        }
        return host;
    }

    private static void requireOnlyGlobalAddresses(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            throw new JwksTransportException("JWKS hostname resolved to no address");
        }
        for (InetAddress address : addresses) {
            if (address == null || !isGlobalUnicast(address)) {
                throw new JwksTransportException(
                        "JWKS hostname resolved to a non-global address");
            }
        }
    }

    static boolean isGlobalUnicast(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isGlobalIpv4(bytes);
        }
        if (bytes.length != 16) {
            return false;
        }
        if (isIpv4Mapped(bytes)) {
            return isGlobalIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
        }
        int first = unsigned(bytes[0]);
        int second = unsigned(bytes[1]);
        if ((first & 0xfe) == 0xfc
                || (first == 0xfe && (second & 0xc0) == 0x80)
                || first == 0xff) {
            return false;
        }
        return !hasPrefix(bytes, new int[]{0x00, 0x64, 0xff, 0x9b}, 96)
                && !hasPrefix(bytes, new int[]{0x00, 0x64, 0xff, 0x9b, 0x00, 0x01}, 48)
                && !hasPrefix(bytes, new int[]{0x01, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00}, 64)
                && !hasPrefix(bytes, new int[]{0x20, 0x01, 0x00, 0x00}, 32)
                && !hasPrefix(bytes, new int[]{0x20, 0x02}, 16)
                && !hasPrefix(bytes, new int[]{0x20, 0x01, 0x0d, 0xb8}, 32)
                && !hasPrefix(bytes, new int[]{0x20, 0x01, 0x00, 0x02}, 48)
                && !hasPrefix(bytes, new int[]{0x20, 0x01, 0x00, 0x10}, 28);
    }

    private static boolean isGlobalIpv4(byte[] bytes) {
        int a = unsigned(bytes[0]);
        int b = unsigned(bytes[1]);
        int c = unsigned(bytes[2]);
        return a != 0
                && a != 10
                && a != 127
                && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 169 && b == 254)
                && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && b == 0)
                && !(a == 192 && b == 88 && c == 99)
                && !(a == 192 && b == 168)
                && !(a == 198 && (b == 18 || b == 19))
                && !(a == 198 && b == 51 && c == 100)
                && !(a == 203 && b == 0 && c == 113)
                && a < 224;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return unsigned(bytes[10]) == 0xff && unsigned(bytes[11]) == 0xff;
    }

    private static boolean hasPrefix(byte[] address, int[] prefix, int bits) {
        int completeBytes = bits / 8;
        int remainingBits = bits % 8;
        for (int index = 0; index < completeBytes; index++) {
            int expected = index < prefix.length ? prefix[index] : 0;
            if (unsigned(address[index]) != expected) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - remainingBits);
        int expected = completeBytes < prefix.length ? prefix[completeBytes] : 0;
        return (unsigned(address[completeBytes]) & mask)
                == (expected & mask);
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static void requireResponseMetadata(JwksHttpResponse response) {
        if (response.statusCode() != 200) {
            throw new JwksTransportException("JWKS response status is not 200");
        }
        List<String> contentTypes = response.headerValues("Content-Type");
        if (contentTypes.size() != 1) {
            throw new JwksTransportException("JWKS content type is missing or ambiguous");
        }
        String contentType = contentTypes.get(0).split(";", 2)[0]
                .trim().toLowerCase(Locale.ROOT);
        if (!Set.of("application/json", "application/jwk-set+json")
                .contains(contentType)) {
            throw new JwksTransportException("JWKS content type is not JSON");
        }
        List<String> encodings = response.headerValues("Content-Encoding");
        if (encodings.size() > 1
                || (encodings.size() == 1
                && !"identity".equalsIgnoreCase(encodings.get(0).trim()))) {
            throw new JwksTransportException("JWKS content encoding is not identity");
        }
        List<String> lengths = response.headerValues("Content-Length");
        if (lengths.size() > 1) {
            throw new JwksTransportException("JWKS content length is ambiguous");
        }
        if (lengths.size() == 1) {
            try {
                long length = Long.parseLong(lengths.get(0));
                if (length < 0 || length > MAX_BYTES) {
                    throw new JwksTransportException("JWKS content length is invalid");
                }
            } catch (NumberFormatException exception) {
                throw new JwksTransportException("JWKS content length is invalid", exception);
            }
        }
    }

    private static byte[] readBounded(JwksHttpResponse response) {
        try {
            byte[] bytes = response.body().readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new JwksTransportException("JWKS response exceeds the byte limit");
            }
            return bytes;
        } catch (IOException exception) {
            throw new JwksTransportException("JWKS response read failed", exception);
        }
    }

    private static String decodeStrictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new JwksTransportException("JWKS response is not valid UTF-8", exception);
        }
    }

    private static Duration requireDuration(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
