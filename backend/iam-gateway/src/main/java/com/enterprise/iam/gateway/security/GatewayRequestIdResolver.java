package com.enterprise.iam.gateway.security;

import org.springframework.web.server.ServerWebExchange;

@FunctionalInterface
public interface GatewayRequestIdResolver {

    String resolve(ServerWebExchange exchange);
}
