package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.headers.UntrustedIdentityHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Removes all client-controlled identity/delegation headers before any
 * authentication or routing component can accidentally trust them.
 */
@Component
public final class ExternalIdentityHeaderSanitizingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> UntrustedIdentityHeaders.NAMES.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
