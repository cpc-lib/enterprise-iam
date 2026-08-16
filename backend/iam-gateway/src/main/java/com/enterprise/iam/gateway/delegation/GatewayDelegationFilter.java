package com.enterprise.iam.gateway.delegation;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Converts authenticated external context into an audience-bound delegation
 * token. External bearer tokens are removed before every downstream hop.
 */
public final class GatewayDelegationFilter implements GlobalFilter, Ordered {

    public static final String AUTHENTICATED_PRINCIPAL_ATTRIBUTE =
            GatewayDelegationFilter.class.getName() + ".authenticatedPrincipal";
    public static final String DELEGATION_HEADER = "X-IAM-Delegation";
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2_000;

    private final DownstreamRouteAudienceRegistry audienceRegistry;
    private final GatewayDelegationTokenIssuer tokenIssuer;

    public GatewayDelegationFilter(
            DownstreamRouteAudienceRegistry audienceRegistry,
            GatewayDelegationTokenIssuer tokenIssuer) {
        this.audienceRegistry = Objects.requireNonNull(
                audienceRegistry, "audienceRegistry must not be null");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return reject(
                    exchange,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IAM_ROUTE_SECURITY_POLICY_MISSING",
                    "Route security policy unavailable");
        }
        String routeId = route.getId();
        var audience = audienceRegistry.audienceForRoute(routeId);

        if (audience.isEmpty()) {
            if (audienceRegistry.isExplicitPublicRoute(routeId)) {
                return chain.filter(withoutExternalCredentials(exchange));
            }
            return reject(
                    exchange,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IAM_ROUTE_SECURITY_POLICY_MISSING",
                    "Route security policy unavailable");
        }

        AuthenticatedGatewayPrincipal principal = exchange.getAttribute(
                AUTHENTICATED_PRINCIPAL_ATTRIBUTE);
        if (principal == null) {
            return reject(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "IAM_AUTHENTICATION_REQUIRED",
                    "Authentication required");
        }

        final String compactToken;
        try {
            compactToken = tokenIssuer.issue(audience.orElseThrow(), principal);
        } catch (RuntimeException exception) {
            return reject(
                    exchange,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IAM_DELEGATION_UNAVAILABLE",
                    "Authentication delegation temporarily unavailable");
        }

        var request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove(DELEGATION_HEADER);
                    headers.set(DELEGATION_HEADER, compactToken);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static ServerWebExchange withoutExternalCredentials(ServerWebExchange exchange) {
        var request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove(DELEGATION_HEADER);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private static Mono<Void> reject(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message) {
        String traceId = normalizeTraceId(exchange.getRequest().getHeaders().getFirst("X-Request-Id"));
        byte[] body = ("{\"success\":false,\"code\":\""
                + code
                + "\",\"message\":\""
                + message
                + "\",\"traceId\":\""
                + traceId
                + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static String normalizeTraceId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) {
            return "trace-unavailable";
        }
        return value;
    }
}
