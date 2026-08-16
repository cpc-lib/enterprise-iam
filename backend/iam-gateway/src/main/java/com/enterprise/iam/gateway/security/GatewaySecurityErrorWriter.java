package com.enterprise.iam.gateway.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public final class GatewaySecurityErrorWriter {

    private GatewaySecurityErrorWriter() {
    }

    public static Mono<Void> reject(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message,
            String traceId) {
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
        if (status == HttpStatus.UNAUTHORIZED) {
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
