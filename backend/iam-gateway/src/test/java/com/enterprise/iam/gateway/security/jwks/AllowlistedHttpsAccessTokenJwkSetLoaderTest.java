package com.enterprise.iam.gateway.security.jwks;

import com.enterprise.iam.common.security.jwt.BoundedRefreshingJwkSetPublicKeyResolver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowlistedHttpsAccessTokenJwkSetLoaderTest {

    private static final URI URI_VALUE =
            URI.create("https://auth.example.net/.well-known/jwks.json");
    private static final Set<String> ALLOWLIST = Set.of("auth.example.net");
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void fetchesAnExactAllowlistedGlobalHttpsEndpoint() {
        String json = "{\"keys\":[]}";
        var loader = loader(
                host -> List.of(literal("93.184.216.34")),
                (uri, timeout) -> response(200, Map.of(
                        "Content-Type", List.of("application/jwk-set+json; charset=utf-8"),
                        "Content-Length", List.of(Integer.toString(json.length()))), json));

        assertThat(loader.load()).isEqualTo(json);
    }

    @Test
    void rejectsNonHttpsCredentialsQueryTraversalAndHostMismatch() {
        assertUnsafe("http://auth.example.net/.well-known/jwks.json", ALLOWLIST);
        assertUnsafe("https://user@auth.example.net/.well-known/jwks.json", ALLOWLIST);
        assertUnsafe("https://auth.example.net/.well-known/jwks.json?next=x", ALLOWLIST);
        assertUnsafe("https://auth.example.net/a/../jwks.json", ALLOWLIST);
        assertUnsafe("https://auth.example.net/%2e%2e/jwks.json", ALLOWLIST);
        assertUnsafe("https://auth.example.net/.well-known/jwks.json", Set.of("other.example.net"));
    }

    @Test
    void blocksPrivateOrMixedDnsAnswersBeforeTransport() {
        AtomicBoolean called = new AtomicBoolean();
        var loader = loader(
                host -> List.of(literal("93.184.216.34"), literal("127.0.0.1")),
                (uri, timeout) -> {
                    called.set(true);
                    return response(200, Map.of("Content-Type", List.of("application/json")), "{}");
                });

        assertThatThrownBy(loader::load).isInstanceOf(JwksTransportException.class);
        assertThat(called).isFalse();
    }

    @Test
    void rejectsRedirectContentTypeEncodingAndAmbiguousLength() {
        assertRejected(response(302,
                Map.of("Content-Type", List.of("application/json")), "{}"));
        assertRejected(response(200,
                Map.of("Content-Type", List.of("text/html")), "{}"));
        assertRejected(response(200,
                Map.of(
                        "Content-Type", List.of("application/json"),
                        "Content-Encoding", List.of("gzip")), "{}"));
        assertRejected(response(200,
                Map.of(
                        "Content-Type", List.of("application/json"),
                        "Content-Length", List.of("2", "3")), "{}"));
    }

    @Test
    void enforcesTheStreamingByteLimitAndStrictUtf8() {
        byte[] oversized = new byte[
                BoundedRefreshingJwkSetPublicKeyResolver.MAX_JWKS_BYTES + 1];
        assertRejected(new JwksHttpResponse(
                200,
                Map.of("Content-Type", List.of("application/json")),
                new ByteArrayInputStream(oversized)));
        assertRejected(new JwksHttpResponse(
                200,
                Map.of("Content-Type", List.of("application/json")),
                new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28})));
    }

    @Test
    void classifiesPrivateDocumentationAndGlobalAddresses() {
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("10.0.0.1"))).isFalse();
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("100.64.0.1"))).isFalse();
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("203.0.113.10"))).isFalse();
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("2001:db8::1"))).isFalse();
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("64:ff9b::7f00:1"))).isFalse();
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("2002:7f00:1::"))).isFalse();
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("93.184.216.34"))).isTrue();
        assertThat(AllowlistedHttpsAccessTokenJwkSetLoader.isGlobalUnicast(
                literal("2606:4700:4700::1111"))).isTrue();
    }

    private static AllowlistedHttpsAccessTokenJwkSetLoader loader(
            JwksDnsResolver resolver,
            JwksHttpTransport transport) {
        return new AllowlistedHttpsAccessTokenJwkSetLoader(
                URI_VALUE, ALLOWLIST, resolver, transport, TIMEOUT);
    }

    private static JwksHttpTransport response(JwksHttpResponse response) {
        return (uri, timeout) -> response;
    }

    private static JwksHttpResponse response(
            int status,
            Map<String, List<String>> headers,
            String body) {
        return new JwksHttpResponse(status, headers,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertRejected(JwksHttpResponse response) {
        assertThatThrownBy(() -> loader(
                host -> List.of(literal("93.184.216.34")),
                response(response)).load())
                .isInstanceOf(JwksTransportException.class);
    }

    private static void assertUnsafe(String uri, Set<String> allowlist) {
        assertThatThrownBy(() -> new AllowlistedHttpsAccessTokenJwkSetLoader(
                URI.create(uri), allowlist,
                host -> List.of(literal("93.184.216.34")),
                response(response(200,
                        Map.of("Content-Type", List.of("application/json")), "{}")),
                TIMEOUT)).isInstanceOf(IllegalArgumentException.class);
    }

    private static InetAddress literal(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (java.net.UnknownHostException exception) {
            throw new AssertionError(exception);
        }
    }
}
