package com.enterprise.iam.security.autoconfigure;

import com.enterprise.iam.common.security.delegation.DelegationJwkSetLoader;
import com.enterprise.iam.common.security.jwt.BoundedRefreshingJwkSetPublicKeyResolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP JWKS transport for services verifying gateway delegation tokens.
 * Exact-host allowlist, no redirects, bounded response body. With
 * {@code allowHttp=false} only HTTPS is accepted (production); with
 * {@code allowHttp=true} plain HTTP is permitted (DEV-ONLY, docker compose).
 */
public final class HttpDelegationJwkSetLoader implements DelegationJwkSetLoader {

    private static final int MAX_BYTES =
            BoundedRefreshingJwkSetPublicKeyResolver.MAX_JWKS_BYTES;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final URI jwksUri;
    private final Set<String> allowedHosts;
    private final HttpClient httpClient;

    public HttpDelegationJwkSetLoader(String jwksUri, Set<String> allowedHosts, boolean allowHttp) {
        Objects.requireNonNull(jwksUri, "jwksUri must not be null");
        this.jwksUri = URI.create(jwksUri.trim());
        requireSupportedScheme(this.jwksUri, allowHttp);
        this.allowedHosts = normalizeAllowlist(allowedHosts);
        if (!this.allowedHosts.contains(
                this.jwksUri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "delegation jwks-uri host is not exactly allowlisted");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String load() {
        HttpRequest request = HttpRequest.newBuilder(jwksUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/jwk-set+json, application/json")
                .GET()
                .build();
        final HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("delegation JWKS request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("delegation JWKS request failed", exception);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "delegation JWKS response status is not 200: " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IllegalStateException("delegation JWKS response exceeds the byte limit");
            }
            return decodeStrictUtf8(bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("delegation JWKS response read failed", exception);
        }
    }

    private static void requireSupportedScheme(URI uri, boolean allowHttp) {
        String scheme = uri.getScheme();
        boolean http = "http".equalsIgnoreCase(scheme);
        boolean https = "https".equalsIgnoreCase(scheme);
        if (!http && !https) {
            throw new IllegalStateException(
                    "iam.security.delegation.jwks-uri must use http or https");
        }
        if (http && !allowHttp) {
            throw new IllegalStateException(
                    "iam.security.delegation.jwks-uri uses plain http but "
                            + "iam.security.delegation.jwks-allow-http is false (plain http is DEV-ONLY)");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("iam.security.delegation.jwks-uri is not a usable endpoint");
        }
    }

    private static Set<String> normalizeAllowlist(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(
                    "iam.security.delegation.jwks-allowed-hosts must not be empty");
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "iam.security.delegation.jwks-allowed-hosts contains an invalid host");
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static String decodeStrictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException("delegation JWKS response is not valid UTF-8", exception);
        }
    }
}
