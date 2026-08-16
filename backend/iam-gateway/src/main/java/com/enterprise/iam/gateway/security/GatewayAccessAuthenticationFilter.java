package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.access.AccessTokenDecoder;
import com.enterprise.iam.common.security.access.AccessTokenValidationFailure;
import com.enterprise.iam.common.security.access.AccessTokenValidationResult;
import com.enterprise.iam.common.security.access.VerifiedAccessToken;
import com.enterprise.iam.gateway.delegation.AuthenticatedGatewayPrincipal;
import com.enterprise.iam.gateway.delegation.DownstreamRouteAudienceRegistry;
import com.enterprise.iam.gateway.delegation.GatewayDelegationFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Establishes authoritative Gateway identity only after ES256 access-token
 * validation and authoritative session/version verification.
 */
public final class GatewayAccessAuthenticationFilter implements GlobalFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1_000;
    private static final int MAX_AUTHORIZATION_HEADER_LENGTH = 8_199;

    private final DownstreamRouteAudienceRegistry routeRegistry;
    private final AccessTokenDecoder tokenDecoder;
    private final ReactiveSessionStateVerifier sessionVerifier;
    private final GatewayRequestIdResolver requestIdResolver;

    public GatewayAccessAuthenticationFilter(
            DownstreamRouteAudienceRegistry routeRegistry,
            AccessTokenDecoder tokenDecoder,
            ReactiveSessionStateVerifier sessionVerifier,
            GatewayRequestIdResolver requestIdResolver) {
        this.routeRegistry = Objects.requireNonNull(routeRegistry, "routeRegistry must not be null");
        this.tokenDecoder = Objects.requireNonNull(tokenDecoder, "tokenDecoder must not be null");
        this.sessionVerifier = Objects.requireNonNull(
                sessionVerifier, "sessionVerifier must not be null");
        this.requestIdResolver = Objects.requireNonNull(
                requestIdResolver, "requestIdResolver must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().remove(
                GatewayDelegationFilter.AUTHENTICATED_PRINCIPAL_ATTRIBUTE);
        String requestId = resolveRequestId(exchange);
        ServerWebExchange tracedExchange = withRequestId(exchange, requestId);
        Route route = tracedExchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return missingRoutePolicy(tracedExchange, requestId);
        }
        String routeId = route.getId();
        var audience = routeRegistry.audienceForRoute(routeId);
        if (audience.isEmpty()) {
            if (routeRegistry.isExplicitPublicRoute(routeId)) {
                return chain.filter(tracedExchange);
            }
            return missingRoutePolicy(tracedExchange, requestId);
        }

        String compactToken = extractBearerToken(
                tracedExchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION));
        if (compactToken == null) {
            return authenticationRequired(tracedExchange, requestId);
        }

        return Mono.fromCallable(() -> tokenDecoder.decode(compactToken))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(AccessTokenValidationResult.invalid(
                        AccessTokenValidationFailure.KEY_RESOLUTION_UNAVAILABLE))
                .flatMap(result -> authenticate(
                        tracedExchange, chain, result, requestId));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private Mono<Void> authenticate(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            AccessTokenValidationResult result,
            String requestId) {
        if (!result.isValid()) {
            if (result.failure() == AccessTokenValidationFailure.KEY_RESOLUTION_UNAVAILABLE) {
                return dependencyUnavailable(exchange, requestId);
            }
            return authenticationRequired(exchange, requestId);
        }
        VerifiedAccessToken token = result.verifiedToken().orElseThrow();
        return Mono.defer(() -> sessionVerifier.verify(token))
                .map(verification -> verification == SessionStateVerification.ACTIVE
                        ? SessionVerificationOutcome.ACTIVE
                        : SessionVerificationOutcome.INVALID)
                .switchIfEmpty(Mono.just(SessionVerificationOutcome.INVALID))
                .onErrorReturn(SessionVerificationOutcome.UNAVAILABLE)
                .flatMap(outcome -> continueAfterSessionVerification(
                        exchange, chain, token, requestId, outcome));
    }

    private static Mono<Void> continueAfterSessionVerification(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            VerifiedAccessToken token,
            String requestId,
            SessionVerificationOutcome outcome) {
        if (outcome == SessionVerificationOutcome.UNAVAILABLE) {
            return dependencyUnavailable(exchange, requestId);
        }
        if (outcome != SessionVerificationOutcome.ACTIVE) {
            return authenticationRequired(exchange, requestId);
        }
        exchange.getAttributes().put(
                GatewayDelegationFilter.AUTHENTICATED_PRINCIPAL_ATTRIBUTE,
                new AuthenticatedGatewayPrincipal(
                        token.tenantId(),
                        token.subjectId(),
                        token.sessionId(),
                        requestId));
        return chain.filter(exchange);
    }

    private static String extractBearerToken(List<String> authorizationHeaders) {
        if (authorizationHeaders == null || authorizationHeaders.size() != 1) {
            return null;
        }
        String value = authorizationHeaders.get(0);
        if (value == null
                || value.length() > MAX_AUTHORIZATION_HEADER_LENGTH
                || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = value.substring(7);
        if (token.isBlank()
                || token.length() != token.trim().length()
                || token.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        return token;
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        try {
            String value = requestIdResolver.resolve(exchange);
            if (value != null && value.matches("[A-Za-z0-9._:-]{8,128}")) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // Request correlation failure must not create an authentication bypass.
        }
        return UUID.randomUUID().toString();
    }

    private static ServerWebExchange withRequestId(
            ServerWebExchange exchange,
            String requestId) {
        var request = exchange.getRequest().mutate()
                .headers(headers -> headers.set("X-Request-Id", requestId))
                .build();
        return exchange.mutate().request(request).build();
    }

    private static Mono<Void> authenticationRequired(
            ServerWebExchange exchange,
            String requestId) {
        return GatewaySecurityErrorWriter.reject(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "IAM_AUTHENTICATION_REQUIRED",
                "Authentication required",
                requestId);
    }

    private static Mono<Void> dependencyUnavailable(
            ServerWebExchange exchange,
            String requestId) {
        return GatewaySecurityErrorWriter.reject(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "IAM_AUTHENTICATION_DEPENDENCY_UNAVAILABLE",
                "Authentication temporarily unavailable",
                requestId);
    }

    private static Mono<Void> missingRoutePolicy(
            ServerWebExchange exchange,
            String requestId) {
        return GatewaySecurityErrorWriter.reject(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "IAM_ROUTE_SECURITY_POLICY_MISSING",
                "Route security policy unavailable",
                requestId);
    }

    private enum SessionVerificationOutcome {
        ACTIVE,
        INVALID,
        UNAVAILABLE
    }
}
