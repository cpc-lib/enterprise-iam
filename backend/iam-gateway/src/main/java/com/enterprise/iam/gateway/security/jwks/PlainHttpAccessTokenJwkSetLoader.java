package com.enterprise.iam.gateway.security.jwks;

import com.enterprise.iam.common.security.access.AccessTokenJwkSetLoader;
import com.enterprise.iam.common.security.jwt.BoundedRefreshingJwkSetPublicKeyResolver;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DEV-ONLY counterpart to {@link AllowlistedHttpsAccessTokenJwkSetLoader}:
 * fetches the JWKS over plain HTTP from an exact allowlisted host (typically a
 * docker compose service name such as {@code iam-auth-service}). No DNS
 * preflight and no TLS — production deployments must use the HTTPS loader
 * behind TLS termination instead.
 */
public final class PlainHttpAccessTokenJwkSetLoader
        implements AccessTokenJwkSetLoader {

    private static final int MAX_BYTES =
            BoundedRefreshingJwkSetPublicKeyResolver.MAX_JWKS_BYTES;
    private static final Pattern ENCODED_PATH_SEPARATOR_OR_DOT = Pattern.compile(
            "(?i)%2e|%2f|%5c");

    private final URI jwksUri;
    private final String hostname;
    private final JwksHttpTransport transport;
    private final Duration requestTimeout;

    public PlainHttpAccessTokenJwkSetLoader(
            URI jwksUri,
            Set<String> allowedHosts,
            JwksHttpTransport transport,
            Duration requestTimeout) {
        this.jwksUri = requireHttpUri(jwksUri);
        this.hostname = this.jwksUri.getHost().toLowerCase(Locale.ROOT);
        Set<String> normalizedAllowlist = normalizeAllowlist(allowedHosts);
        if (!normalizedAllowlist.contains(hostname)) {
            throw new IllegalArgumentException("JWKS URI host is not exactly allowlisted");
        }
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.requestTimeout = requireDuration(requestTimeout, "requestTimeout");
    }

    @Override
    public String load() {
        try (JwksHttpResponse response = transport.get(jwksUri, requestTimeout)) {
            AllowlistedHttpsAccessTokenJwkSetLoader.requireResponseMetadata(response);
            byte[] bytes = AllowlistedHttpsAccessTokenJwkSetLoader.readBounded(response);
            return AllowlistedHttpsAccessTokenJwkSetLoader.decodeStrictUtf8(bytes);
        }
    }

    private static URI requireHttpUri(URI uri) {
        Objects.requireNonNull(uri, "jwksUri must not be null");
        if (!uri.isAbsolute()
                || !"http".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("JWKS URI must be an exact HTTP endpoint");
        }
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isBlank() || !rawPath.startsWith("/")
                || rawPath.contains("\\") || rawPath.contains("//")
                || ENCODED_PATH_SEPARATOR_OR_DOT.matcher(rawPath).find()
                || !uri.normalize().equals(uri)) {
            throw new IllegalArgumentException("JWKS URI path is unsafe");
        }
        return uri;
    }

    private static Set<String> normalizeAllowlist(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("JWKS host allowlist must not be empty");
        }
        HashSet<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("JWKS hostname is invalid");
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static Duration requireDuration(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
