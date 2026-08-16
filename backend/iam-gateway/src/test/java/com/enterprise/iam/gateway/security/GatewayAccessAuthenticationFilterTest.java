package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.access.AccessTokenValidationFailure;
import com.enterprise.iam.common.security.access.AccessTokenValidationResult;
import com.enterprise.iam.common.security.access.VerifiedAccessToken;
import com.enterprise.iam.gateway.delegation.AuthenticatedGatewayPrincipal;
import com.enterprise.iam.gateway.delegation.ConfiguredDownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.GatewayDelegationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class GatewayAccessAuthenticationFilterTest {

    @Test
    void establishesPrincipalOnlyAfterTokenAndSessionValidation() {
        AtomicReference<VerifiedAccessToken> checkedSession = new AtomicReference<>();
        GatewayAccessAuthenticationFilter filter = filter(
                token -> AccessTokenValidationResult.valid(verifiedToken()),
                token -> {
                    checkedSession.set(token);
                    return Mono.just(SessionStateVerification.ACTIVE);
                });
        MockServerWebExchange exchange = exchange("Bearer signed-access-token");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));
        exchange.getAttributes().put(
                GatewayDelegationFilter.AUTHENTICATED_PRINCIPAL_ATTRIBUTE,
                new AuthenticatedGatewayPrincipal(999, 999, 999, "forged-request"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, value -> {
            forwarded.set(value);
            return Mono.empty();
        }).block();

        assertThat(checkedSession.get()).isEqualTo(verifiedToken());
        AuthenticatedGatewayPrincipal principal = forwarded.get().getAttribute(
                GatewayDelegationFilter.AUTHENTICATED_PRINCIPAL_ATTRIBUTE);
        assertThat(principal)
                .isEqualTo(new AuthenticatedGatewayPrincipal(
                        10, 20, 30, "request-0001"));
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(
                HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer signed-access-token");
        assertThat(filter.getOrder()).isLessThan(GatewayDelegationFilter.ORDER);
    }

    @Test
    void missingOrMalformedBearerTokenReturnsGenericUnauthorized() {
        AtomicInteger decoderCalls = new AtomicInteger();
        GatewayAccessAuthenticationFilter filter = filter(token -> {
            decoderCalls.incrementAndGet();
            return AccessTokenValidationResult.invalid(
                    AccessTokenValidationFailure.MALFORMED_TOKEN);
        }, token -> Mono.just(SessionStateVerification.ACTIVE));
        MockServerWebExchange exchange = exchange("Basic credentials");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));

        filter.filter(exchange, mustNotContinue()).block();

        assertThat(decoderCalls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate"))
                .isEqualTo("Bearer");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("IAM_AUTHENTICATION_REQUIRED")
                .doesNotContain("MALFORMED_TOKEN");
    }

    @Test
    void revokedOrVersionMismatchedSessionReturnsUnauthorized() {
        GatewayAccessAuthenticationFilter filter = filter(
                token -> AccessTokenValidationResult.valid(verifiedToken()),
                token -> Mono.just(SessionStateVerification.INVALID));
        MockServerWebExchange exchange = exchange("Bearer signed-access-token");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));

        filter.filter(exchange, mustNotContinue()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("IAM_AUTHENTICATION_REQUIRED")
                .doesNotContain("session");
    }

    @Test
    void keyOrSessionDependencyFailureReturnsNonLeakingServiceUnavailable() {
        GatewayAccessAuthenticationFilter keyFailure = filter(
                token -> AccessTokenValidationResult.invalid(
                        AccessTokenValidationFailure.KEY_RESOLUTION_UNAVAILABLE),
                token -> Mono.just(SessionStateVerification.ACTIVE));
        MockServerWebExchange keyExchange = exchange("Bearer signed-access-token");
        keyExchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));
        keyFailure.filter(keyExchange, mustNotContinue()).block();
        assertUnavailable(keyExchange);

        GatewayAccessAuthenticationFilter sessionFailure = filter(
                token -> AccessTokenValidationResult.valid(verifiedToken()),
                token -> Mono.error(new IllegalStateException("Redis unavailable")));
        MockServerWebExchange sessionExchange = exchange("Bearer signed-access-token");
        sessionExchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));
        sessionFailure.filter(sessionExchange, mustNotContinue()).block();
        assertUnavailable(sessionExchange);
        assertThat(sessionExchange.getResponse().getBodyAsString().block())
                .doesNotContain("Redis");
    }

    @Test
    void explicitPublicRouteDoesNotCreateIdentityOrCallDependencies() {
        AtomicInteger decoderCalls = new AtomicInteger();
        AtomicInteger sessionCalls = new AtomicInteger();
        GatewayAccessAuthenticationFilter filter = filter(token -> {
            decoderCalls.incrementAndGet();
            return AccessTokenValidationResult.valid(verifiedToken());
        }, token -> {
            sessionCalls.incrementAndGet();
            return Mono.just(SessionStateVerification.ACTIVE);
        });
        MockServerWebExchange exchange = exchange("Bearer ignored-on-public-route");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("public-route"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, value -> {
            forwarded.set(value);
            return Mono.empty();
        }).block();

        assertThat(decoderCalls).hasValue(0);
        assertThat(sessionCalls).hasValue(0);
        AuthenticatedGatewayPrincipal principal = forwarded.get().getAttribute(
                GatewayDelegationFilter.AUTHENTICATED_PRINCIPAL_ATTRIBUTE);
        assertThat(principal).isNull();
    }

    @Test
    void unregisteredRouteFailsClosedBeforeAuthentication() {
        GatewayAccessAuthenticationFilter filter = filter(
                token -> AccessTokenValidationResult.valid(verifiedToken()),
                token -> Mono.just(SessionStateVerification.ACTIVE));
        MockServerWebExchange exchange = exchange("Bearer signed-access-token");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("unregistered-route"));

        filter.filter(exchange, mustNotContinue()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("IAM_ROUTE_SECURITY_POLICY_MISSING");
    }

    @Test
    void downstreamFailureAfterSuccessfulAuthenticationIsNotRemapped() {
        GatewayAccessAuthenticationFilter filter = filter(
                token -> AccessTokenValidationResult.valid(verifiedToken()),
                token -> Mono.just(SessionStateVerification.ACTIVE));
        MockServerWebExchange exchange = exchange("Bearer signed-access-token");
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route("authorization-route"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.filter(
                exchange,
                value -> Mono.error(new IllegalStateException("downstream failed"))).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("downstream failed");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private GatewayAccessAuthenticationFilter filter(
            com.enterprise.iam.common.security.access.AccessTokenDecoder decoder,
            ReactiveSessionStateVerifier sessionVerifier) {
        return new GatewayAccessAuthenticationFilter(
                new ConfiguredDownstreamRouteAudienceRegistry(
                        Map.of("authorization-route", "iam-authorization-service"),
                        Set.of("public-route")),
                decoder,
                sessionVerifier,
                exchange -> "request-0001");
    }

    private MockServerWebExchange exchange(String authorization) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/resource")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-Request-Id", "request-0001")
                .build());
    }

    private static VerifiedAccessToken verifiedToken() {
        return new VerifiedAccessToken(
                10, 20, 30, 4, 5, "access-jti-0001",
                Instant.parse("2026-08-12T12:00:00Z"),
                Instant.parse("2026-08-12T12:05:00Z"));
    }

    private static GatewayFilterChain mustNotContinue() {
        return exchange -> Mono.error(new AssertionError("chain must not continue"));
    }

    private static Route route(String id) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(id);
        return route;
    }

    private static void assertUnavailable(MockServerWebExchange exchange) {
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("IAM_AUTHENTICATION_DEPENDENCY_UNAVAILABLE");
    }
}
