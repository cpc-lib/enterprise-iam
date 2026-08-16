package com.enterprise.iam.gateway.delegation;

import com.enterprise.iam.common.security.delegation.DelegationTokenPolicy;
import com.enterprise.iam.common.security.delegation.Es256DelegationTokenDecoder;
import com.enterprise.iam.common.security.delegation.Es256DelegationTokenSigner;
import com.enterprise.iam.common.security.access.AccessTokenValidationResult;
import com.enterprise.iam.common.security.access.VerifiedAccessToken;
import com.enterprise.iam.gateway.security.GatewayAccessAuthenticationFilter;
import com.enterprise.iam.gateway.security.SessionStateVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class GatewayDelegationFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private ECPublicKey publicKey;
    private GatewayDelegationTokenIssuer issuer;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        publicKey = (ECPublicKey) keyPair.getPublic();
        Es256DelegationTokenSigner signer = new Es256DelegationTokenSigner(
                (ECPrivateKey) keyPair.getPrivate(),
                "gateway-key-1",
                "iam-gateway",
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                () -> "delegation-jti-1");
        issuer = new Es256GatewayDelegationTokenIssuer(signer);
    }

    @Test
    void bindsProtectedRouteToAudienceAndRemovesExternalBearerToken() {
        GatewayDelegationFilter filter = filter(issuer);
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));
        exchange.getAttributes().put(
                GatewayDelegationFilter.AUTHENTICATED_PRINCIPAL_ATTRIBUTE,
                new AuthenticatedGatewayPrincipal(10, 20, 30, "request-0001"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = value -> {
            forwarded.set(value);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        String compactToken = headers.getFirst(GatewayDelegationFilter.DELEGATION_HEADER);
        assertThat(compactToken).isNotBlank().isNotEqualTo("forged-delegation");
        assertThat(decoder().decode(compactToken).isValid()).isTrue();
    }

    @Test
    void protectedRouteWithoutAuthenticatedPrincipalFailsClosed() {
        GatewayDelegationFilter filter = filter(issuer);
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, value -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(chainCalls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("IAM_AUTHENTICATION_REQUIRED")
                .doesNotContain("principal");
    }

    @Test
    void signingFailureReturnsNonLeakingServiceUnavailable() {
        GatewayDelegationFilter filter = filter((audience, principal) -> {
            throw new IllegalStateException("KMS unavailable");
        });
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));
        exchange.getAttributes().put(
                GatewayDelegationFilter.AUTHENTICATED_PRINCIPAL_ATTRIBUTE,
                new AuthenticatedGatewayPrincipal(10, 20, 30, "request-0001"));

        filter.filter(exchange, value -> Mono.error(
                new AssertionError("chain must not continue"))).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("IAM_DELEGATION_UNAVAILABLE")
                .doesNotContain("KMS");
    }

    @Test
    void unprotectedRouteStillRemovesExternalCredentialsBeforeForwarding() {
        GatewayDelegationFilter filter = filter(issuer);
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("public-route"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, value -> {
            forwarded.set(value);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().containsKey(
                HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(
                GatewayDelegationFilter.DELEGATION_HEADER)).isFalse();
        assertThat(filter.getOrder()).isGreaterThan(Integer.MIN_VALUE);
    }

    @Test
    void routeWithoutExplicitSecurityPolicyFailsClosed() {
        GatewayDelegationFilter filter = filter(issuer);
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("unregistered-route"));
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, value -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(chainCalls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("IAM_ROUTE_SECURITY_POLICY_MISSING");
    }

    @Test
    void verifiedAccessAndActiveSessionFlowIntoRouteBoundDelegation() {
        ConfiguredDownstreamRouteAudienceRegistry registry =
                new ConfiguredDownstreamRouteAudienceRegistry(
                        Map.of("authorization-route", "iam-authorization-service"),
                        Set.of("public-route"));
        GatewayAccessAuthenticationFilter authenticationFilter =
                new GatewayAccessAuthenticationFilter(
                        registry,
                        token -> AccessTokenValidationResult.valid(new VerifiedAccessToken(
                                10, 20, 30, 4, 5, "access-jti-0001",
                                NOW, NOW.plusSeconds(300))),
                        token -> Mono.just(SessionStateVerification.ACTIVE),
                        value -> "request-0001");
        GatewayDelegationFilter delegationFilter = new GatewayDelegationFilter(registry, issuer);
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        authenticationFilter.filter(
                exchange,
                authenticated -> delegationFilter.filter(authenticated, value -> {
                    forwarded.set(value);
                    return Mono.empty();
                })).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey(
                HttpHeaders.AUTHORIZATION)).isFalse();
        String delegation = forwarded.get().getRequest().getHeaders().getFirst(
                GatewayDelegationFilter.DELEGATION_HEADER);
        assertThat(decoder().decode(delegation).isValid()).isTrue();
    }

    private GatewayDelegationFilter filter(GatewayDelegationTokenIssuer tokenIssuer) {
        return new GatewayDelegationFilter(
                new ConfiguredDownstreamRouteAudienceRegistry(Map.of(
                        "authorization-route", "iam-authorization-service"),
                        Set.of("public-route")),
                tokenIssuer);
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/resource")
                .header(HttpHeaders.AUTHORIZATION, "Bearer external-user-token")
                .header(GatewayDelegationFilter.DELEGATION_HEADER, "forged-delegation")
                .header("X-Request-Id", "request-0001")
                .build());
    }

    private Route route(String id) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(id);
        return route;
    }

    private Es256DelegationTokenDecoder decoder() {
        return new Es256DelegationTokenDecoder(
                keyId -> "gateway-key-1".equals(keyId)
                        ? Optional.of(publicKey)
                        : Optional.empty(),
                new DelegationTokenPolicy(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Set.of("ES256"),
                        "iam-gateway",
                        "iam-authorization-service",
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5)));
    }
}
