package com.enterprise.iam.gateway.security;

import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

public final class DefaultGatewayRequestIdResolver implements GatewayRequestIdResolver {

    @Override
    public String resolve(ServerWebExchange exchange) {
        return UUID.randomUUID().toString();
    }
}
