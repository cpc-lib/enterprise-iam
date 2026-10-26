package com.enterprise.iam.gateway.security.jwks;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainHttpAccessTokenJwkSetLoaderTest {

    private static final URI JWKS_URI =
            URI.create("http://iam-auth-service:8081/internal/v1/crypto/jwks.json");
    private static final String JWKS_BODY = "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\"}]}";

    @Test
    void fetchesJwksThroughInjectedTransport() {
        PlainHttpAccessTokenJwkSetLoader loader = new PlainHttpAccessTokenJwkSetLoader(
                JWKS_URI,
                Set.of("iam-auth-service"),
                stubTransport(JWKS_BODY, 200),
                Duration.ofSeconds(3));

        assertThat(loader.load()).isEqualTo(JWKS_BODY);
    }

    @Test
    void rejectsNonHttpSchemeAndMissingAllowlistEntry() {
        assertThatThrownBy(() -> new PlainHttpAccessTokenJwkSetLoader(
                URI.create("https://iam-auth-service:8081/internal/v1/crypto/jwks.json"),
                Set.of("iam-auth-service"),
                stubTransport(JWKS_BODY, 200),
                Duration.ofSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact HTTP endpoint");
        assertThatThrownBy(() -> new PlainHttpAccessTokenJwkSetLoader(
                URI.create("http://other-host:8081/internal/v1/crypto/jwks.json"),
                Set.of("iam-auth-service"),
                stubTransport(JWKS_BODY, 200),
                Duration.ofSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlisted");
        assertThatThrownBy(() -> new PlainHttpAccessTokenJwkSetLoader(
                JWKS_URI, Set.of(), stubTransport(JWKS_BODY, 200), Duration.ofSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlist must not be empty");
        assertThatThrownBy(() -> new PlainHttpAccessTokenJwkSetLoader(
                URI.create("http://iam-auth-service:8081/internal/v1/crypto/jwks.json?x=1"),
                Set.of("iam-auth-service"),
                stubTransport(JWKS_BODY, 200),
                Duration.ofSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlainHttpAccessTokenJwkSetLoader(
                URI.create("http://iam-auth-service:8081/internal/v1//crypto/jwks.json"),
                Set.of("iam-auth-service"),
                stubTransport(JWKS_BODY, 200),
                Duration.ofSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path is unsafe");
    }

    @Test
    void enforcesResponseMetadataAndByteCap() {
        PlainHttpAccessTokenJwkSetLoader loader = new PlainHttpAccessTokenJwkSetLoader(
                JWKS_URI, Set.of("iam-auth-service"), stubTransport(JWKS_BODY, 500),
                Duration.ofSeconds(3));
        assertThatThrownBy(loader::load)
                .isInstanceOf(JwksTransportException.class)
                .hasMessageContaining("status is not 200");

        PlainHttpAccessTokenJwkSetLoader oversized = new PlainHttpAccessTokenJwkSetLoader(
                JWKS_URI,
                Set.of("iam-auth-service"),
                stubTransport("x".repeat(65_537), 200),
                Duration.ofSeconds(3));
        assertThatThrownBy(oversized::load)
                .isInstanceOf(JwksTransportException.class)
                .hasMessageContaining("byte limit");

        PlainHttpAccessTokenJwkSetLoader wrongType = new PlainHttpAccessTokenJwkSetLoader(
                JWKS_URI,
                Set.of("iam-auth-service"),
                (uri, timeout) -> new JwksHttpResponse(200,
                        Map.of("Content-Type", List.of("text/plain")),
                        new ByteArrayInputStream(JWKS_BODY.getBytes(StandardCharsets.UTF_8))),
                Duration.ofSeconds(3));
        assertThatThrownBy(wrongType::load)
                .isInstanceOf(JwksTransportException.class)
                .hasMessageContaining("content type");
    }

    private static JwksHttpTransport stubTransport(String body, int status) {
        return (uri, timeout) -> new JwksHttpResponse(
                status,
                Map.of("Content-Type", List.of("application/json")),
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
}
